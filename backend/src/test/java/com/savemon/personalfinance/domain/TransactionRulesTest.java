package com.savemon.personalfinance.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TransactionRulesTest {
    @Test void permitsUnitemizedOrExactlyItemizedTransactions() {
        var total = new PositiveAmount(new BigDecimal("10"));
        TransactionRules.validateItems(total, List.of());
        TransactionRules.validateItems(total, List.of(
                new TransactionRules.ItemAmount(UUID.randomUUID(), new PositiveAmount(new BigDecimal("4"))),
                new TransactionRules.ItemAmount(UUID.randomUUID(), new PositiveAmount(new BigDecimal("6")))));
    }
    @Test void rejectsItemTotalMismatchAndCurrencyMismatch() {
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.validateItems(
                new PositiveAmount(new BigDecimal("10")), List.of(
                        new TransactionRules.ItemAmount(UUID.randomUUID(), new PositiveAmount(new BigDecimal("9"))))));
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.requireSameCurrency("USD", "VND"));
    }
    @Test void preventsNormalNegativeBalanceButAllowsExplicitReversalAdjustment() {
        var balance = new AccountBalance(new BigDecimal("2"));
        assertThrows(AccountBalance.InsufficientBalanceException.class,
                () -> balance.debit(new PositiveAmount(new BigDecimal("3"))));
        assertEquals(new BigDecimal("-1"), balance.applyReversal(new BigDecimal("-3")).value());
    }
    @Test void onlyActiveTransactionsAreEditable() {
        TransactionRules.requireEditable(TransactionStatus.ACTIVE);
        assertThrows(IllegalStateException.class, () -> TransactionRules.requireEditable(TransactionStatus.REVERSED));
    }
}
