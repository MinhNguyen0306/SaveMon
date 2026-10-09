package com.savemon.personalfinance.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Public application command for same-transaction shared-finance debits. */
public interface DebitOwnedAccount {
    void debit(UUID authenticatedUserId, UUID accountId, BigDecimal amount, String currency);
}
