package com.savemon.personalfinance.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.personalfinance.domain.PositiveAmount;
import com.savemon.personalfinance.domain.TransactionRules;
import com.savemon.personalfinance.application.DebitOwnedAccount;
import com.savemon.personalfinance.application.PersonalFinanceOperations;
import com.savemon.personalfinance.application.PersonalFinanceOperations.EditRequest;
import com.savemon.personalfinance.application.PersonalFinanceOperations.FinancialRequest;
import com.savemon.personalfinance.application.PersonalFinanceOperations.ItemInput;
import com.savemon.shared.interfaces.exception.BusinessApiException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC implementation of PersonalFinanceOperations and DebitOwnedAccount.
 * Every financial mutation is transactionally persisted with its balance projection.
 */
@Service
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcPersonalFinanceAdapter implements DebitOwnedAccount, PersonalFinanceOperations {
    private static final RowMapper<Map<String, Object>> ROW = (rs, n) -> row(rs);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcPersonalFinanceAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void debit(UUID authenticatedUserId, UUID accountId, BigDecimal amount, String currency) {
        Map<String, Object> account = lockAccount(accountId, authenticatedUserId);
        requireActive(account);
        requireCurrency(currency, (String) account.get("currency"));
        requireAmount(amount);
        int updated = jdbc.update("""
                UPDATE accounts SET current_balance=current_balance-?, version=version+1, updated_at=?
                WHERE id=? AND current_balance>=?
                """, amount, Instant.now(), accountId, amount);
        if (updated != 1) throw failure("INSUFFICIENT_BALANCE", "Insufficient account balance.", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Transactional
    public Map<String, Object> createAccount(UUID user, String name, String type, String currency) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO accounts(id,user_id,name,type,current_balance,currency,status,version,created_at,updated_at)
                VALUES (?,?,?,?,0,?,'ACTIVE',0,?,?)
                """, id, user, clean(name, "name"), type, currency(currency), now, now);
        audit(user, "ACCOUNT_CREATED", "ACCOUNT", id);
        return account(id, user);
    }

    public Map<String, Object> accounts(UUID user, String status,int page,int size) {
        validatePage(page,size);
        if (status != null && !List.of("ACTIVE", "ARCHIVED").contains(status))
            throw failure("VALIDATION_ERROR", "status must be ACTIVE or ARCHIVED.", HttpStatus.BAD_REQUEST);
        Long total=status==null?jdbc.queryForObject("SELECT count(*) FROM accounts WHERE user_id=?",Long.class,user)
                :jdbc.queryForObject("SELECT count(*) FROM accounts WHERE user_id=? AND status=?",Long.class,user,status);
        List<Map<String,Object>> items=status==null?jdbc.query("""
                SELECT id,name,type,RTRIM(currency) AS currency,current_balance AS balance,status
                FROM accounts WHERE user_id=? ORDER BY created_at LIMIT ? OFFSET ?
                """,ROW,user,size,page*size):jdbc.query("""
                SELECT id,name,type,RTRIM(currency) AS currency,current_balance AS balance,status
                FROM accounts WHERE user_id=? AND status=? ORDER BY created_at LIMIT ? OFFSET ?
                """,ROW,user,status,size,page*size);
        return pageResult(items,page,size,total==null?0:total);
    }

    public Map<String, Object> account(UUID id, UUID user) {
        return jdbc.query("SELECT id,name,type,RTRIM(currency) AS currency,current_balance AS balance,status FROM accounts WHERE id=? AND user_id=?",
                ROW, id, user).stream().findFirst().orElseThrow(() -> failure("ACCOUNT_NOT_FOUND", "Account was not found.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Map<String, Object> updateAccount(UUID id, UUID user, String name) {
        int count = jdbc.update("UPDATE accounts SET name=?,updated_at=?,version=version+1 WHERE id=? AND user_id=? AND status='ACTIVE'",
                clean(name, "name"), Instant.now(), id, user);
        if (count == 0) account(id, user);
        return account(id, user);
    }

    @Transactional
    public void archiveAccount(UUID id, UUID user) {
        int count = jdbc.update("UPDATE accounts SET status='ARCHIVED',updated_at=?,version=version+1 WHERE id=? AND user_id=? AND status='ACTIVE'",
                Instant.now(), id, user);
        if (count == 0) account(id, user);
        audit(user, "ACCOUNT_ARCHIVED", "ACCOUNT", id);
    }

    @Transactional
    public Map<String, Object> createCategory(UUID user, String name, String type, UUID parentId) {
        if (parentId != null) category(parentId, user, type, false);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO categories(id,user_id,parent_id,name,type,status,created_at,updated_at)
                VALUES (?,?,?,?,?,'ACTIVE',?,?)
                """, id, user, parentId, clean(name, "name"), type, now, now);
        return category(id, user, type, true);
    }

    public Map<String,Object> categories(UUID user, String type,int page,int size) {
        validatePage(page,size);
        if (type != null && !List.of("INCOME", "EXPENSE").contains(type))
            throw failure("VALIDATION_ERROR", "type must be INCOME or EXPENSE.", HttpStatus.BAD_REQUEST);
        Long total=type==null?jdbc.queryForObject("SELECT count(*) FROM categories WHERE user_id=?",Long.class,user)
                :jdbc.queryForObject("SELECT count(*) FROM categories WHERE user_id=? AND type=?",Long.class,user,type);
        List<Map<String,Object>> items=type==null?jdbc.query("""
                SELECT id,name,type,parent_id AS "parentId",status FROM categories WHERE user_id=?
                ORDER BY name LIMIT ? OFFSET ?
                """,ROW,user,size,page*size):jdbc.query("""
                SELECT id,name,type,parent_id AS "parentId",status FROM categories WHERE user_id=? AND type=?
                ORDER BY name LIMIT ? OFFSET ?
                """,ROW,user,type,size,page*size);
        return pageResult(items,page,size,total==null?0:total);
    }

    @Transactional
    public Map<String,Object> updateCategory(UUID id, UUID user, String name) {
        int changed = jdbc.update("""
                UPDATE categories SET name=?,updated_at=? WHERE id=? AND user_id=? AND status='ACTIVE'
                """, clean(name, "name"), Instant.now(), id, user);
        if (changed == 0) {
            categoryForOwner(id, user);
        }
        return categoryForOwner(id, user);
    }

    @Transactional
    public void archiveCategory(UUID id, UUID user) {
        int changed = jdbc.update("""
                UPDATE categories SET status='ARCHIVED',updated_at=? WHERE id=? AND user_id=? AND status='ACTIVE'
                """, Instant.now(), id, user);
        if (changed == 0) categoryForOwner(id, user);
    }

    @Transactional
    public Map<String, Object> record(UUID user, String type, String key, FinancialRequest request) {
        if (request == null || request.accountId() == null)
            throw failure("VALIDATION_ERROR", "accountId is required.", HttpStatus.BAD_REQUEST);
        requireAmount(request.amount());
        if (request.transactionDate() == null) throw failure("VALIDATION_ERROR", "transactionDate is required.", HttpStatus.BAD_REQUEST);
        String currency = currency(request.currency());
        if (request.description() != null && request.description().length() > 500)
            throw failure("VALIDATION_ERROR", "description must be at most 500 characters.", HttpStatus.BAD_REQUEST);
        byte[] hash = hash(request);
        UUID duplicate = reserve(user, "PERSONAL_" + type, key, hash);
        if (duplicate != null) return transaction(duplicate, user);

        Map<String, Object> account = lockAccount(request.accountId(), user);
        requireActive(account);
        requireCurrency(currency, (String) account.get("currency"));
        validateItems(user, type, request.amount(), request.items());
        if ("EXPENSE".equals(type)) {
            int updated = jdbc.update("""
                    UPDATE accounts SET current_balance=current_balance-?,version=version+1,updated_at=?
                    WHERE id=? AND current_balance>=?
                    """, request.amount(), Instant.now(), request.accountId(), request.amount());
            if (updated != 1) throw failure("INSUFFICIENT_BALANCE", "Insufficient account balance.", HttpStatus.UNPROCESSABLE_ENTITY);
        } else {
            requireFitsNumeric((BigDecimal) account.get("balance"), request.amount());
            jdbc.update("UPDATE accounts SET current_balance=current_balance+?,version=version+1,updated_at=? WHERE id=?",
                    request.amount(), Instant.now(), request.accountId());
        }

        UUID txId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO transactions(id,user_id,account_id,type,amount,currency,transaction_date,description,status,
                    idempotency_key,version,created_at,updated_at)
                VALUES (?,?,?, ?,?,?,?,?,'ACTIVE',?,0,?,?)
                """, txId, user, request.accountId(), type, request.amount(), currency,
                request.transactionDate(), request.description(), key, now, now);
        insertItems(txId, user, type, request.items());
        setIdemReference(user, "PERSONAL_" + type, key, txId);
        audit(user, "TRANSACTION_CREATED", "TRANSACTION", txId);
        return transaction(txId, user);
    }

    public Map<String, Object> transaction(UUID id, UUID user) {
        List<Map<String, Object>> records = jdbc.query("""
                SELECT t.id,t.type,t.amount,RTRIM(t.currency) AS currency,t.transaction_date AS "transactionDate",t.description,t.status,
                       a.id AS "accountId",a.name AS "accountName"
                FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id
                WHERE t.id=? AND t.user_id=? AND t.vault_id IS NULL
                """, ROW, id, user);
        if (records.isEmpty()) throw failure("TRANSACTION_NOT_FOUND", "Transaction was not found.", HttpStatus.NOT_FOUND);
        Map<String, Object> result = new LinkedHashMap<>(records.get(0));
        result.put("account", Map.of("id", result.remove("accountId"), "name", result.remove("accountName")));
        result.put("items", jdbc.query("""
                SELECT ti.category_id AS "categoryId",c.name AS "categoryName",ti.amount,ti.description
                FROM transaction_items ti JOIN categories c ON c.id=ti.category_id WHERE ti.transaction_id=?
                ORDER BY ti.created_at
                """, ROW, id));
        return result;
    }

    public Map<String, Object> history(UUID user, UUID accountId, String type, String status,
                                       Instant from, Instant to, UUID categoryId, String currency, int page, int size) {
        validatePage(page, size);
        if (type != null && !List.of("INCOME", "EXPENSE").contains(type))
            throw failure("VALIDATION_ERROR", "type must be INCOME or EXPENSE.", HttpStatus.BAD_REQUEST);
        if (status != null && !List.of("ACTIVE", "REVERSED").contains(status))
            throw failure("VALIDATION_ERROR", "status must be ACTIVE or REVERSED.", HttpStatus.BAD_REQUEST);
        StringBuilder filter = new StringBuilder(" WHERE t.user_id=? AND t.vault_id IS NULL");
        List<Object> args = new ArrayList<>();
        args.add(user);
        if (accountId != null) { filter.append(" AND t.account_id=?"); args.add(accountId); }
        if (type != null) { filter.append(" AND t.type=?"); args.add(type); }
        if (status != null) { filter.append(" AND t.status=?"); args.add(status); }
        if (from != null) { filter.append(" AND t.transaction_date>=?"); args.add(from); }
        if (to != null) { filter.append(" AND t.transaction_date<?"); args.add(to); }
        if (currency != null) { filter.append(" AND t.currency=?"); args.add(currency(currency)); }
        if (categoryId != null) {
            filter.append(" AND EXISTS(SELECT 1 FROM transaction_items ti WHERE ti.transaction_id=t.id AND ti.category_id=?)");
            args.add(categoryId);
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM transactions t" + filter, Long.class, args.toArray());
        args.add(size); args.add(page * size);
        List<Map<String, Object>> items = jdbc.query("""
                SELECT t.id,t.type,t.amount,RTRIM(t.currency) AS currency,t.transaction_date AS "transactionDate",
                       t.description,t.status,a.id AS "accountId",a.name AS "accountName"
                FROM transactions t JOIN accounts a ON a.id=t.account_id
                """ + filter + " ORDER BY t.transaction_date DESC,t.id DESC LIMIT ? OFFSET ?", ROW, args.toArray());
        attachTransactionAccountsAndItems(items);
        return pageResult(items, page, size, total == null ? 0 : total);
    }

    @Transactional
    public Map<String, Object> updateTransaction(UUID id, UUID user, EditRequest request) {
        if (request.description() != null && request.description().length() > 500)
            throw failure("VALIDATION_ERROR", "description must be at most 500 characters.", HttpStatus.BAD_REQUEST);
        Map<String, Object> tx = jdbc.query("""
                SELECT id,account_id AS accountId,type,amount,currency,status,version FROM transactions
                WHERE id=? AND user_id=? AND vault_id IS NULL FOR UPDATE
                """, ROW, id, user).stream().findFirst()
                .orElseThrow(() -> failure("TRANSACTION_NOT_FOUND", "Transaction was not found.", HttpStatus.NOT_FOUND));
        if (!"ACTIVE".equals(tx.get("status"))) throw failure("TRANSACTION_ALREADY_REVERSED", "Reversed transactions cannot be edited.", HttpStatus.CONFLICT);
        if (request.items() != null) {
            validateItems(user, (String) tx.get("type"), (BigDecimal) tx.get("amount"), request.items());
            jdbc.update("DELETE FROM transaction_items WHERE transaction_id=?", id);
            insertItems(id, user, (String) tx.get("type"), request.items());
        }
        int changed = jdbc.update("""
                UPDATE transactions SET description=COALESCE(?,description),transaction_date=COALESCE(?,transaction_date),
                version=version+1,updated_at=? WHERE id=? AND version=?
                """, request.description(), request.transactionDate(), Instant.now(), id, tx.get("version"));
        if (changed != 1)
            throw failure("TRANSACTION_CONCURRENCY_CONFLICT", "Transaction changed concurrently.", HttpStatus.CONFLICT);
        audit(user, "TRANSACTION_UPDATED", "TRANSACTION", id);
        return transaction(id, user);
    }

    @Transactional
    public Map<String, Object> reverse(UUID id, UUID user, String key, String reason) {
        byte[] requestHash = hash(Map.of("transactionId", id, "reason", reason == null ? "" : reason));
        UUID duplicate = reserve(user, "PERSONAL_REVERSE", key, requestHash);
        if (duplicate != null) {
            Map<String,Object> original = jdbc.query("""
                    SELECT id,account_id AS accountId,type,amount,status,updated_at AS "reversedAt"
                    FROM transactions WHERE id=? AND user_id=? AND vault_id IS NULL
                    """, ROW, duplicate, user).stream().findFirst()
                    .orElseThrow(() -> failure("TRANSACTION_NOT_FOUND", "Transaction was not found.", HttpStatus.NOT_FOUND));
            if (!"REVERSED".equals(original.get("status")))
                throw failure("TRANSACTION_ALREADY_REVERSED", "Transaction has already been reversed.", HttpStatus.CONFLICT);
            String reverseType = "INCOME".equals(original.get("type")) ? "EXPENSE" : "INCOME";
            UUID reversalId = jdbc.query("""
                    SELECT id FROM transactions
                    WHERE user_id=? AND vault_id IS NULL AND idempotency_key=? AND account_id=?
                      AND amount=? AND type=? AND created_at=?
                    """, (rs,n) -> rs.getObject("id",UUID.class), user,key,original.get("accountId"),
                    original.get("amount"),reverseType,original.get("reversedAt")).stream().findFirst()
                    .orElseThrow(() -> failure("IDEMPOTENCY_KEY_REUSED",
                            "The reversal response could not be recovered.", HttpStatus.CONFLICT));
            return reversalResponse(duplicate,reversalId,(Instant)original.get("reversedAt"),reason);
        }
        Map<String, Object> original = jdbc.query("""
                SELECT id,account_id AS accountId,type,amount,currency,transaction_date AS transactionDate,status,version
                FROM transactions WHERE id=? AND user_id=? AND vault_id IS NULL FOR UPDATE
                """, ROW, id, user).stream().findFirst()
                .orElseThrow(() -> failure("TRANSACTION_NOT_FOUND", "Transaction was not found.", HttpStatus.NOT_FOUND));
        if (!"ACTIVE".equals(original.get("status"))) throw failure("TRANSACTION_ALREADY_REVERSED", "Transaction has already been reversed.", HttpStatus.CONFLICT);
        UUID accountId = (UUID) original.get("accountId");
        BigDecimal amount = (BigDecimal) original.get("amount");
        String reverseType = "INCOME".equals(original.get("type")) ? "EXPENSE" : "INCOME";
        Map<String, Object> account = lockAccount(accountId, user);
        BigDecimal currentBalance = (BigDecimal) account.get("balance");
        if ("EXPENSE".equals(reverseType)) {
            requireFitsNumeric(currentBalance, amount.negate());
            jdbc.update("UPDATE accounts SET current_balance=current_balance-?,version=version+1,updated_at=? WHERE id=?",
                    amount, Instant.now(), accountId);
        } else {
            requireFitsNumeric(currentBalance, amount);
            jdbc.update("UPDATE accounts SET current_balance=current_balance+?,version=version+1,updated_at=? WHERE id=?",
                    amount, Instant.now(), accountId);
        }
        Instant now = Instant.now();
        UUID reversalId = UUID.randomUUID();
        jdbc.update("UPDATE transactions SET status='REVERSED',version=version+1,updated_at=? WHERE id=? AND version=?",
                now, id, original.get("version"));
        jdbc.update("""
                INSERT INTO transactions(id,user_id,account_id,type,amount,currency,transaction_date,description,status,
                idempotency_key,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',?,0,?,?)
                """, reversalId, user, accountId, reverseType, amount, original.get("currency"),
                original.get("transactionDate"), cleanOptional(reason).isBlank() ? "Reversal" : cleanOptional(reason), key, now, now);
        setIdemReference(user, "PERSONAL_REVERSE", key, id);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reversalTransactionId", reversalId);
        if (reason != null) metadata.put("reason", reason);
        audit(user, "TRANSACTION_REVERSED", "TRANSACTION", id, metadata);
        return reversalResponse(id,reversalId,now,reason);
    }

    private Map<String,Object> reversalResponse(UUID originalId,UUID reversalId,Instant reversedAt,String reason) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",originalId);
        result.put("originalTransactionId",originalId);
        result.put("reversalTransactionId",reversalId);
        result.put("status","REVERSED");
        result.put("reversedAt",reversedAt);
        if(reason!=null)result.put("reason",reason);
        return result;
    }

    private void attachTransactionAccountsAndItems(List<Map<String,Object>> transactions) {
        if(transactions.isEmpty())return;
        List<UUID> ids=transactions.stream().map(item->(UUID)item.get("id")).toList();
        String placeholders=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
        Map<UUID,List<Map<String,Object>>> itemsByTransaction=new LinkedHashMap<>();
        jdbc.query("""
                SELECT ti.transaction_id AS "transactionId",ti.category_id AS "categoryId",
                       c.name AS "categoryName",ti.amount
                FROM transaction_items ti JOIN categories c ON c.id=ti.category_id
                WHERE ti.transaction_id IN ("""+placeholders+") ORDER BY ti.created_at",
                ROW,ids.toArray()).forEach(item->itemsByTransaction
                .computeIfAbsent((UUID)item.get("transactionId"),ignored->new ArrayList<>()).add(Map.of(
                        "categoryId",item.get("categoryId"),"categoryName",item.get("categoryName"),
                        "amount",item.get("amount"))));
        for(Map<String,Object> item:transactions) {
            UUID id=(UUID)item.get("id");
            item.put("account",Map.of("id",item.remove("accountId"),"name",item.remove("accountName")));
            item.put("items",itemsByTransaction.getOrDefault(id,List.of()));
        }
    }

    public Map<String, Object> calendar(UUID user, LocalDate from, LocalDate to, String currency) {
        validateDatesOptional(from, to);
        String filter = "t.user_id=? AND t.vault_id IS NULL AND t.status='ACTIVE'";
        List<Object> args = new ArrayList<>(List.of(user));
        if (from != null) { filter += " AND t.transaction_date>=?"; args.add(from.atStartOfDay().toInstant(ZoneOffset.UTC)); }
        if (to != null) { filter += " AND t.transaction_date<?"; args.add(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)); }
        String selectedCurrency = currency(currency);
        filter += " AND t.currency=?";
        args.add(selectedCurrency);
        List<Map<String,Object>> raw = jdbc.query("""
                SELECT CAST(t.transaction_date AT TIME ZONE 'UTC' AS date) AS date,
                SUM(CASE WHEN t.type='INCOME' THEN t.amount ELSE 0 END) AS income,
                SUM(CASE WHEN t.type='EXPENSE' THEN t.amount ELSE 0 END) AS expense,
                count(*) AS "transactionCount"
                FROM transactions t WHERE
                """ + filter + " GROUP BY 1 ORDER BY 1", ROW, args.toArray());
        return Map.of("currency", selectedCurrency, "days", raw);
    }

    public Map<String,Object> summary(UUID user, LocalDate from, LocalDate to, String currency) {
        validateDatesOptional(from, to);
        String selectedCurrency = currency(currency);
        String filter = "user_id=? AND vault_id IS NULL AND status='ACTIVE' AND currency=?";
        List<Object> args = new ArrayList<>(List.of(user, selectedCurrency));
        if (from != null) { filter += " AND transaction_date>=?"; args.add(from.atStartOfDay().toInstant(ZoneOffset.UTC)); }
        if (to != null) { filter += " AND transaction_date<?"; args.add(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)); }
        List<Map<String,Object>> rows = jdbc.query("""
                SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amount ELSE 0 END),0) AS income,
                COALESCE(SUM(CASE WHEN type='EXPENSE' THEN amount ELSE 0 END),0) AS expense
                FROM transactions WHERE
                """ + filter, ROW, args.toArray());
        Map<String,Object> totals = rows.isEmpty() ? Map.of() : rows.get(0);
        BigDecimal income = (BigDecimal) totals.getOrDefault("income", BigDecimal.ZERO);
        BigDecimal expense = (BigDecimal) totals.getOrDefault("expense", BigDecimal.ZERO);
        return Map.of("currency", selectedCurrency, "income", income, "expense", expense,
                "net", income.subtract(expense));
    }

    private void validateItems(UUID user, String type, BigDecimal amount, List<ItemInput> items) {
        List<ItemInput> safe = items == null ? List.of() : items;
        if (safe.stream().anyMatch(item -> item == null || item.categoryId() == null || item.amount() == null))
            throw failure("VALIDATION_ERROR", "Each transaction item requires a category and amount.", HttpStatus.BAD_REQUEST);
        try {
            TransactionRules.validateItems(new PositiveAmount(amount), safe.stream()
                    .map(item -> new TransactionRules.ItemAmount(item.categoryId(), new PositiveAmount(item.amount()))).toList());
        } catch (IllegalArgumentException exception) {
            String code = exception.getMessage() != null && exception.getMessage().contains("total")
                    ? "TRANSACTION_ITEM_TOTAL_MISMATCH" : "INVALID_TRANSACTION_AMOUNT";
            throw failure(code, code.equals("TRANSACTION_ITEM_TOTAL_MISMATCH")
                    ? "Transaction item amounts must equal the transaction amount."
                    : "Transaction item amounts must be positive.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        for (ItemInput item : safe) {
            if (item.description() != null && item.description().length() > 500)
                throw failure("VALIDATION_ERROR", "Item descriptions must be at most 500 characters.", HttpStatus.BAD_REQUEST);
            category(item.categoryId(), user, type, false);
        }
    }

    private void insertItems(UUID transactionId, UUID user, String type, List<ItemInput> items) {
        if (items == null) return;
        Instant now = Instant.now();
        for (ItemInput item : items) {
            category(item.categoryId(), user, type, false);
            jdbc.update("""
                    INSERT INTO transaction_items(id,transaction_id,category_id,description,amount,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), transactionId, item.categoryId(), item.description(), item.amount(), now, now);
        }
    }

    private Map<String,Object> category(UUID id, UUID user, String type, boolean includeArchived) {
        String sql = "SELECT id,name,type,parent_id AS \"parentId\",status FROM categories WHERE id=? AND user_id=? AND type=?"
                + (includeArchived ? "" : " AND status='ACTIVE'");
        return jdbc.query(sql, ROW, id, user, type).stream().findFirst()
                .orElseThrow(() -> failure("CATEGORY_NOT_OWNED", "Category was not found or is not valid for this transaction.", HttpStatus.FORBIDDEN));
    }

    private Map<String,Object> lockAccount(UUID id, UUID user) {
        List<Map<String,Object>> found = jdbc.query("""
                SELECT id,user_id AS userId,name,type,RTRIM(currency) AS currency,current_balance AS balance,status,version
                FROM accounts WHERE id=? AND user_id=? FOR UPDATE
                """, ROW, id, user);
        if (!found.isEmpty()) return found.get(0);
        if (jdbc.queryForObject("SELECT count(*) FROM accounts WHERE id=?", Long.class, id) > 0)
            throw failure("ACCOUNT_NOT_OWNED", "Account does not belong to the authenticated user.", HttpStatus.FORBIDDEN);
        throw failure("ACCOUNT_NOT_FOUND", "Account was not found.", HttpStatus.NOT_FOUND);
    }
    private Map<String,Object> categoryForOwner(UUID id, UUID user) {
        List<Map<String,Object>> found = jdbc.query("""
                SELECT id,name,type,parent_id AS "parentId",status FROM categories WHERE id=? AND user_id=?
                """, ROW, id, user);
        if (!found.isEmpty()) return found.get(0);
        if (jdbc.queryForObject("SELECT count(*) FROM categories WHERE id=?", Long.class, id) > 0)
            throw failure("CATEGORY_NOT_OWNED", "Category does not belong to the authenticated user.", HttpStatus.FORBIDDEN);
        throw failure("CATEGORY_NOT_FOUND", "Category was not found.", HttpStatus.NOT_FOUND);
    }
    private void requireActive(Map<String,Object> account) {
        if (!"ACTIVE".equals(account.get("status"))) throw failure("ACCOUNT_ARCHIVED", "Account is archived.", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private static void requireAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || !fitsNumeric(value))
            throw failure("INVALID_TRANSACTION_AMOUNT", "Amount must be positive and fit NUMERIC(19,4).", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private static void requireFitsNumeric(BigDecimal current, BigDecimal delta) {
        if (!fitsNumeric(current.add(delta)))
            throw failure("INVALID_TRANSACTION_AMOUNT", "Resulting balance does not fit NUMERIC(19,4).", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private static boolean fitsNumeric(BigDecimal value) {
        return value.scale() <= 4 && value.precision() - value.scale() <= 15;
    }
    private static void requireCurrency(String requested, String actual) {
        if (!requested.equals(actual)) throw failure("VALIDATION_ERROR", "Currency must match the account or vault currency.", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private UUID reserve(UUID user, String operation, String key, byte[] requestHash) {
        if (key == null || key.isBlank() || key.length() > 200) throw failure("VALIDATION_ERROR", "A valid Idempotency-Key is required.", HttpStatus.BAD_REQUEST);
        String hash = HexFormat.of().formatHex(requestHash);
        int inserted = jdbc.update("""
                INSERT INTO idempotency_keys(id,user_id,key,operation,request_hash,created_at)
                VALUES (?,?,?,?,?,?) ON CONFLICT(user_id,operation,key) DO NOTHING
                """, UUID.randomUUID(), user, key, operation, hash, Instant.now());
        if (inserted == 1) return null;
        Map<String,Object> existing = jdbc.queryForMap("""
                SELECT request_hash,response_reference AS reference FROM idempotency_keys
                WHERE user_id=? AND operation=? AND key=? FOR UPDATE
                """, user, operation, key);
        if (!hash.equals(existing.get("request_hash")))
            throw failure("IDEMPOTENCY_REQUEST_MISMATCH", "Idempotency key was already used for a different request.", HttpStatus.CONFLICT);
        if (existing.get("reference") == null)
            throw failure("IDEMPOTENCY_KEY_REUSED", "The request with this key is still being processed.", HttpStatus.CONFLICT);
        return (UUID) existing.get("reference");
    }
    private void setIdemReference(UUID user, String operation, String key, UUID reference) {
        jdbc.update("UPDATE idempotency_keys SET response_reference=? WHERE user_id=? AND operation=? AND key=?",
                reference, user, operation, key);
    }
    private byte[] hash(Object request) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private void audit(UUID actor, String action, String entity, UUID id) {
        jdbc.update("INSERT INTO audit_logs(id,actor_user_id,action,entity_type,entity_id,created_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), actor, action, entity, id, Instant.now());
    }
    private void audit(UUID actor, String action, String entity, UUID id, Object metadata) {
        try {
            jdbc.update("""
                    INSERT INTO audit_logs(id,actor_user_id,action,entity_type,entity_id,metadata,created_at)
                    VALUES (?,?,?,?,?,CAST(? AS jsonb),?)
                    """, UUID.randomUUID(), actor, action, entity, id, json.writeValueAsString(metadata), Instant.now());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit metadata.", exception);
        }
    }
    private static String currency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) throw failure("VALIDATION_ERROR", "Currency must be a three-letter uppercase code.", HttpStatus.BAD_REQUEST);
        return value;
    }
    private static String clean(String value, String field) {
        String text = cleanOptional(value);
        if (text.isBlank() || text.length() > 120) throw failure("VALIDATION_ERROR", field + " is required and must be at most 120 characters.", HttpStatus.BAD_REQUEST);
        return text;
    }
    private static String cleanOptional(String value) { return value == null ? "" : value.trim(); }
    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size)
            throw failure("VALIDATION_ERROR", "page must be non-negative and size must be between 1 and 100.", HttpStatus.BAD_REQUEST);
    }
    private static void validateDatesOptional(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to))
            throw failure("VALIDATION_ERROR", "from must not be after to.", HttpStatus.BAD_REQUEST);
    }
    private static Map<String,Object> pageResult(List<?> items, int page, int size, long total) {
        return Map.of("items", items, "page", page, "size", size, "totalItems", total,
                "totalPages", total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }
    private static Map<String,Object> row(ResultSet rs) throws SQLException {
        Map<String,Object> map = new LinkedHashMap<>();
        var metadata = rs.getMetaData();
        for (int i=1; i<=metadata.getColumnCount(); i++) map.put(metadata.getColumnLabel(i), rs.getObject(i));
        return map;
    }
    private static BusinessApiException failure(String code, String message, HttpStatus status) {
        return new BusinessApiException(code, message, status);
    }
}
