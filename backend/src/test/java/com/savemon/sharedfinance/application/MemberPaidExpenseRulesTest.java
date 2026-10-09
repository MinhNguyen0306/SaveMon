package com.savemon.sharedfinance.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.savemon.personalfinance.application.DebitOwnedAccount;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberPaidExpenseRulesTest {
    @Test
    void delegatesAnAuthorizedSameCurrencyDebitThroughTheApplicationPort() {
        DebitOwnedAccount debit = mock(DebitOwnedAccount.class);
        MemberPaidExpenseRules rules = new MemberPaidExpenseRules(debit);
        UUID user = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("25.00");

        rules.debitPayer(user, user, account, amount, "VND", "VND");

        verify(debit).debit(user, account, amount, "VND");
    }

    @Test
    void rejectsAnUnauthorizedPayerBeforeCallingThePort() {
        DebitOwnedAccount debit = mock(DebitOwnedAccount.class);
        MemberPaidExpenseRules rules = new MemberPaidExpenseRules(debit);

        assertThatThrownBy(() -> rules.debitPayer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, "VND", "VND")).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(debit);
    }

    @Test
    void rejectsCurrencyMismatchBeforeCallingThePort() {
        DebitOwnedAccount debit = mock(DebitOwnedAccount.class);
        MemberPaidExpenseRules rules = new MemberPaidExpenseRules(debit);
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> rules.debitPayer(user, user, UUID.randomUUID(),
                BigDecimal.TEN, "USD", "VND")).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(debit);
    }
}
