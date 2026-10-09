package com.savemon.sharedfinance.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberNetPositionTest {
    @Test void computesContributionPlusPaidOnBehalfMinusAllocatedAndPositiveMeansCredit() {
        var result = new MemberNetPosition(UUID.randomUUID(), new BigDecimal("100"),
                new BigDecimal("40"), new BigDecimal("30"));
        assertEquals(new BigDecimal("110"), result.netPosition());
    }
}
