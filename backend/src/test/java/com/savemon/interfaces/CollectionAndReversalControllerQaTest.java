package com.savemon.interfaces;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.savemon.identity.application.CurrentUserProvider;
import com.savemon.personalfinance.application.PersonalFinanceOperations;
import com.savemon.personalfinance.interfaces.PersonalFinanceController;
import com.savemon.sharedfinance.application.SharedFinanceOperations;
import com.savemon.sharedfinance.interfaces.SharedFinanceController;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollectionAndReversalControllerQaTest {
    @Test
    void reverseAcceptsAnAbsentOptionalRequestBody() {
        PersonalFinanceOperations operations=mock(PersonalFinanceOperations.class);
        CurrentUserProvider currentUser=mock(CurrentUserProvider.class);
        UUID user=UUID.randomUUID(),transaction=UUID.randomUUID();
        when(currentUser.currentUserId()).thenReturn(Optional.of(user));
        when(operations.reverse(transaction,user,"key",null)).thenReturn(Map.of("status","REVERSED"));

        new PersonalFinanceController(operations,currentUser).reverse(transaction,"key",null);

        verify(operations).reverse(transaction,user,"key",null);
    }

    @Test
    void sharedCollectionControllersForwardPageSizeAndExistingFilters() {
        SharedFinanceOperations operations=mock(SharedFinanceOperations.class);
        CurrentUserProvider currentUser=mock(CurrentUserProvider.class);
        UUID user=UUID.randomUUID(),vault=UUID.randomUUID(),member=UUID.randomUUID();
        when(currentUser.currentUserId()).thenReturn(Optional.of(user));
        SharedFinanceController controller=new SharedFinanceController(operations,currentUser);
        LocalDate from=LocalDate.parse("2026-10-01"),to=LocalDate.parse("2026-10-31");

        controller.members(vault,2,15);
        controller.contributions(vault,member,from,to,3,25);
        controller.expenses(vault,from,to,member,4,30);

        verify(operations).members(vault,user,2,15);
        verify(operations).contributions(vault,user,member,from,to,3,25);
        verify(operations).expenses(vault,user,from,to,member,4,30);
    }
}
