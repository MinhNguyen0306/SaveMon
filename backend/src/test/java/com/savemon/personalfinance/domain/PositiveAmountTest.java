package com.savemon.personalfinance.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositiveAmountTest {

    @Test
    void storesPositiveAmountAtDatabaseScaleWithoutRounding() {
        PositiveAmount amount = new PositiveAmount(new BigDecimal("125.3"));

        assertEquals(new BigDecimal("125.3000"), amount.value());
    }

    @Test
    void acceptsExtraTrailingZeroesWhenValueIsExactlyRepresentable() {
        PositiveAmount amount = new PositiveAmount(new BigDecimal("1.23000"));

        assertEquals(new BigDecimal("1.2300"), amount.value());
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class, () -> new PositiveAmount(null));
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new PositiveAmount(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PositiveAmount(new BigDecimal("-0.01")));
    }

    @Test
    void rejectsValuesThatWouldRequireRounding() {
        assertThrows(IllegalArgumentException.class, () -> new PositiveAmount(new BigDecimal("1.00001")));
    }

    @Test
    void rejectsValuesOutsideNumericNineteenFour() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PositiveAmount(new BigDecimal("1000000000000000.0000")));
    }
}
