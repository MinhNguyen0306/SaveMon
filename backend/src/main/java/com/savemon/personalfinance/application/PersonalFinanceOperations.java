package com.savemon.personalfinance.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Application use-case boundary for personal financial commands and queries. */
public interface PersonalFinanceOperations {
    Map<String,Object> createAccount(UUID user,String name,String type,String currency);
    Map<String,Object> accounts(UUID user,String status,int page,int size);
    Map<String,Object> account(UUID id,UUID user);
    Map<String,Object> updateAccount(UUID id,UUID user,String name);
    void archiveAccount(UUID id,UUID user);
    Map<String,Object> createCategory(UUID user,String name,String type,UUID parentId);
    Map<String,Object> categories(UUID user,String type,int page,int size);
    Map<String,Object> updateCategory(UUID id,UUID user,String name);
    void archiveCategory(UUID id,UUID user);
    Map<String,Object> record(UUID user,String type,String key,FinancialRequest request);
    Map<String,Object> transaction(UUID id,UUID user);
    Map<String,Object> history(UUID user,UUID accountId,String type,String status,Instant from,Instant to,
                                UUID categoryId,String currency,int page,int size);
    Map<String,Object> updateTransaction(UUID id,UUID user,EditRequest request);
    Map<String,Object> reverse(UUID id,UUID user,String key,String reason);
    Map<String,Object> calendar(UUID user,LocalDate from,LocalDate to,String currency);
    Map<String,Object> summary(UUID user,LocalDate from,LocalDate to,String currency);

    record FinancialRequest(UUID accountId, BigDecimal amount, String currency, Instant transactionDate,
                            String description, List<ItemInput> items) { }
    record ItemInput(UUID categoryId, BigDecimal amount, String description) { }
    record EditRequest(String description, Instant transactionDate, List<ItemInput> items) { }
}
