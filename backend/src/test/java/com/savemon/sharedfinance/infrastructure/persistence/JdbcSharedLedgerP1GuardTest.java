package com.savemon.sharedfinance.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.identity.application.SubjectStatusQuery;
import com.savemon.personalfinance.application.SharedOperationLedger;
import com.savemon.shared.interfaces.exception.BusinessApiException;
import com.savemon.sharedfinance.application.CommandJournal;
import com.savemon.sharedfinance.application.SharedLedgerCommands;
import com.savemon.sharedfinance.application.SplitPreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JdbcSharedLedgerP1GuardTest {
    @Test void optionalVersionAndDerivationCannotBeSilentlyDroppedByUnqualifiedStore() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SharedOperationLedger personal = mock(SharedOperationLedger.class);
        SubjectStatusQuery subjects = mock(SubjectStatusQuery.class);
        CommandJournal journal = mock(CommandJournal.class);
        SharedLedgerHistory history = mock(SharedLedgerHistory.class);
        var store = new JdbcSharedLedgerStore(jdbc, new ObjectMapper(), personal, subjects, journal, history);
        UUID member = UUID.randomUUID();
        var split = List.of(new SharedLedgerCommands.Split(member, new BigDecimal("100")));
        var versioned = new SharedLedgerCommands.Expense(new BigDecimal("100"), "VND", null,
                Instant.parse("2020-01-01T12:00:00Z"), "DATE_ONLY", "VAULT", null, null,
                split, null, 5L);
        assertThatThrownBy(() -> store.recordExpense(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), versioned))
                .isInstanceOf(BusinessApiException.class)
                .extracting("code").isEqualTo("SHARED_EXPENSE_INVALID");
        var derivation = new SplitPreviewService.Derivation(SplitPreviewService.VERSION, "EQUAL",
                new SplitPreviewService.Request(new BigDecimal("100"), "VND", "EQUAL",
                        List.of(new SplitPreviewService.Participant(member, null, null)), null));
        var derived = new SharedLedgerCommands.Expense(new BigDecimal("100"), "VND", null,
                Instant.parse("2020-01-01T12:00:00Z"), "DATE_ONLY", "VAULT", null, null,
                split, derivation, null);
        assertThatThrownBy(() -> store.recordExpense(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), derived))
                .isInstanceOf(BusinessApiException.class)
                .extracting("code").isEqualTo("SHARED_EXPENSE_INVALID");
        verifyNoInteractions(jdbc, personal, subjects, journal, history);
    }
}
