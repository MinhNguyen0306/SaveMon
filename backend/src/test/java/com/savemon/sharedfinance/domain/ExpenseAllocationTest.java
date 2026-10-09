package com.savemon.sharedfinance.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpenseAllocationTest {

    private static final UUID FIRST_MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void acceptsExactAllocationRegardlessOfDecimalScale() {
        ExpenseAllocation allocation = new ExpenseAllocation(
                new BigDecimal("10.0"),
                List.of(
                        new ExpenseSplit(FIRST_MEMBER, new BigDecimal("4.00")),
                        new ExpenseSplit(SECOND_MEMBER, new BigDecimal("6"))
                )
        );

        assertEquals(2, allocation.splits().size());
    }

    @Test
    void rejectsAllocationWhoseTotalDiffersFromExpenseAmount() {
        assertThrows(IllegalArgumentException.class, () -> new ExpenseAllocation(
                new BigDecimal("10"),
                List.of(new ExpenseSplit(FIRST_MEMBER, new BigDecimal("9.99")))
        ));
    }

    @Test
    void rejectsDuplicateMemberAllocations() {
        assertThrows(IllegalArgumentException.class, () -> new ExpenseAllocation(
                new BigDecimal("10"),
                List.of(
                        new ExpenseSplit(FIRST_MEMBER, new BigDecimal("4")),
                        new ExpenseSplit(FIRST_MEMBER, new BigDecimal("6"))
                )
        ));
    }

    @Test
    void rejectsNonPositiveExpenseAndSplitAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new ExpenseAllocation(
                BigDecimal.ZERO,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new ExpenseSplit(FIRST_MEMBER, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ExpenseSplit(FIRST_MEMBER, new BigDecimal("-1")));
    }

    @Test
    void copiesSplitListToKeepAllocationImmutable() {
        var splits = new java.util.ArrayList<>(List.of(
                new ExpenseSplit(FIRST_MEMBER, new BigDecimal("10"))
        ));
        ExpenseAllocation allocation = new ExpenseAllocation(new BigDecimal("10"), splits);

        splits.clear();

        assertEquals(1, allocation.splits().size());
    }
}
