package com.savemon.personalfinance.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Balance operations distinguish normal debits from explicit reversal corrections. */
public record AccountBalance(BigDecimal value) {
    public AccountBalance {
        Objects.requireNonNull(value, "value must not be null");
    }
    public AccountBalance debit(PositiveAmount amount) {
        BigDecimal next = value.subtract(amount.value());
        if (next.signum() < 0) throw new InsufficientBalanceException();
        return new AccountBalance(next);
    }
    public AccountBalance credit(PositiveAmount amount) { return new AccountBalance(value.add(amount.value())); }
    /** Used only for the compensating reversal operation; may produce a negative balance. */
    public AccountBalance applyReversal(BigDecimal signedBalanceDelta) {
        return new AccountBalance(value.add(Objects.requireNonNull(signedBalanceDelta)));
    }
    public static final class InsufficientBalanceException extends RuntimeException { }
}
