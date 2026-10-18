package com.savemon.sharedfinance.interfaces;

import com.savemon.shared.interfaces.exception.BusinessApiException;
import com.savemon.sharedfinance.application.CommandJournal;
import com.savemon.sharedfinance.application.SharedLedgerCommands;
import com.savemon.sharedfinance.application.SplitPreviewService;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Unregistered ordinary shared-expense DTO/core bridge; never invoked by AI extraction. */
public final class SharedExpensePostingBridge {
    private final SharedLedgerCommands commands;

    public SharedExpensePostingBridge(SharedLedgerCommands commands) { this.commands = commands; }

    public CommandJournal.Receipt record(UUID actor, UUID vaultId, UUID key,
                                         String ifMatch, ExpenseCreate request) {
        if (request == null)
            throw new BusinessApiException("VALIDATION_ERROR", "Expense request is required.", HttpStatus.BAD_REQUEST);
        Long expectedVersion = expectedVersion(ifMatch);
        Funding funding = request.funding();
        List<SharedLedgerCommands.Split> splits = request.splits() == null ? null : request.splits().stream()
                .map(split -> split == null ? null
                        : new SharedLedgerCommands.Split(split.memberId(), split.amount())).toList();
        return commands.recordExpense(actor, vaultId, key, new SharedLedgerCommands.Expense(
                request.amount(), request.currency(), request.description(), request.expenseDate(),
                request.datePrecision(), funding == null ? null : funding.type(),
                funding == null ? null : funding.payerMemberId(),
                funding == null ? null : funding.sourceAccountId(),
                splits, request.splitDerivation(), expectedVersion));
    }

    private Long expectedVersion(String header) {
        if (header == null) return null;
        if (!header.matches("\"(0|[1-9][0-9]*)\""))
            throw new BusinessApiException("VALIDATION_ERROR", "Invalid If-Match version.", HttpStatus.BAD_REQUEST);
        try { return Long.parseLong(header.substring(1, header.length() - 1)); }
        catch (NumberFormatException exception) {
            throw new BusinessApiException("VALIDATION_ERROR", "Invalid If-Match version.", HttpStatus.BAD_REQUEST);
        }
    }

    public record ExpenseCreate(BigDecimal amount, String currency, String description,
                                Instant expenseDate, String datePrecision, Funding funding,
                                List<Split> splits, SplitPreviewService.Derivation splitDerivation) { }
    public record Funding(String type, UUID payerMemberId, UUID sourceAccountId) { }
    public record Split(UUID memberId, BigDecimal amount) { }
}
