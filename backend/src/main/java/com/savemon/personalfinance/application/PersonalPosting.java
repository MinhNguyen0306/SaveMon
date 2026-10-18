package com.savemon.personalfinance.application;

import com.savemon.shared.interfaces.exception.BusinessApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Isolated, not-yet-HTTP-wired ordinary personal posting command. */
public final class PersonalPosting {
    private static final BigDecimal MAX = new BigDecimal("999999999999999.9999");
    private final Store store;

    public PersonalPosting(Store store) { this.store = store; }

    public Receipt record(UUID subject, String type, UUID key, Command command) {
        if (subject == null || key == null || command == null || command.accountId() == null
                || command.transactionDate() == null || command.currency() == null
                || !List.of("INCOME", "EXPENSE").contains(type)) bad("Invalid personal posting command.");
        String currency = command.currency();
        int minor = switch (currency) { case "VND" -> 0; case "USD" -> 2; default -> -1; };
        if (minor < 0) bad("Unsupported currency.");
        amount(command.amount(), minor);
        if (command.description() != null && command.description().length() > 500) bad("Description is too long.");
        String precision = command.datePrecision() == null ? "INSTANT" : command.datePrecision();
        if (!List.of("INSTANT", "DATE_ONLY").contains(precision)) bad("Invalid date precision.");
        if (precision.equals("INSTANT") && command.transactionDate().isAfter(Instant.now()))
            throw new BusinessApiException("FUTURE_DATE_NOT_ALLOWED", "Future instant is not allowed.", HttpStatus.UNPROCESSABLE_ENTITY);
        if (precision.equals("DATE_ONLY") && LocalDate.ofInstant(command.transactionDate(), ZoneOffset.UTC)
                .isAfter(LocalDate.now(ZoneOffset.UTC)))
            throw new BusinessApiException("FUTURE_DATE_NOT_ALLOWED", "Future date is not allowed.", HttpStatus.UNPROCESSABLE_ENTITY);
        if (command.items() != null && command.items().stream().anyMatch(item ->
                item == null || item.categoryId() == null)) bad("Category is required.");
        List<Item> items = command.items() == null ? List.of() : List.copyOf(command.items());
        BigDecimal sum = BigDecimal.ZERO;
        for (Item item : items) {
            if (item == null || item.categoryId() == null) bad("Category is required.");
            amount(item.amount(), minor);
            if (item.description() != null && item.description().length() > 500) bad("Item description is too long.");
            sum = sum.add(item.amount());
        }
        if (!items.isEmpty() && sum.compareTo(command.amount()) != 0)
            throw new BusinessApiException("TRANSACTION_ITEM_TOTAL_MISMATCH",
                    "Item sum must equal transaction amount.", HttpStatus.UNPROCESSABLE_ENTITY);
        Instant effectiveAt = precision.equals("DATE_ONLY")
                ? LocalDate.ofInstant(command.transactionDate(), ZoneOffset.UTC).atTime(12, 0).toInstant(ZoneOffset.UTC)
                : command.transactionDate();
        return store.record(subject, type, key, new Command(command.accountId(), command.amount(), currency,
                effectiveAt, precision, command.description(), items, command.narrativeSource()));
    }

    private static void amount(BigDecimal value, int minor) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX) > 0
                || value.stripTrailingZeros().scale() > minor)
            throw new BusinessApiException("INVALID_TRANSACTION_AMOUNT", "Invalid currency amount.", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static void bad(String message) {
        throw new BusinessApiException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public record Command(UUID accountId, BigDecimal amount, String currency, Instant transactionDate,
                          String datePrecision, String description, List<Item> items, NarrativeSource narrativeSource) {
        public Command(UUID accountId, BigDecimal amount, String currency, Instant transactionDate,
                       String datePrecision, String description, List<Item> items) {
            this(accountId, amount, currency, transactionDate, datePrecision, description, items, null);
        }
    }
    public record NarrativeSource(UUID narrativeId, UUID itemId, long reviewedVersion) { }
    public record Item(UUID categoryId, BigDecimal amount, String description) { }
    public record Receipt(int status, String etag, String body) { }
    public interface Store { Receipt record(UUID subject, String type, UUID key, Command command); }
}
