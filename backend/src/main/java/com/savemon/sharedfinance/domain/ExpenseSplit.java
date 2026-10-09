package com.savemon.sharedfinance.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * The portion of a shared expense allocated to one vault member.
 */
public record ExpenseSplit(UUID memberId, BigDecimal amount) {

    public ExpenseSplit {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Split amount must be positive");
        }
    }
}
