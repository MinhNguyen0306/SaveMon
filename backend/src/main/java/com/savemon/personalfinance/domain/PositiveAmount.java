package com.savemon.personalfinance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A positive financial amount stored using the database's NUMERIC(19,4) precision.
 *
 * <p>This deliberately models transaction and item amounts, not account balances:
 * account balance direction rules are not defined per account type.</p>
 */
public record PositiveAmount(BigDecimal value) {

    private static final int SCALE = 4;
    private static final int PRECISION = 19;

    public PositiveAmount {
        Objects.requireNonNull(value, "value must not be null");
        try {
            value = value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("value must be representable with scale 4", exception);
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }
        if (value.precision() > PRECISION) {
            throw new IllegalArgumentException("value exceeds NUMERIC(19,4) precision");
        }
    }
}
