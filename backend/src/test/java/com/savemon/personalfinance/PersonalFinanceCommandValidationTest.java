package com.savemon.personalfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.personalfinance.application.PersonalFinanceOperations.FinancialRequest;
import com.savemon.personalfinance.infrastructure.persistence.JdbcPersonalFinanceAdapter;
import com.savemon.shared.interfaces.exception.BusinessApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PersonalFinanceCommandValidationTest {
    @Test
    void rejectsNonPositiveTransactionAmountBeforePersistence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcPersonalFinanceAdapter adapter = new JdbcPersonalFinanceAdapter(jdbc, new ObjectMapper());
        FinancialRequest request = new FinancialRequest(
                UUID.randomUUID(), BigDecimal.ZERO, "VND", Instant.parse("2026-10-08T09:00:00Z"), "Invalid", null);

        assertThatThrownBy(() -> adapter.record(UUID.randomUUID(), "EXPENSE", "key", request))
                .isInstanceOfSatisfying(BusinessApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_TRANSACTION_AMOUNT"));

        verifyNoInteractions(jdbc);
    }
}
