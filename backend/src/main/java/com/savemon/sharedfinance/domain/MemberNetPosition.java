package com.savemon.sharedfinance.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Positive position means the member has a credit; settlements are not included in MVP. */
public record MemberNetPosition(UUID memberId, BigDecimal contributed, BigDecimal paidOnBehalf,
                                BigDecimal allocatedExpenses, BigDecimal netPosition) {
    public MemberNetPosition(UUID memberId, BigDecimal contributed, BigDecimal paidOnBehalf,
                             BigDecimal allocatedExpenses) {
        this(Objects.requireNonNull(memberId), nonNull(contributed), nonNull(paidOnBehalf),
                nonNull(allocatedExpenses), contributed.add(paidOnBehalf).subtract(allocatedExpenses));
    }
    private static BigDecimal nonNull(BigDecimal value) { return Objects.requireNonNull(value); }
}
