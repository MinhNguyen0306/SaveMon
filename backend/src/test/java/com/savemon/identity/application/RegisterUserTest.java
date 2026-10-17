package com.savemon.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RegisterUserTest {
    @Test
    void commitsUserBeforeCallingTokenIssuerAndReturnsAuthenticationPayload() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        RefreshSessions sessions = mock(RefreshSessions.class);
        TrackingTransactionManager transactions = new TrackingTransactionManager();
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(passwords.encode(anyString())).thenReturn("argon-hash");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sessions.create(any())).thenAnswer(call -> {
            assertThat(transactions.active).isFalse();
            return new RefreshSessions.IssuedSession("access-token", 900, "refresh-token");
        });

        RegisterUser.AuthenticationSuccessResponse result =
                new RegisterUser(users, passwords, sessions, transactions)
                        .execute(" USER@Example.com ", "password-123", " SaveMon User ");

        assertThat(result.user().email()).isEqualTo("user@example.com");
        assertThat(result.user().displayName()).isEqualTo("SaveMon User");
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.expiresIn()).isEqualTo(900);
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(transactions.committed).isTrue();
        verify(sessions).create(result.user().id());
    }

    @Test
    void preservesCommittedUserWhenIssuerIsUnavailable() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        RefreshSessions sessions = mock(RefreshSessions.class);
        TrackingTransactionManager transactions = new TrackingTransactionManager();
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(passwords.encode(anyString())).thenReturn("argon-hash");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sessions.create(any())).thenThrow(new TokenIssuerUnavailableException());

        assertThatThrownBy(() -> new RegisterUser(users, passwords, sessions, transactions)
                .execute("user@example.com", "password-123", "User"))
                .isInstanceOf(TokenIssuerUnavailableException.class);

        assertThat(transactions.committed).isTrue();
        verify(users).save(any());
    }

    @Test
    void doesNotMislabelSessionStoreFailureAsIssuerFailure() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        RefreshSessions sessions = mock(RefreshSessions.class);
        TrackingTransactionManager transactions = new TrackingTransactionManager();
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(passwords.encode(anyString())).thenReturn("argon-hash");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sessions.create(any())).thenThrow(new AuthServiceUnavailableException(
                new DataAccessResourceFailureException("store unavailable")));

        assertThatThrownBy(() -> new RegisterUser(users, passwords, sessions, transactions)
                .execute("user@example.com", "password-123", "User"))
                .isInstanceOf(AuthServiceUnavailableException.class);
        assertThat(transactions.committed).isTrue();
    }

    private static final class TrackingTransactionManager implements PlatformTransactionManager {
        private boolean active;
        private boolean committed;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            active = true;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            active = false;
            committed = true;
        }

        @Override
        public void rollback(TransactionStatus status) {
            active = false;
        }
    }
}
