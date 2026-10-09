package com.savemon.personalfinance.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic validation shared by transaction application commands. */
public final class TransactionRules {
    private TransactionRules() { }

    public static void validateItems(PositiveAmount amount, List<ItemAmount> items) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(items, "items must not be null");
        BigDecimal sum = BigDecimal.ZERO;
        for (ItemAmount item : items) {
            Objects.requireNonNull(item, "item must not be null");
            sum = sum.add(item.amount().value());
        }
        if (!items.isEmpty() && sum.compareTo(amount.value()) != 0) {
            throw new IllegalArgumentException("Transaction item total must equal transaction amount");
        }
    }

    public static void requireEditable(TransactionStatus status) {
        if (status != TransactionStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE transactions may be edited");
        }
    }

    public static void requireSameCurrency(String transactionCurrency, String accountCurrency) {
        if (!Objects.requireNonNull(transactionCurrency).equals(Objects.requireNonNull(accountCurrency))) {
            throw new IllegalArgumentException("Transaction currency must match account currency");
        }
    }

    public record ItemAmount(UUID categoryId, PositiveAmount amount) {
        public ItemAmount {
            Objects.requireNonNull(categoryId, "categoryId must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
        }
    }
}
