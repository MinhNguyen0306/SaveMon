package com.savemon.sharedfinance.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A complete allocation of an expense to distinct vault members.
 *
 * This value object deliberately does not decide membership eligibility or
 * expense funding; those require application and accounting rules beyond the
 * allocation invariant.
 */
public record ExpenseAllocation(BigDecimal expenseAmount, List<ExpenseSplit> splits) {

    public ExpenseAllocation {
        Objects.requireNonNull(expenseAmount, "expenseAmount must not be null");
        Objects.requireNonNull(splits, "splits must not be null");

        if (expenseAmount.signum() <= 0) {
            throw new IllegalArgumentException("Expense amount must be positive");
        }

        splits = List.copyOf(splits);
        Set<UUID> memberIds = new HashSet<>();
        BigDecimal splitTotal = BigDecimal.ZERO;
        for (ExpenseSplit split : splits) {
            Objects.requireNonNull(split, "splits must not contain null entries");
            if (!memberIds.add(split.memberId())) {
                throw new IllegalArgumentException("A member may only appear once in an expense allocation");
            }
            splitTotal = splitTotal.add(split.amount());
        }

        if (splitTotal.compareTo(expenseAmount) != 0) {
            throw new IllegalArgumentException("Split amounts must equal the expense amount");
        }
    }
}
