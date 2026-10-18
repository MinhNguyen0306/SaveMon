package com.savemon.sharedfinance.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.identity.application.SubjectStatusQuery;
import com.savemon.personalfinance.application.SharedOperationLedger;
import com.savemon.shared.interfaces.exception.BusinessApiException;
import com.savemon.sharedfinance.application.CommandJournal;
import com.savemon.sharedfinance.application.SharedLedgerCommands;
import com.savemon.sharedfinance.application.SharedLedgerCommands.Expense;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Shared-owned transaction host for isolated V5 shared ledger commands. */
@Repository
public class JdbcSharedLedgerStore implements SharedLedgerCommands.Store {
    private static final BigDecimal MAX = new BigDecimal("999999999999999.9999");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SharedOperationLedger personal;
    private final SubjectStatusQuery subjects;
    private final CommandJournal journal;
    private final SharedLedgerHistory history;

    public JdbcSharedLedgerStore(JdbcTemplate jdbc, ObjectMapper json, SharedOperationLedger personal,
                                 SubjectStatusQuery subjects, CommandJournal journal, SharedLedgerHistory history) {
        this.jdbc = jdbc;
        this.json = json;
        this.personal = personal;
        this.subjects = subjects;
        this.journal = journal;
        this.history = history;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommandJournal.Receipt recordExpense(UUID actor, UUID vault, UUID key, Expense input) {
        try { return post(actor, vault, key, input); }
        catch (DataAccessException ex) {
            if (concurrent(ex)) throw fail("RESOURCE_CONCURRENCY_CONFLICT", HttpStatus.CONFLICT);
            throw ex;
        }
    }

    private CommandJournal.Receipt post(UUID actor, UUID vaultId, UUID key, Expense c) {
        // P1 bridge carries these approved optional inputs. Do not silently discard them
        // before the P3 atomic version/derivation/receipt persistence work is qualified.
        if (c.expectedVaultVersion() != null || c.splitDerivation() != null)
            throw fail("SHARED_EXPENSE_INVALID", HttpStatus.UNPROCESSABLE_ENTITY);
        start(actor);
        String op = "LEDGER_SHARED_EXPENSE:" + vaultId;
        CommandJournal.Claim claim = claim(actor, op, key, hash(Arrays.asList(vaultId, c.amount().stripTrailingZeros(),
                c.currency(), c.description(), c.expenseDate(), c.datePrecision(), c.funding(),
                c.payerMemberId(), c.sourceAccountId(), c.splits())));
        Map<String,Object> vault = vault(vaultId);
        Map<String,Object> member = member(vaultId, actor);
        if (!claim.fresh()) {
            if ("MEMBER".equals(c.funding())) personalReplay(actor, c.sourceAccountId());
            return claim.previous();
        }
        if (!"ACTIVE".equals(vault.get("status"))) throw fail("VAULT_ARCHIVED", HttpStatus.UNPROCESSABLE_ENTITY);
        if (!c.currency().equals(vault.get("currency"))) throw fail("CURRENCY_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
        for (var split : c.splits()) {
            List<Map<String,Object>> rows = jdbc.queryForList(
                    "SELECT id FROM vault_members WHERE id=? AND vault_id=? AND status='ACTIVE'",
                    split.memberId(), vaultId);
            if (rows.size() != 1) throw fail("EXPENSE_SPLIT_MEMBER_INVALID", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if ("MEMBER".equals(c.funding()) && !c.payerMemberId().equals(member.get("id")))
            throw fail("ACCESS_DENIED", HttpStatus.FORBIDDEN);
        history.require(vaultId, c.currency(), (BigDecimal)vault.get("current_balance"),
                ((Number)vault.get("version")).longValue());
        SharedOperationLedger.PreparedMemberExpense payer = "MEMBER".equals(c.funding())
                ? personal.prepareMemberExpense(actor, c.sourceAccountId(), c.currency(), c.amount()) : null;
        BigDecimal next = ((BigDecimal)vault.get("current_balance")).subtract(payer == null ? c.amount() : BigDecimal.ZERO);
        if (payer == null && next.signum() < 0)
            throw fail("INSUFFICIENT_BALANCE", HttpStatus.UNPROCESSABLE_ENTITY);
        if (next.abs().compareTo(MAX) > 0) throw fail("SHARED_EXPENSE_INVALID", HttpStatus.UNPROCESSABLE_ENTITY);
        Instant at = Instant.now();
        UUID tx = UUID.randomUUID(), expense = UUID.randomUUID();
        personal.recordExpense(new SharedOperationLedger.ExpenseOriginal(tx, actor, vaultId, c.amount(), c.currency(),
                c.expenseDate(), c.datePrecision(), c.description(), key, at), payer);
        Timestamp now = Timestamp.from(at);
        jdbc.update("""
                INSERT INTO shared_expenses(id,vault_id,transaction_id,paid_by_member_id,amount,description,
                                            expense_date,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)
                """, expense, vaultId, tx, payer == null ? null : c.payerMemberId(), c.amount(),
                c.description(), Timestamp.from(c.expenseDate()), now, now);
        for (var split : c.splits()) jdbc.update("""
                INSERT INTO expense_splits(id,shared_expense_id,member_id,amount,created_at) VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), expense, split.memberId(), split.amount(), now);
        long version = ((Number)vault.get("version")).longValue() + 1;
        jdbc.update("UPDATE shared_vaults SET current_balance=?,version=?,updated_at=? WHERE id=?",
                next, version, now, vaultId);
        if (payer == null) vaultEntry(tx, vaultId, c.amount().negate(), version, at);
        journal.appendSharedAudit(actor, "SHARED_EXPENSE_CREATED", "SHARED_EXPENSE", expense,
                tx, vaultId, null, at);
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("id", expense);
        body.put("vaultId", vaultId);
        body.put("transactionId", tx);
        body.put("amount", c.amount());
        body.put("currency", c.currency());
        body.put("description", c.description());
        body.put("expenseDate", c.expenseDate().toString());
        Map<String,Object> funding = new LinkedHashMap<>();
        funding.put("type", c.funding());
        if (payer != null) funding.put("sourceAccountId", payer.accountId());
        body.put("funding", funding);
        body.put("paidByMemberId", payer == null ? null : c.payerMemberId());
        body.put("splits", c.splits());
        body.put("status", "ACTIVE");
        CommandJournal.Receipt receipt = new CommandJournal.Receipt(201, Map.of(), write(body));
        journal.complete(actor, op, key, receipt, at);
        return receipt;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommandJournal.Receipt reverseContribution(UUID actor, UUID vault, UUID child, UUID key, String reason) {
        try { return reverse(actor, vault, child, key, reason, true); }
        catch (DataAccessException ex) {
            if (concurrent(ex)) throw fail("RESOURCE_CONCURRENCY_CONFLICT", HttpStatus.CONFLICT);
            throw ex;
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommandJournal.Receipt reverseExpense(UUID actor, UUID vault, UUID child, UUID key, String reason) {
        try { return reverse(actor, vault, child, key, reason, false); }
        catch (DataAccessException ex) {
            if (concurrent(ex)) throw fail("RESOURCE_CONCURRENCY_CONFLICT", HttpStatus.CONFLICT);
            throw ex;
        }
    }

    private CommandJournal.Receipt reverse(UUID actor, UUID vaultId, UUID child, UUID key,
                                           String reason, boolean contribution) {
        start(actor);
        String op = (contribution ? "LEDGER_REV_CONTRIBUTION:" : "LEDGER_REV_EXPENSE:") + vaultId + ":" + child;
        CommandJournal.Claim claim = claim(actor, op, key, hash(Arrays.asList(vaultId, child, reason)));
        Map<String,Object> vault = vault(vaultId);
        Map<String,Object> member = member(vaultId, actor);
        List<Map<String,Object>> children = jdbc.queryForList(contribution
                ? "SELECT transaction_id,amount,member_id FROM contributions WHERE id=? AND vault_id=?"
                : "SELECT transaction_id,amount,paid_by_member_id FROM shared_expenses WHERE id=? AND vault_id=?",
                child, vaultId);
        if (children.size() != 1) throw fail("TRANSACTION_NOT_FOUND", HttpStatus.NOT_FOUND);
        var row = children.get(0);
        UUID originalId = (UUID)row.get("transaction_id");
        String expected = contribution ? "CONTRIBUTION"
                : row.get("paid_by_member_id") == null ? "SHARED_VAULT" : "SHARED_MEMBER";
        SharedOperationLedger.LockedOriginal original = personal.lockOriginal(originalId, vaultId, expected);
        boolean owner = "OWNER".equals(member.get("role"));
        if (!actor.equals(original.actor()) && !owner) throw fail("ACCESS_DENIED", HttpStatus.FORBIDDEN);
        if (!claim.fresh()) return claim.previous();
        if (!"ACTIVE".equals(original.status()) || original.version() != 0)
            throw fail("TRANSACTION_ALREADY_REVERSED", HttpStatus.CONFLICT);
        if (((BigDecimal)row.get("amount")).compareTo(original.amount()) != 0) conflict();
        history.require(vaultId, (String)vault.get("currency"), (BigDecimal)vault.get("current_balance"),
                ((Number)vault.get("version")).longValue());
        var prepared = personal.prepareCompensation(original);
        BigDecimal originalVault = contribution ? original.amount()
                : "SHARED_VAULT".equals(expected) ? original.amount().negate() : BigDecimal.ZERO;
        BigDecimal next = ((BigDecimal)vault.get("current_balance")).subtract(originalVault);
        if (next.abs().compareTo(MAX) > 0) conflict();
        Instant at = Instant.now();
        UUID counter = personal.reverseOriginal(prepared, key, reason, at);
        long version = ((Number)vault.get("version")).longValue() + 1;
        jdbc.update("UPDATE shared_vaults SET current_balance=?,version=?,updated_at=? WHERE id=?",
                next, version, Timestamp.from(at), vaultId);
        if (originalVault.signum() != 0) vaultEntry(counter, vaultId, originalVault.negate(), version, at);
        journal.appendSharedAudit(actor, contribution ? "CONTRIBUTION_REVERSED" : "SHARED_EXPENSE_REVERSED",
                contribution ? "CONTRIBUTION" : "SHARED_EXPENSE", child, counter, vaultId, originalId, at);
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("id", counter);
        body.put("originalTransactionId", originalId);
        body.put("reversalTransactionId", counter);
        body.put("status", "REVERSED");
        body.put("reversedAt", at.toString());
        CommandJournal.Receipt receipt = new CommandJournal.Receipt(201, Map.of(), write(body));
        journal.complete(actor, op, key, receipt, at);
        return receipt;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommandJournal.Receipt archiveVault(UUID actor, UUID vaultId, UUID key) {
        try { return archive(actor, vaultId, key); }
        catch (DataAccessException ex) {
            if (concurrent(ex)) throw fail("RESOURCE_CONCURRENCY_CONFLICT", HttpStatus.CONFLICT);
            throw ex;
        }
    }

    private CommandJournal.Receipt archive(UUID actor, UUID vaultId, UUID key) {
        start(actor);
        String op = "LEDGER_ARCHIVE_VAULT:" + vaultId;
        CommandJournal.Claim claim = claim(actor, op, key, hash(List.of(vaultId)));
        Map<String,Object> vault = vault(vaultId);
        Map<String,Object> member = member(vaultId, actor);
        if (!"OWNER".equals(member.get("role"))) throw fail("ACCESS_DENIED", HttpStatus.FORBIDDEN);
        if (!claim.fresh()) return claim.previous();
        if (!"ACTIVE".equals(vault.get("status"))) throw fail("VAULT_ARCHIVED", HttpStatus.UNPROCESSABLE_ENTITY);
        if (((BigDecimal)vault.get("current_balance")).signum() != 0)
            throw fail("VAULT_BALANCE_NOT_ZERO", HttpStatus.UNPROCESSABLE_ENTITY);
        history.require(vaultId, (String)vault.get("currency"), BigDecimal.ZERO,
                ((Number)vault.get("version")).longValue());
        Instant at = Instant.now();
        long version = ((Number)vault.get("version")).longValue() + 1;
        jdbc.update("UPDATE shared_vaults SET status='ARCHIVED',version=?,updated_at=? WHERE id=?",
                version, Timestamp.from(at), vaultId);
        journal.appendSharedAudit(actor, "VAULT_ARCHIVED", "VAULT", vaultId, null, vaultId, null, at);
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("id", vaultId);
        body.put("name", vault.get("name"));
        body.put("currency", vault.get("currency"));
        body.put("balance", BigDecimal.ZERO);
        body.put("status", "ARCHIVED");
        body.put("version", version);
        CommandJournal.Receipt receipt = new CommandJournal.Receipt(200,
                Map.of("ETag", "\"" + version + "\""), write(body));
        journal.complete(actor, op, key, receipt, at);
        return receipt;
    }

    private void start(UUID actor) {
        jdbc.execute("SET LOCAL lock_timeout = '2s'");
        if (!subjects.isActive(actor)) throw fail("AUTHENTICATION_FAILED", HttpStatus.UNAUTHORIZED);
    }

    private CommandJournal.Claim claim(UUID actor, String op, UUID key, String hash) {
        try { return journal.claim(actor, op, key, hash); }
        catch (DataAccessException ex) {
            if (concurrent(ex)) throw fail("IDEMPOTENCY_IN_PROGRESS", HttpStatus.CONFLICT);
            throw ex;
        }
    }

    private Map<String,Object> vault(UUID id) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT id,name,RTRIM(currency) AS currency,current_balance,status,version
                FROM shared_vaults WHERE id=? FOR UPDATE
                """, id);
        if (rows.isEmpty()) throw fail("VAULT_NOT_FOUND", HttpStatus.NOT_FOUND);
        return rows.get(0);
    }

    private Map<String,Object> member(UUID vault, UUID actor) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT id,role,status FROM vault_members WHERE vault_id=? AND user_id=?
                """, vault, actor);
        if (rows.size() != 1 || !"ACTIVE".equals(rows.get(0).get("status")))
            throw fail("MEMBERSHIP_REQUIRED", HttpStatus.FORBIDDEN);
        return rows.get(0);
    }

    private void personalReplay(UUID actor, UUID accountId) {
        personal.authorizeReplay(actor, accountId);
    }

    private void vaultEntry(UUID tx, UUID vault, BigDecimal delta, long version, Instant at) {
        jdbc.update("""
                INSERT INTO vault_entries(id,transaction_id,vault_id,delta,recorded_at,resource_version)
                VALUES (?,?,?,?,?,?)
                """, UUID.randomUUID(), tx, vault, delta, Timestamp.from(at), version);
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(write(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private static boolean concurrent(Throwable ex) {
        for (Throwable cause=ex; cause!=null; cause=cause.getCause())
            if (cause instanceof SQLException sql && ("55P03".equals(sql.getSQLState())
                    || "40P01".equals(sql.getSQLState()))) return true;
        return false;
    }
    private static void conflict() { throw fail("RESOURCE_CONCURRENCY_CONFLICT", HttpStatus.CONFLICT); }
    private static BusinessApiException fail(String code, HttpStatus status) {
        return new BusinessApiException(code, code, status);
    }
}
