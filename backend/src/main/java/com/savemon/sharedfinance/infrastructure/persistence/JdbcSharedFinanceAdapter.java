package com.savemon.sharedfinance.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.identity.application.UserDisplayNameLookup;
import com.savemon.personalfinance.application.DebitOwnedAccount;
import com.savemon.sharedfinance.application.SharedFinanceOperations;
import com.savemon.sharedfinance.application.SharedFinanceOperations.ContributionRequest;
import com.savemon.sharedfinance.application.SharedFinanceOperations.ExpenseRequest;
import com.savemon.sharedfinance.application.SharedFinanceOperations.FundingInput;
import com.savemon.sharedfinance.application.SharedFinanceOperations.SplitInput;
import com.savemon.sharedfinance.application.MemberPaidExpenseRules;
import com.savemon.shared.interfaces.exception.BusinessApiException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * JDBC implementation of the shared-finance use-case port. Personal Finance is invoked
 * only through the approved account-debit application port inside each DB transaction.
 */
@Service
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcSharedFinanceAdapter implements SharedFinanceOperations {
    private final JdbcTemplate jdbc;
    private final DebitOwnedAccount debitAccount;
    private final UserDisplayNameLookup displayNames;
    private final MemberPaidExpenseRules memberPaidExpenseRules;
    private final ObjectMapper json;

    public JdbcSharedFinanceAdapter(JdbcTemplate jdbc, DebitOwnedAccount debitAccount,
                                    UserDisplayNameLookup displayNames, ObjectMapper json) {
        this.jdbc = jdbc;
        this.debitAccount = debitAccount;
        this.displayNames = displayNames;
        this.memberPaidExpenseRules = new MemberPaidExpenseRules(debitAccount);
        this.json = json;
    }

    @Transactional
    public Map<String,Object> createVault(UUID actor,String name,String currency) {
        String curr = currency(currency);
        String safeName = requiredName(name);
        UUID vault = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO shared_vaults(id,name,currency,current_balance,status,created_by,version,created_at,updated_at)
                VALUES (?,?,?,0,'ACTIVE',?,0,?,?)
                """,vault,safeName,curr,actor,now,now);
        UUID member = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO vault_members(id,vault_id,user_id,role,status,joined_at,left_at,created_at,updated_at)
                VALUES (?,?,?,'OWNER','ACTIVE',?,NULL,?,?)
                """,member,vault,actor,now,now,now);
        audit(actor,"VAULT_CREATED","VAULT",vault);
        return vault(vault,actor);
    }

    public Map<String,Object> vaults(UUID actor,int page,int size) {
        validatePage(page,size);
        Long count=jdbc.queryForObject("""
                SELECT count(*) FROM vault_members WHERE user_id=? AND status='ACTIVE'
                """,Long.class,actor);
        List<Map<String,Object>> items=jdbc.query("""
                SELECT v.id,v.name,RTRIM(v.currency) AS currency,v.current_balance AS balance,v.status
                FROM shared_vaults v JOIN vault_members m ON m.vault_id=v.id
                WHERE m.user_id=? AND m.status='ACTIVE' ORDER BY v.created_at DESC LIMIT ? OFFSET ?
                """,(rs,n)->row(rs),actor,size,page*size);
        return page(items,page,size,count==null?0:count);
    }
    public Map<String,Object> vault(UUID id,UUID actor) {
        requireMember(id,actor);
        return jdbc.query("""
                SELECT id,name,RTRIM(currency) AS currency,current_balance AS balance,status FROM shared_vaults WHERE id=?
                """,(rs,n)->row(rs),id).stream().findFirst()
                .orElseThrow(()->fail("VAULT_NOT_FOUND","Vault was not found.",HttpStatus.NOT_FOUND));
    }

    public Map<String,Object> members(UUID vault,UUID actor,int page,int size) {
        validatePage(page,size);
        requireMember(vault,actor);
        Long total=jdbc.queryForObject("SELECT count(*) FROM vault_members WHERE vault_id=?",Long.class,vault);
        List<Map<String,Object>> items=jdbc.query("""
                SELECT m.id,m.user_id AS "userId",m.role,m.status,
                       m.joined_at AS "joinedAt"
                FROM vault_members m WHERE m.vault_id=? ORDER BY m.joined_at LIMIT ? OFFSET ?
                """,(rs,n)->row(rs),vault,size,page*size);
        Map<UUID,String> names=displayNames.displayNames(items.stream()
                .map(item->(UUID)item.get("userId")).toList());
        items.forEach(item->item.put("displayName",names.get(item.get("userId"))));
        return page(items,page,size,total==null?0:total);
    }

    @Transactional
    public Map<String,Object> addMember(UUID vault,UUID actor,UUID user) {
        Map<String,Object> v=lockVault(vault);
        ensureActiveVault(v);
        requireOwner(vault,actor);
        List<Map<String,Object>> existing=jdbc.query("""
                SELECT id,status FROM vault_members WHERE vault_id=? AND user_id=? FOR UPDATE
                """,(rs,n)->row(rs),vault,user);
        Instant now=Instant.now();
        if (!existing.isEmpty()) {
            if ("ACTIVE".equals(existing.get(0).get("status")))
                throw fail("MEMBER_ALREADY_EXISTS","User is already an active member.",HttpStatus.CONFLICT);
            jdbc.update("UPDATE vault_members SET status='ACTIVE',joined_at=?,left_at=NULL,updated_at=? WHERE id=?",
                    now,now,existing.get(0).get("id"));
            UUID id=(UUID)existing.get(0).get("id");
            audit(actor,"MEMBER_ADDED","VAULT_MEMBER",id);
            return member(id);
        }
        UUID id=UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO vault_members(id,vault_id,user_id,role,status,joined_at,created_at,updated_at)
                    VALUES (?,?,?,'MEMBER','ACTIVE',?,?,?)
                    """,id,vault,user,now,now,now);
        } catch (DataIntegrityViolationException exception) {
            throw fail("MEMBER_NOT_FOUND","User was not found.",HttpStatus.NOT_FOUND);
        }
        audit(actor,"MEMBER_ADDED","VAULT_MEMBER",id);
        return member(id);
    }

    @Transactional
    public void removeMember(UUID vault,UUID actor,UUID memberId) {
        lockVault(vault);
        requireOwner(vault,actor);
        Map<String,Object> target=jdbc.query("""
                SELECT id,role,status FROM vault_members WHERE id=? AND vault_id=? FOR UPDATE
                """,(rs,n)->row(rs),memberId,vault).stream().findFirst()
                .orElseThrow(()->fail("MEMBER_NOT_FOUND","Member was not found.",HttpStatus.NOT_FOUND));
        if (!"ACTIVE".equals(target.get("status"))) throw fail("MEMBER_NOT_ACTIVE","Member is not active.",HttpStatus.CONFLICT);
        long owners=jdbc.queryForObject("SELECT count(*) FROM vault_members WHERE vault_id=? AND role='OWNER' AND status='ACTIVE'",Long.class,vault);
        if ("OWNER".equals(target.get("role")) && owners<=1)
            throw fail("VAULT_OWNER_REQUIRED","The last active vault owner cannot be removed.",HttpStatus.CONFLICT);
        Instant now=Instant.now();
        jdbc.update("UPDATE vault_members SET status='REMOVED',left_at=?,updated_at=? WHERE id=?",now,now,memberId);
        audit(actor,"MEMBER_REMOVED","VAULT_MEMBER",memberId);
    }

    @Transactional
    public Map<String,Object> contribution(UUID vault,UUID actor,String key,ContributionRequest request) {
        if(request==null||request.sourceAccountId()==null||request.contributedAt()==null||request.currency()==null)
            throw fail("CONTRIBUTION_INVALID","sourceAccountId, amount, currency, and contributedAt are required.",HttpStatus.UNPROCESSABLE_ENTITY);
        if(request.description()!=null&&request.description().length()>500)
            throw fail("VALIDATION_ERROR","description must be at most 500 characters.",HttpStatus.BAD_REQUEST);
        byte[] hash=hash(request);
        UUID duplicate=reserve(actor,"VAULT_CONTRIBUTION_"+vault,key,hash);
        if (duplicate!=null) return contributionDetail(duplicate,vault,actor);
        Map<String,Object> v=lockVault(vault);
        ensureActiveVault(v);
        Map<String,Object> member=requireMember(vault,actor);
        requireAmount(request.amount());
        String currency=(String)v.get("currency");
        if(!currency.equals(currency(request.currency())))
            throw fail("VALIDATION_ERROR","Contribution currency must match vault currency.",HttpStatus.UNPROCESSABLE_ENTITY);
        UUID account=request.sourceAccountId();
        debitAccount.debit(actor,account,request.amount(),currency);
        UUID tx=UUID.randomUUID(), contribution=UUID.randomUUID();
        Instant at=request.contributedAt();
        Instant now=Instant.now();
        jdbc.update("""
                INSERT INTO transactions(id,user_id,account_id,vault_id,type,amount,currency,transaction_date,
                description,status,idempotency_key,version,created_at,updated_at)
                VALUES (?,?,?,?,'EXPENSE',?,?,?,?,'ACTIVE',?,0,?,?)
                """,tx,actor,account,vault,request.amount(),currency,at,request.description(),key,now,now);
        jdbc.update("""
                INSERT INTO contributions(id,vault_id,member_id,transaction_id,amount,contributed_at,created_at)
                VALUES (?,?,?,?,?,?,?)
                """,contribution,vault,member.get("id"),tx,request.amount(),at,now);
        requireFitsNumeric((BigDecimal)v.get("balance"),request.amount());
        jdbc.update("UPDATE shared_vaults SET current_balance=current_balance+?,version=version+1,updated_at=? WHERE id=?",
                request.amount(),now,vault);
        setReference(actor,"VAULT_CONTRIBUTION_"+vault,key,contribution);
        audit(actor,"CONTRIBUTION_CREATED","CONTRIBUTION",contribution);
        return contributionDetail(contribution,vault,actor);
    }

    public Map<String,Object> contributions(UUID vault,UUID actor,UUID memberId,LocalDate from,LocalDate to,
                                             int page,int size) {
        validatePage(page,size);
        requireMember(vault,actor);
        validateDatesOptional(from,to);
        StringBuilder filter=new StringBuilder(" WHERE c.vault_id=?");
        List<Object> filterArgs=new ArrayList<>(List.of(vault));
        if(from!=null){filter.append(" AND c.contributed_at>=?");filterArgs.add(from.atStartOfDay().toInstant(ZoneOffset.UTC));}
        if(to!=null){filter.append(" AND c.contributed_at<?");filterArgs.add(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));}
        if(memberId!=null){filter.append(" AND c.member_id=?");filterArgs.add(memberId);}
        Long total=jdbc.queryForObject("SELECT count(*) FROM contributions c"+filter,Long.class,filterArgs.toArray());
        List<Object> queryArgs=new ArrayList<>(filterArgs);
        queryArgs.add(size);
        queryArgs.add(page*size);
        String sql="""
                SELECT c.id,c.vault_id AS "vaultId",c.member_id AS "memberId",t.account_id AS "sourceAccountId",
                c.amount,RTRIM(v.currency) AS currency,t.description,c.contributed_at AS "contributedAt",
                c.transaction_id AS "transactionId",t.status
                FROM contributions c JOIN vault_members m ON m.id=c.member_id JOIN shared_vaults v ON v.id=c.vault_id
                JOIN transactions t ON t.id=c.transaction_id
                """+filter+" ORDER BY c.contributed_at DESC LIMIT ? OFFSET ?";
        List<Map<String,Object>> items=jdbc.query(sql,(rs,n)->row(rs),queryArgs.toArray());
        items.forEach(item -> { if(item.get("description")==null)item.remove("description"); });
        return page(items,page,size,total==null?0:total);
    }

    @Transactional
    public Map<String,Object> expense(UUID vault,UUID actor,String key,ExpenseRequest request) {
        if(request==null||request.expenseDate()==null||request.funding()==null)
            throw fail("SHARED_EXPENSE_INVALID","expenseDate and funding are required.",HttpStatus.UNPROCESSABLE_ENTITY);
        UUID duplicate=reserve(actor,"VAULT_EXPENSE_"+vault,key,hash(request));
        if(duplicate!=null)return expenseDetail(duplicate,vault,actor);
        Map<String,Object> v=lockVault(vault);
        ensureActiveVault(v);
        requireMember(vault,actor);
        requireAmount(request.amount());
        String curr=currency(request.currency());
        if(!curr.equals(v.get("currency")))throw fail("VALIDATION_ERROR","Expense currency must match vault currency.",HttpStatus.UNPROCESSABLE_ENTITY);
        if(request.splits()==null||request.splits().isEmpty())throw fail("SHARED_EXPENSE_INVALID","At least one expense split is required.",HttpStatus.UNPROCESSABLE_ENTITY);
        BigDecimal total=BigDecimal.ZERO;
        java.util.HashSet<UUID> seen=new java.util.HashSet<>();
        for(SplitInput split:request.splits()){
            if(split==null||split.memberId()==null)throw fail("EXPENSE_SPLIT_MEMBER_INVALID","Every split must identify a member.",HttpStatus.UNPROCESSABLE_ENTITY);
            requireAmount(split.amount());
            if(!seen.add(split.memberId()))throw fail("EXPENSE_SPLIT_MEMBER_INVALID","A member may only appear once in expense splits.",HttpStatus.UNPROCESSABLE_ENTITY);
            activeMember(vault,split.memberId());
            total=total.add(split.amount());
        }
        if(total.compareTo(request.amount())!=0)throw fail("EXPENSE_SPLIT_TOTAL_MISMATCH","Expense splits must equal the expense amount.",HttpStatus.UNPROCESSABLE_ENTITY);

        UUID payer=null;
        UUID accountId=null;
        String funding=request.funding().type();
        if("VAULT".equals(funding)){
            int updated=jdbc.update("""
                    UPDATE shared_vaults SET current_balance=current_balance-?,version=version+1,updated_at=?
                    WHERE id=? AND current_balance>=?
                    """,request.amount(),Instant.now(),vault,request.amount());
            if(updated!=1)throw fail("INSUFFICIENT_BALANCE","Insufficient vault balance.",HttpStatus.UNPROCESSABLE_ENTITY);
        } else if("MEMBER".equals(funding)){
            FundingInput f=request.funding();
            if(f.payerMemberId()==null||f.sourceAccountId()==null)
                throw fail("SHARED_EXPENSE_INVALID","Member funding requires payerMemberId and sourceAccountId.",HttpStatus.UNPROCESSABLE_ENTITY);
            Map<String,Object> payerMember=activeMember(vault,f.payerMemberId());
            accountId=f.sourceAccountId();
            try {
                memberPaidExpenseRules.debitPayer(actor,(UUID)payerMember.get("userId"),accountId,request.amount(),curr,
                        (String)v.get("currency"));
            } catch (IllegalStateException exception) {
                throw fail("ACCESS_DENIED","A member-paid expense must be paid by the authenticated member.",HttpStatus.FORBIDDEN);
            } catch (IllegalArgumentException exception) {
                throw fail("VALIDATION_ERROR",exception.getMessage(),HttpStatus.UNPROCESSABLE_ENTITY);
            }
            payer=(UUID)payerMember.get("id");
        } else throw fail("SHARED_EXPENSE_INVALID","Funding type must be VAULT or MEMBER.",HttpStatus.UNPROCESSABLE_ENTITY);

        UUID tx=UUID.randomUUID(),expense=UUID.randomUUID();
        Instant at=request.expenseDate(),now=Instant.now();
        jdbc.update("""
                INSERT INTO transactions(id,user_id,account_id,vault_id,type,amount,currency,transaction_date,
                description,status,idempotency_key,version,created_at,updated_at)
                VALUES (?,?,?,?, 'EXPENSE',?,?,?,?,'ACTIVE',?,0,?,?)
                """,tx,actor,accountId,vault,request.amount(),curr,at,request.description(),key,now,now);
        jdbc.update("""
                INSERT INTO shared_expenses(id,vault_id,transaction_id,paid_by_member_id,amount,description,expense_date,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,expense,vault,tx,payer,request.amount(),request.description(),at,now,now);
        for(SplitInput split:request.splits())jdbc.update("""
                INSERT INTO expense_splits(id,shared_expense_id,member_id,amount,created_at) VALUES (?,?,?,?,?)
                """,UUID.randomUUID(),expense,split.memberId(),split.amount(),now);
        setReference(actor,"VAULT_EXPENSE_"+vault,key,expense);
        audit(actor,"SHARED_EXPENSE_CREATED","SHARED_EXPENSE",expense);
        return expenseDetail(expense,vault,actor);
    }

    public Map<String,Object> expenses(UUID vault,UUID actor,LocalDate from,LocalDate to,UUID memberId,
                                       int page,int size) {
        validatePage(page,size);
        requireMember(vault,actor);
        validateDatesOptional(from,to);
        String join=memberId==null?"":" LEFT JOIN expense_splits s ON s.shared_expense_id=e.id";
        String filter=" WHERE e.vault_id=?"+(from==null?"":" AND e.expense_date>=?")
                +(to==null?"":" AND e.expense_date<?")
                +(memberId==null?"":" AND (s.member_id=? OR e.paid_by_member_id=?)");
        List<Object> args=new ArrayList<>(List.of(vault));
        if(from!=null)args.add(from.atStartOfDay().toInstant(ZoneOffset.UTC));
        if(to!=null)args.add(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        if(memberId!=null){args.add(memberId);args.add(memberId);}
        Long total=jdbc.queryForObject("SELECT count(DISTINCT e.id) FROM shared_expenses e"+join+filter,
                Long.class,args.toArray());
        List<Object> queryArgs=new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add(page*size);
        String sql="""
                SELECT DISTINCT e.id,e.vault_id AS "vaultId",e.transaction_id AS "transactionId",
                e.paid_by_member_id AS "paidByMemberId",t.account_id AS "sourceAccountId",
                CASE WHEN e.paid_by_member_id IS NULL THEN 'VAULT' ELSE 'MEMBER' END AS "fundingType",
                e.amount,RTRIM(v.currency) AS currency,e.description,e.expense_date AS "expenseDate",t.status
                FROM shared_expenses e
                JOIN shared_vaults v ON v.id=e.vault_id
                JOIN transactions t ON t.id=e.transaction_id
                """+join+filter+" ORDER BY e.expense_date DESC LIMIT ? OFFSET ?";
        return page(expenseResources(jdbc.query(sql,(rs,n)->row(rs),queryArgs.toArray())),
                page,size,total==null?0:total);
    }
    public Map<String,Object> expenseDetail(UUID expense,UUID vault,UUID actor) {
        requireMember(vault,actor);
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT e.id,e.vault_id AS "vaultId",e.transaction_id AS "transactionId",
                e.paid_by_member_id AS "paidByMemberId",t.account_id AS "sourceAccountId",
                CASE WHEN e.paid_by_member_id IS NULL THEN 'VAULT' ELSE 'MEMBER' END AS "fundingType",
                e.amount,RTRIM(v.currency) AS currency,e.description,e.expense_date AS "expenseDate",t.status
                FROM shared_expenses e JOIN shared_vaults v ON v.id=e.vault_id
                JOIN transactions t ON t.id=e.transaction_id WHERE e.id=? AND e.vault_id=?
                """,(rs,n)->row(rs),expense,vault).stream().findFirst()
                .map(List::of)
                .orElseThrow(()->fail("SHARED_EXPENSE_INVALID","Shared expense was not found.",HttpStatus.NOT_FOUND));
        return expenseResources(rows).get(0);
    }

    public Map<String,Object> responsibility(UUID vault,UUID actor,UUID memberId) {
        requireMember(vault,actor);
        Map<String,Object> m=jdbc.query("""
                SELECT id,user_id AS "userId" FROM vault_members WHERE id=? AND vault_id=?
                """,(rs,n)->row(rs),memberId,vault).stream().findFirst()
                .orElseThrow(()->fail("MEMBER_NOT_FOUND","Member was not found.",HttpStatus.NOT_FOUND));
        BigDecimal contributed=jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM contributions WHERE member_id=?",BigDecimal.class,memberId);
        BigDecimal allocated=jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM expense_splits WHERE member_id=?",BigDecimal.class,memberId);
        BigDecimal paid=jdbc.queryForObject("SELECT COALESCE(sum(e.amount),0) FROM shared_expenses e WHERE e.paid_by_member_id=?",BigDecimal.class,memberId);
        Map<String,Object> vaultData=vault(vault,actor);
        return Map.of("memberId",memberId,"currency",vaultData.get("currency"),"contributed",contributed,
                "allocatedExpenses",allocated,"paidOnBehalf",paid,"settled",BigDecimal.ZERO,
                "netPosition",contributed.add(paid).subtract(allocated));
    }

    private Map<String,Object> contributionDetail(UUID contribution,UUID vault,UUID actor) {
        requireMember(vault,actor);
        Map<String,Object> result=jdbc.query("""
                SELECT c.id,c.vault_id AS "vaultId",c.member_id AS "memberId",t.account_id AS "sourceAccountId",
                c.amount,RTRIM(v.currency) AS currency,t.description,c.contributed_at AS "contributedAt",
                c.transaction_id AS "transactionId",t.status
                FROM contributions c JOIN transactions t ON t.id=c.transaction_id
                JOIN shared_vaults v ON v.id=c.vault_id
                WHERE c.id=? AND c.vault_id=?
                """,(rs,n)->row(rs),contribution,vault).stream().findFirst()
                .orElseThrow(()->fail("CONTRIBUTION_INVALID","Contribution was not found.",HttpStatus.NOT_FOUND));
        if(result.get("description")==null)result.remove("description");
        return result;
    }
    private Map<String,Object> member(UUID id) {
        Map<String,Object> result=jdbc.query("""
                SELECT id,user_id AS "userId",role,status,joined_at AS "joinedAt"
                FROM vault_members WHERE id=?
                """,(rs,n)->row(rs),id).get(0);
        result.put("displayName",displayNames.displayNames(List.of((UUID)result.get("userId")))
                .get(result.get("userId")));
        return result;
    }
    private List<Map<String,Object>> expenseResources(List<Map<String,Object>> rows) {
        if(rows.isEmpty())return List.of();
        String placeholders=String.join(",",java.util.Collections.nCopies(rows.size(),"?"));
        Object[] ids=rows.stream().map(row->row.get("id")).toArray();
        Map<UUID,List<Map<String,Object>>> splitsByExpense=new java.util.HashMap<>();
        jdbc.query("""
                SELECT s.shared_expense_id AS "expenseId",s.member_id AS "memberId",s.amount
                FROM expense_splits s JOIN vault_members m ON m.id=s.member_id
                WHERE s.shared_expense_id IN (""" + placeholders + ") ORDER BY m.joined_at",
                (rs,n)->row(rs),ids).forEach(split->splitsByExpense
                .computeIfAbsent((UUID)split.get("expenseId"),ignored->new ArrayList<>())
                .add(Map.of("memberId",split.get("memberId"),"amount",split.get("amount"))));
        List<Map<String,Object>> resources=new ArrayList<>(rows.size());
        for(Map<String,Object> row:rows){
            Map<String,Object> resource=new LinkedHashMap<>();
            resource.put("id",row.get("id"));
            resource.put("vaultId",row.get("vaultId"));
            resource.put("amount",row.get("amount"));
            resource.put("currency",row.get("currency"));
            resource.put("description",row.get("description"));
            resource.put("expenseDate",row.get("expenseDate"));
            Map<String,Object> funding=new LinkedHashMap<>();
            funding.put("type",row.get("fundingType"));
            if("MEMBER".equals(row.get("fundingType")))funding.put("sourceAccountId",row.get("sourceAccountId"));
            resource.put("funding",funding);
            resource.put("paidByMemberId",row.get("paidByMemberId"));
            resource.put("splits",splitsByExpense.getOrDefault((UUID)row.get("id"),List.of()));
            resource.put("transactionId",row.get("transactionId"));
            resource.put("status",row.get("status"));
            resources.add(resource);
        }
        return resources;
    }
    private Map<String,Object> activeMember(UUID vault,UUID member) {
        return jdbc.query("""
                SELECT id,user_id AS "userId",role,status FROM vault_members
                WHERE id=? AND vault_id=? AND status='ACTIVE'
                """,(rs,n)->row(rs),member,vault).stream().findFirst()
                .orElseThrow(()->fail("EXPENSE_SPLIT_MEMBER_INVALID","Member is not active in this vault.",HttpStatus.UNPROCESSABLE_ENTITY));
    }
    private Map<String,Object> requireMember(UUID vault,UUID user) {
        if(jdbc.queryForObject("SELECT count(*) FROM shared_vaults WHERE id=?",Long.class,vault)==0)
            throw fail("VAULT_NOT_FOUND","Vault was not found.",HttpStatus.NOT_FOUND);
        return jdbc.query("""
                SELECT id,user_id AS "userId",role,status FROM vault_members
                WHERE vault_id=? AND user_id=? AND status='ACTIVE'
                """,(rs,n)->row(rs),vault,user).stream().findFirst()
                .orElseThrow(()->fail("VAULT_MEMBERSHIP_REQUIRED","Active vault membership is required.",HttpStatus.FORBIDDEN));
    }
    private void requireOwner(UUID vault,UUID user) {
        Map<String,Object> m=requireMember(vault,user);
        if(!"OWNER".equals(m.get("role")))throw fail("VAULT_OWNER_REQUIRED","Vault owner permission is required.",HttpStatus.FORBIDDEN);
    }
    private Map<String,Object> lockVault(UUID vault) {
        return jdbc.query("""
                SELECT id,RTRIM(currency) AS currency,current_balance AS balance,status,version FROM shared_vaults WHERE id=? FOR UPDATE
                """,(rs,n)->row(rs),vault).stream().findFirst()
                .orElseThrow(()->fail("VAULT_NOT_FOUND","Vault was not found.",HttpStatus.NOT_FOUND));
    }
    private void ensureActiveVault(Map<String,Object> v) {
        if(!"ACTIVE".equals(v.get("status")))throw fail("VAULT_NOT_ACTIVE","Vault is not active.",HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private UUID reserve(UUID user,String operation,String key,byte[] bytes) {
        if(key==null||key.isBlank()||key.length()>200)throw fail("VALIDATION_ERROR","A valid Idempotency-Key is required.",HttpStatus.BAD_REQUEST);
        String h=HexFormat.of().formatHex(bytes);
        int inserted=jdbc.update("""
                INSERT INTO idempotency_keys(id,user_id,key,operation,request_hash,created_at)
                VALUES (?,?,?,?,?,?) ON CONFLICT(user_id,operation,key) DO NOTHING
                """,UUID.randomUUID(),user,key,operation,h,Instant.now());
        if(inserted==1)return null;
        Map<String,Object> old=jdbc.queryForMap("""
                SELECT request_hash,response_reference AS reference FROM idempotency_keys
                WHERE user_id=? AND operation=? AND key=? FOR UPDATE
                """,user,operation,key);
        if(!h.equals(old.get("request_hash")))throw fail("IDEMPOTENCY_REQUEST_MISMATCH","Idempotency key was already used for a different request.",HttpStatus.CONFLICT);
        if(old.get("reference")==null)throw fail("IDEMPOTENCY_KEY_REUSED","The request with this key is still being processed.",HttpStatus.CONFLICT);
        return (UUID)old.get("reference");
    }
    private void setReference(UUID user,String op,String key,UUID ref) {
        jdbc.update("UPDATE idempotency_keys SET response_reference=? WHERE user_id=? AND operation=? AND key=?",ref,user,op,key);
    }
    private byte[] hash(Object o) {
        try{return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(o));}
        catch(NoSuchAlgorithmException|JsonProcessingException e){throw new IllegalStateException(e);}
    }
    private void audit(UUID actor,String action,String entity,UUID id) {
        jdbc.update("INSERT INTO audit_logs(id,actor_user_id,action,entity_type,entity_id,created_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),actor,action,entity,id,Instant.now());
    }
    private static void requireAmount(BigDecimal amount) {
        if(amount==null||amount.signum()<=0||!fitsNumeric(amount))
            throw fail("CONTRIBUTION_INVALID","Amount must be positive and fit NUMERIC(19,4).",HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private static void requireFitsNumeric(BigDecimal balance,BigDecimal amount) {
        if(!fitsNumeric(balance.add(amount)))
            throw fail("CONTRIBUTION_INVALID","Resulting vault balance does not fit NUMERIC(19,4).",HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private static boolean fitsNumeric(BigDecimal value) {
        return value.scale()<=4&&value.precision()-value.scale()<=15;
    }
    private static String currency(String c) {
        if(c==null||!c.matches("[A-Z]{3}"))throw fail("VALIDATION_ERROR","Currency must be a three-letter uppercase code.",HttpStatus.BAD_REQUEST);
        return c;
    }
    private static String requiredName(String n) {
        String s=n==null?"":n.trim();
        if(s.isEmpty()||s.length()>120)throw fail("VALIDATION_ERROR","Name is required and must be at most 120 characters.",HttpStatus.BAD_REQUEST);
        return s;
    }
    private static void validateDates(LocalDate from,LocalDate to) {
        if(from==null||to==null||from.isAfter(to))throw fail("VALIDATION_ERROR","Valid from and to dates are required.",HttpStatus.BAD_REQUEST);
    }
    private static void validateDatesOptional(LocalDate from,LocalDate to) { if(from!=null&&to!=null&&from.isAfter(to))throw fail("VALIDATION_ERROR","from must not be after to.",HttpStatus.BAD_REQUEST); }
    private static void validatePage(int page,int size) {
        if(page<0||size<1||size>100||page>Integer.MAX_VALUE/size)
            throw fail("VALIDATION_ERROR","page must be non-negative and size must be between 1 and 100.",HttpStatus.BAD_REQUEST);
    }
    private static Map<String,Object> page(List<?> items,int page,int size,long total) {
        return Map.of("items",items,"page",page,"size",size,"totalItems",total,
                "totalPages",total==0?0:(int)Math.ceil((double)total/size));
    }
    private static Map<String,Object> row(java.sql.ResultSet rs)throws java.sql.SQLException {
        Map<String,Object> out=new LinkedHashMap<>();
        var md=rs.getMetaData();
        for(int i=1;i<=md.getColumnCount();i++)out.put(md.getColumnLabel(i),rs.getObject(i));
        return out;
    }
    private static BusinessApiException fail(String c,String m,HttpStatus s){return new BusinessApiException(c,m,s);}
}
