package com.savemon.personalfinance.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PersonalFinanceTypesTest {

    @Test
    void exposesTheApprovedAccountAndTransactionTypeValues() {
        assertArrayEquals(
                new AccountType[]{AccountType.CASH, AccountType.BANK, AccountType.EWALLET, AccountType.OTHER},
                AccountType.values());
        assertArrayEquals(
                new TransactionType[]{TransactionType.INCOME, TransactionType.EXPENSE},
                TransactionType.values());
        assertArrayEquals(
                new TransactionStatus[]{TransactionStatus.ACTIVE, TransactionStatus.REVERSED},
                TransactionStatus.values());
        assertArrayEquals(
                new CategoryType[]{CategoryType.INCOME, CategoryType.EXPENSE},
                CategoryType.values());
    }
}
