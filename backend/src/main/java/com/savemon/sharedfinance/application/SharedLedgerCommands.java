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

/** Isolated expense-only validation port; deliberately not wired to legacy HTTP routes. */
public final class SharedLedgerCommands {
    private static final BigDecimal MAX = new BigDecimal("999999999999999.9999");
    private final Store store;

    public SharedLedgerCommands(Store store) { this.store = store; }

    public Receipt recordExpense(UUID actor, UUID vaultId, UUID key, Expense input) {
        if (actor == null || vaultId == null || key == null || input == null
                || input.amount() == null || input.currency() == null || input.expenseDate() == null
                || input.funding() == null || input.splits() == null || input.splits().isEmpty()) invalid();
        requireMoney(input.amount(), input.currency());
        if (input.description() != null && input.description().length() > 500) invalid();
        if (input.expectedVaultVersion() != null && input.expectedVaultVersion() < 0) invalid();
        validateDerivation(input.splitDerivation(), input.amount(), input.currency());
        String precision = input.datePrecision() == null ? "INSTANT" : input.datePrecision();
        if (!List.of("INSTANT", "DATE_ONLY").contains(precision)) invalid();
        Instant at;
        if ("DATE_ONLY".equals(precision)) {
            LocalDate date = LocalDate.ofInstant(input.expenseDate(), ZoneOffset.UTC);
            if (date.isAfter(LocalDate.now(ZoneOffset.UTC))) futureDate();
            at = date.atTime(12, 0).toInstant(ZoneOffset.UTC);
        } else {
            if (input.expenseDate().isAfter(Instant.now())) futureDate();
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
        if (sum.compareTo(input.amount()) != 0)
            throw new BusinessApiException("EXPENSE_SPLIT_TOTAL_MISMATCH",
                    "Split total does not match expense amount.", HttpStatus.UNPROCESSABLE_ENTITY);
        List<Split> ordered = input.splits().stream()
                .sorted(java.util.Comparator.comparing(s -> s.memberId().toString())).toList();
        return store.recordExpense(actor, vaultId, key,
                new Expense(input.amount(), input.currency(), input.description(), at, precision,
                        input.funding(), input.payerMemberId(), input.sourceAccountId(), ordered,
                        input.splitDerivation(), input.expectedVaultVersion()));
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

    private static void futureDate() {
        throw new BusinessApiException("FUTURE_DATE_NOT_ALLOWED", "Future date is not allowed.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static void invalidSplit() {
        throw new BusinessApiException("SPLIT_INPUT_INVALID", "Invalid split derivation input.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static void malformedSplit() {
        throw new BusinessApiException("VALIDATION_ERROR", "Malformed split derivation input.",
                HttpStatus.BAD_REQUEST);
    }

    private static void validateDerivation(Derivation derivation, BigDecimal amount, String currency) {
        if (derivation == null) return;
        if (!"largest-remainder-v1".equals(derivation.algorithmVersion())
                || derivation.input() == null || derivation.mode() == null
                || !List.of("EQUAL", "SHARES", "PERCENT", "ITEMIZED").contains(derivation.mode())
                || !derivation.mode().equals(derivation.input().mode())
                || !currency.equals(derivation.input().currency())
                || derivation.input().amount() == null
                || derivation.input().amount().compareTo(amount) != 0) invalidSplit();
        SplitRequest input = derivation.input();
        if ("ITEMIZED".equals(derivation.mode())) {
            if (input.participants() != null && !input.participants().isEmpty()) invalidSplit();
            if (input.lines() == null || input.lines().isEmpty() || input.lines().size() > 200)
                invalidSplit();
            BigDecimal lineTotal = BigDecimal.ZERO;
            for (Line line : input.lines()) {
                if (line.amount() == null || line.amount().signum() <= 0) invalidSplit();
                requireParticipants(line.participants(), "SHARES");
                lineTotal = lineTotal.add(line.amount());
            }
            if (lineTotal.compareTo(amount) != 0)
                throw new BusinessApiException("EXPENSE_SPLIT_TOTAL_MISMATCH",
                        "Split line total does not match expense amount.", HttpStatus.UNPROCESSABLE_ENTITY);
        } else {
            if (input.lines() != null && !input.lines().isEmpty()) invalidSplit();
            requireParticipants(input.participants(), derivation.mode());
        }
        // Exact allocation recomputation and membership checks belong to the later locked store.
    }

    private static void requireParticipants(List<Participant> participants, String mode) {
        if (participants == null || participants.isEmpty() || participants.size() > 100) invalidSplit();
        Set<UUID> members = new HashSet<>();
        int basisTotal = 0;
        for (Participant participant : participants) {
            if (participant == null || participant.memberId() == null || !members.add(participant.memberId()))
                invalidSplit();
            switch (mode) {
                case "EQUAL" -> {
                    if (participant.weight() != null || participant.basisPoints() != null) invalidSplit();
                }
                case "SHARES" -> {
                    if (participant.basisPoints() != null || participant.weight() == null
                            || participant.weight() < 1 || participant.weight() > 1_000_000) invalidSplit();
                }
                case "PERCENT" -> {
                    if (participant.weight() != null || participant.basisPoints() == null
                            || participant.basisPoints() < 1 || participant.basisPoints() > 10_000) invalidSplit();
                    basisTotal += participant.basisPoints();
                }
                default -> invalidSplit();
            }
        }
        if ("PERCENT".equals(mode) && basisTotal != 10_000) invalidSplit();
    }

    public record Split(UUID memberId, BigDecimal amount) { }
    public record Expense(BigDecimal amount, String currency, String description, Instant expenseDate,
                          String datePrecision, String funding, UUID payerMemberId,
                          UUID sourceAccountId, List<Split> splits,
                          Derivation splitDerivation, Long expectedVaultVersion) {
        public Expense(BigDecimal amount, String currency, String description, Instant expenseDate,
                       String datePrecision, String funding, UUID payerMemberId,
                       UUID sourceAccountId, List<Split> splits) {
            this(amount, currency, description, expenseDate, datePrecision, funding,
                    payerMemberId, sourceAccountId, splits, null, null);
        }
    }
    public record Participant(UUID memberId, Integer weight, Integer basisPoints) { }
    public record Line(UUID lineId, BigDecimal amount, List<Participant> participants) {
        public Line {
            if (lineId == null || participants == null
                    || participants.stream().anyMatch(p -> p == null || p.memberId() == null)) malformedSplit();
            participants = List.copyOf(participants);
        }
    }
    public record SplitRequest(BigDecimal amount, String currency, String mode,
                               List<Participant> participants, List<Line> lines) {
        public SplitRequest {
            if (participants != null) {
                if (participants.stream().anyMatch(p -> p == null || p.memberId() == null)) malformedSplit();
                participants = List.copyOf(participants);
            }
            if (lines != null) {
                if (lines.stream().anyMatch(line -> line == null || line.lineId() == null)) malformedSplit();
                lines = List.copyOf(lines);
            }
        }
    }
    public record Derivation(String algorithmVersion, String mode, SplitRequest input) { }
    public record Receipt(int status, java.util.Map<String, String> headers, String body) { }
    public interface Store {
        Receipt recordExpense(UUID actor, UUID vaultId, UUID key, Expense input);
    }
}
