package com.savemon.sharedfinance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.identity.application.UserDisplayNameLookup;
import com.savemon.personalfinance.application.DebitOwnedAccount;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcSharedFinancePaginationQaTest {
    @Test
    void membersContributionsAndExpensesReturnPageMetadata() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        UserDisplayNameLookup displayNames=mock(UserDisplayNameLookup.class);
        UUID vault=UUID.randomUUID(),user=UUID.randomUUID();
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
        doAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            if(sql.contains("WHERE vault_id=? AND user_id=? AND status='ACTIVE'")) {
                return List.of(Map.of("id",UUID.randomUUID(),"userId",user,"role","OWNER","status","ACTIVE"));
            }
            return List.of();
        }).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        when(displayNames.displayNames(any())).thenReturn(Map.of());
        JdbcSharedFinanceAdapter adapter=new JdbcSharedFinanceAdapter(jdbc,mock(DebitOwnedAccount.class),
                displayNames,new ObjectMapper());

        Map<String,Object> members=adapter.members(vault,user,0,10);
        Map<String,Object> contributions=adapter.contributions(vault,user,null,null,null,1,5);
        Map<String,Object> expenses=adapter.expenses(vault,user,null,null,null,2,4);

        assertMetadata(members,0,10);
        assertMetadata(contributions,1,5);
        assertMetadata(expenses,2,4);
    }

    private static void assertMetadata(Map<String,Object> page,int number,int size) {
        assertThat(page).containsKeys("items","page","size","totalItems","totalPages")
                .containsEntry("page",number).containsEntry("size",size)
                .containsEntry("totalItems",1L).containsEntry("totalPages",1);
        assertThat(page.get("items")).isEqualTo(List.of());
    }
}
