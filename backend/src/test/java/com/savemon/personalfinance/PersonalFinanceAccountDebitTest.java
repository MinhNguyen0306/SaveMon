package com.savemon.personalfinance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.personalfinance.infrastructure.persistence.JdbcPersonalFinanceAdapter;
import com.savemon.shared.interfaces.exception.BusinessApiException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PersonalFinanceAccountDebitTest {
    @Test
    void rejectsDebitWhenTheLockedAccountCannotCoverIt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Map<String,Object> account = new LinkedHashMap<>();
        account.put("id", accountId);
        account.put("userId", owner);
        account.put("currency", "VND");
        account.put("status", "ACTIVE");
        account.put("balance", new BigDecimal("20.0000"));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(account));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        JdbcPersonalFinanceAdapter adapter = new JdbcPersonalFinanceAdapter(jdbc, new ObjectMapper());

        assertThatThrownBy(() -> adapter.debit(owner, accountId, new BigDecimal("20.01"), "VND"))
                .isInstanceOfSatisfying(BusinessApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("INSUFFICIENT_BALANCE"));
    }
}
