package com.savemon.sharedfinance.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Application use-case boundary for shared-finance resources and accounting commands. */
public interface SharedFinanceOperations {
    Map<String,Object> createVault(UUID actor,String name,String currency);
    Map<String,Object> vaults(UUID actor,int page,int size);
    Map<String,Object> vault(UUID id,UUID actor);
    Map<String,Object> members(UUID vault,UUID actor,int page,int size);
    Map<String,Object> addMember(UUID vault,UUID actor,UUID user);
    void removeMember(UUID vault,UUID actor,UUID memberId);
    Map<String,Object> contribution(UUID vault,UUID actor,String key,ContributionRequest request);
    Map<String,Object> contributions(UUID vault,UUID actor,UUID memberId,LocalDate from,LocalDate to,int page,int size);
    Map<String,Object> expense(UUID vault,UUID actor,String key,ExpenseRequest request);
    Map<String,Object> expenses(UUID vault,UUID actor,LocalDate from,LocalDate to,UUID memberId,int page,int size);
    Map<String,Object> expenseDetail(UUID expense,UUID vault,UUID actor);
    Map<String,Object> responsibility(UUID vault,UUID actor,UUID memberId);

    record ContributionRequest(UUID sourceAccountId,BigDecimal amount,String currency,String description,
                               Instant contributedAt) { }
    record FundingInput(String type,UUID payerMemberId,UUID sourceAccountId) { }
    record SplitInput(UUID memberId,BigDecimal amount) { }
    record ExpenseRequest(BigDecimal amount,String currency,String description,Instant expenseDate,
                          FundingInput funding,List<SplitInput> splits) { }
}
