package com.savemon.personalfinance.interfaces;

import com.savemon.personalfinance.application.PersonalPosting;
import com.savemon.shared.interfaces.exception.BusinessApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Unregistered ordinary-expense DTO/core bridge; not a scan financial writer. */
public final class PersonalExpensePostingBridge {
    private final PersonalPosting posting;

    public PersonalExpensePostingBridge(PersonalPosting posting) { this.posting = posting; }

    public PersonalPosting.Receipt record(UUID subject, UUID key, TransactionCreate request) {
        if (request == null)
            throw new BusinessApiException("VALIDATION_ERROR", "Expense request is required.", HttpStatus.BAD_REQUEST);
        List<PersonalPosting.Item> items = request.items() == null ? null : request.items().stream()
                .map(item -> item == null ? null
                        : new PersonalPosting.Item(item.categoryId(), item.amount(), item.description()))
                .toList();
        PersonalPosting.NarrativeSource narrative = request.narrativeSource() == null ? null
                : new PersonalPosting.NarrativeSource(request.narrativeSource().narrativeId(),
                request.narrativeSource().itemId(), request.narrativeSource().reviewedVersion());
        return posting.record(subject, "EXPENSE", key, new PersonalPosting.Command(
                request.accountId(), request.amount(), request.currency(), request.transactionDate(),
                request.datePrecision(), request.description(), items, narrative));
    }

    public record TransactionCreate(UUID accountId, BigDecimal amount, String currency,
                                    Instant transactionDate, String datePrecision,
                                    String description, List<Item> items,
                                    NarrativeSource narrativeSource) { }
    public record Item(UUID categoryId, BigDecimal amount, String description) { }
    public record NarrativeSource(UUID narrativeId, UUID itemId, long reviewedVersion) { }
}
