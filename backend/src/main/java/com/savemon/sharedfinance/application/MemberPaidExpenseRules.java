package com.savemon.sharedfinance.application;

import com.savemon.personalfinance.application.DebitOwnedAccount;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Shared Finance integration boundary; callers execute it under their DB transaction. */
public final class MemberPaidExpenseRules {
    private final DebitOwnedAccount debitOwnedAccount;
    public MemberPaidExpenseRules(DebitOwnedAccount debitOwnedAccount) {
        this.debitOwnedAccount = Objects.requireNonNull(debitOwnedAccount);
    }
    public void debitPayer(UUID authenticatedUserId, UUID payerMemberUserId,
                           UUID sourceAccountId, BigDecimal amount,
                           String accountCurrency, String vaultCurrency) {
        if (!Objects.equals(authenticatedUserId, payerMemberUserId)) {
            throw new IllegalStateException("Payer member must match authenticated caller");
        }
        if (!Objects.equals(accountCurrency, vaultCurrency)) {
            throw new IllegalArgumentException("Source account currency must match vault currency");
        }
        debitOwnedAccount.debit(authenticatedUserId, sourceAccountId, amount, vaultCurrency);
    }
}
