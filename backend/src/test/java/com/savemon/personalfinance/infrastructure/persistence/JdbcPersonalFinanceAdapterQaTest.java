package com.savemon.personalfinance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcPersonalFinanceAdapterQaTest {
    @Test
    void historyItemsContainTransactionResourceFieldsAndPageMetadata() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID user = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Map<String,Object> historyRow = new LinkedHashMap<>();
        historyRow.put("id",transactionId);
        historyRow.put("type","EXPENSE");
        historyRow.put("amount",new BigDecimal("25.00"));
        historyRow.put("currency","VND");
        historyRow.put("transactionDate",Instant.parse("2026-10-08T12:00:00Z"));
        historyRow.put("description","Lunch");
        historyRow.put("status","ACTIVE");
        historyRow.put("accountId",accountId);
        historyRow.put("accountName","Cash");
        Map<String,Object> transactionItem = Map.of(
                "transactionId",transactionId,"categoryId",categoryId,"categoryName","Food",
                "amount",new BigDecimal("25.00"));
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
        doAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            if(sql.contains("FROM transactions t JOIN accounts a"))return List.of(historyRow);
            if(sql.contains("FROM transaction_items ti"))return List.of(transactionItem);
            return List.of();
        }).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));

        Map<String,Object> result=new JdbcPersonalFinanceAdapter(jdbc,new ObjectMapper())
                .history(user,null,null,null,null,null,null,null,0,20);

        assertThat(result).containsEntry("page",0).containsEntry("size",20)
                .containsEntry("totalItems",1L).containsEntry("totalPages",1);
        @SuppressWarnings("unchecked")
        Map<String,Object> item=((List<Map<String,Object>>)result.get("items")).get(0);
        assertThat(item).containsKeys("id","type","amount","currency","transactionDate","description","status","account","items");
        assertThat(item.get("account")).isEqualTo(Map.of("id",accountId,"name","Cash"));
        assertThat(item.get("items")).isEqualTo(List.of(Map.of(
                "categoryId",categoryId,"categoryName","Food","amount",new BigDecimal("25.00"))));
    }

    @Test
    void reversalReturnsTheDedicatedResourceShape() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID user=UUID.randomUUID(),originalId=UUID.randomUUID(),accountId=UUID.randomUUID();
        Instant transactionDate=Instant.parse("2026-10-08T12:00:00Z");
        AtomicReference<String> reversalAuditMetadata=new AtomicReference<>();
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            if(sql.contains("INSERT INTO audit_logs")) {
                reversalAuditMetadata.set(invocation.getArgument(6));
            }
            return 1;
        });
        doAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            if(sql.contains("FROM transactions WHERE id=? AND user_id=?")) {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",originalId);row.put("accountId",accountId);row.put("type","EXPENSE");
                row.put("amount",new BigDecimal("25.00"));row.put("currency","VND");
                row.put("transactionDate",transactionDate);row.put("status","ACTIVE");row.put("version",0L);
                return List.of(row);
            }
            if(sql.contains("FROM accounts WHERE id=? AND user_id=? FOR UPDATE")) {
                return List.of(Map.of("id",accountId,"balance",BigDecimal.ZERO,"currency","VND",
                        "status","ACTIVE","version",0L));
            }
            return List.of();
        }).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));

        Map<String,Object> result=new JdbcPersonalFinanceAdapter(jdbc,new ObjectMapper())
                .reverse(originalId,user,"reverse-key","Mistake");

        assertThat(result).containsEntry("id",originalId)
                .containsEntry("originalTransactionId",originalId)
                .containsEntry("status","REVERSED")
                .containsEntry("reason","Mistake");
        assertThat(result.get("reversalTransactionId")).isInstanceOf(UUID.class);
        assertThat(result.get("reversedAt")).isInstanceOf(Instant.class);
        try {
            Map<String, Object> auditMetadata = new ObjectMapper().readValue(
                    reversalAuditMetadata.get(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            assertThat(auditMetadata).containsEntry(
                    "reversalTransactionId", result.get("reversalTransactionId").toString())
                    .containsEntry("reason", "Mistake");
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError("Reversal audit metadata should be valid JSON.", exception);
        }
    }

    @Test
    void idempotentReversalReplayReturnsTheSameDedicatedResource() throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        ObjectMapper json=new ObjectMapper();
        UUID user=UUID.randomUUID(),originalId=UUID.randomUUID(),accountId=UUID.randomUUID();
        Instant transactionDate=Instant.parse("2026-10-08T12:00:00Z");
        AtomicInteger reservations=new AtomicInteger();
        AtomicReference<Instant> reversedAt=new AtomicReference<>();
        AtomicReference<UUID> reversalId=new AtomicReference<>();
        Map<String,Object> request=Map.of("transactionId",originalId,"reason","Mistake");
        String requestHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsBytes(request)));
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            Object[] args=invocation.getArguments();
            if(sql.startsWith("INSERT INTO idempotency_keys"))
                return reservations.getAndIncrement()==0?1:0;
            if(sql.startsWith("UPDATE transactions SET status='REVERSED'"))reversedAt.set((Instant)args[1]);
            if(sql.startsWith("INSERT INTO transactions"))reversalId.set((UUID)args[1]);
            return 1;
        });
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of(
                "request_hash",requestHash,"reference",originalId));
        doAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            if(sql.contains("SELECT id FROM transactions"))return List.of(reversalId.get());
            if(sql.contains("FROM transactions WHERE id=? AND user_id=?")) {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",originalId);row.put("accountId",accountId);row.put("type","EXPENSE");
                row.put("amount",new BigDecimal("25.00"));
                if(sql.contains("FOR UPDATE")) {
                    row.put("currency","VND");row.put("transactionDate",transactionDate);
                    row.put("status","ACTIVE");row.put("version",0L);
                } else {
                    row.put("status","REVERSED");row.put("reversedAt",reversedAt.get());
                }
                return List.of(row);
            }
            if(sql.contains("FROM accounts WHERE id=? AND user_id=? FOR UPDATE"))
                return List.of(Map.of("id",accountId,"balance",BigDecimal.ZERO,"currency","VND",
                        "status","ACTIVE","version",0L));
            return List.of();
        }).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        JdbcPersonalFinanceAdapter adapter=new JdbcPersonalFinanceAdapter(jdbc,json);

        Map<String,Object> initial=adapter.reverse(originalId,user,"reverse-key","Mistake");
        Map<String,Object> replay=adapter.reverse(originalId,user,"reverse-key","Mistake");

        assertThat(replay).isEqualTo(initial);
        assertThat(replay).containsEntry("originalTransactionId",originalId)
                .containsEntry("reversalTransactionId",reversalId.get())
                .containsEntry("status","REVERSED").containsEntry("reason","Mistake");
    }
}
