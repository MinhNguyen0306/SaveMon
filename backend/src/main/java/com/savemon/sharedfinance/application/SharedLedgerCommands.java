package com.savemon.sharedfinance.application;

import com.savemon.shared.interfaces.exception.BusinessApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Isolated shared-ledger commands; deliberately not wired to legacy HTTP routes. */
@Service
public final class SharedLedgerCommands {
    private static final BigDecimal MAX = new BigDecimal("999999999999999.9999");
    private final Store store;

    public SharedLedgerCommands(Store store) { this.store = store; }

    public CommandJournal.Receipt recordExpense(UUID actor, UUID vaultId, UUID key, Expense input) {
        if (actor == null || vaultId == null || key == null || input == null
                || input.amount() == null || input.currency() == null || input.expenseDate() == null
                || input.funding() == null || input.splits() == null || input.splits().isEmpty()) invalid();
        requireMoney(input.amount(), input.currency());
        if (input.description() != null && input.description().length() > 500) invalid();
        if (input.expectedVaultVersion() != null && input.expectedVaultVersion() < 0) invalid();
        String precision = input.datePrecision() == null ? "INSTANT" : input.datePrecision();
        if (!List.of("INSTANT", "DATE_ONLY").contains(precision)) invalid();
        Instant at;
        if ("DATE_ONLY".equals(precision)) {
            LocalDate date = LocalDate.ofInstant(input.expenseDate(), ZoneOffset.UTC);
            if (date.isAfter(LocalDate.now(ZoneOffset.UTC))) invalid();
            at = date.atTime(12, 0).toInstant(ZoneOffset.UTC);
        } else {
            if (input.expenseDate().isAfter(Instant.now())) invalid();
            at = input.expenseDate();
        }
        boolean member = "MEMBER".equals(input.funding());
        if (!member && !"VAULT".equals(input.funding())) invalid();
        if (member && (input.payerMemberId() == null || input.sourceAccountId() == null)) invalid();
        if (!member && (input.payerMemberId() != null || input.sourceAccountId() != null)) invalid();
        Set<UUID> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Split split : input.splits()) {
            if (split == null || split.memberId() == null || !seen.add(split.memberId())) invalid();
            requireMoney(split.amount(), input.currency());
            sum = sum.add(split.amount());
        }
        if (sum.compareTo(input.amount()) != 0) invalid();
        List<Split> ordered = input.splits().stream()
                .sorted(java.util.Comparator.comparing(s -> s.memberId().toString())).toList();
        return store.recordExpense(actor, vaultId, key,
                new Expense(input.amount(), input.currency(), input.description(), at, precision,
                        input.funding(), input.payerMemberId(), input.sourceAccountId(), ordered,
                        input.splitDerivation(), input.expectedVaultVersion()));
    }

    public CommandJournal.Receipt reverseContribution(UUID actor, UUID vaultId, UUID contributionId,
                                                       UUID key, String reason) {
        requireReverse(actor, vaultId, contributionId, key, reason);
        return store.reverseContribution(actor, vaultId, contributionId, key, reason);
    }

    public CommandJournal.Receipt reverseExpense(UUID actor, UUID vaultId, UUID expenseId,
                                                  UUID key, String reason) {
        requireReverse(actor, vaultId, expenseId, key, reason);
        return store.reverseExpense(actor, vaultId, expenseId, key, reason);
    }

    public CommandJournal.Receipt archiveVault(UUID actor, UUID vaultId, UUID key) {
        if (actor == null || vaultId == null || key == null) invalid();
        return store.archiveVault(actor, vaultId, key);
    }

    private static void requireReverse(UUID actor, UUID vault, UUID child, UUID key, String reason) {
        if (actor == null || vault == null || child == null || key == null
                || (reason != null && reason.length() > 500)) invalid();
    }

    private static void requireMoney(BigDecimal amount, String currency) {
        int scale = "VND".equals(currency) ? 0 : "USD".equals(currency) ? 2 : -1;
        if (amount == null || scale < 0 || amount.signum() <= 0 || amount.compareTo(MAX) > 0
                || amount.stripTrailingZeros().scale() > scale) invalid();
    }

    private static void invalid() {
        throw new BusinessApiException("SHARED_EXPENSE_INVALID", "Invalid shared ledger command.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record Split(UUID memberId, BigDecimal amount) { }
    public record Expense(BigDecimal amount, String currency, String description, Instant expenseDate,
                          String datePrecision, String funding, UUID payerMemberId,
                          UUID sourceAccountId, List<Split> splits,
                          SplitPreviewService.Derivation splitDerivation, Long expectedVaultVersion) {
        public Expense(BigDecimal amount, String currency, String description, Instant expenseDate,
                       String datePrecision, String funding, UUID payerMemberId,
                       UUID sourceAccountId, List<Split> splits) {
            this(amount, currency, description, expenseDate, datePrecision, funding,
                    payerMemberId, sourceAccountId, splits, null, null);
        }
    }
    public interface Store {
        CommandJournal.Receipt recordExpense(UUID actor, UUID vaultId, UUID key, Expense input);
        CommandJournal.Receipt reverseContribution(UUID actor, UUID vaultId, UUID childId, UUID key, String reason);
        CommandJournal.Receipt reverseExpense(UUID actor, UUID vaultId, UUID childId, UUID key, String reason);
        CommandJournal.Receipt archiveVault(UUID actor, UUID vaultId, UUID key);
    }
}
