package com.savemon.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.savemon.identity.domain.EmailAddress;
import com.savemon.identity.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginUserTest {
    @Test
    void mapsIdentityStoreOutageToAuthServiceUnavailable() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail(any())).thenThrow(new DataAccessResourceFailureException("store down"));

        assertThatThrownBy(() -> new LoginUser(users, mock(PasswordEncoder.class),
                mock(RefreshSessions.class)).execute("user@example.com", "password-123"))
                .isInstanceOf(AuthServiceUnavailableException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void successfulLoginReturnsTheSameTokenAndUserShapeAsRegistration() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        RefreshSessions sessions = mock(RefreshSessions.class);
        UUID id = UUID.randomUUID();
        User user = new User(id, new EmailAddress("USER@example.com"), "argon-hash", "User",
                User.Status.ACTIVE, Instant.now(), Instant.now());
        when(users.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwords.matches(anyString(), anyString())).thenReturn(true);
        when(sessions.create(id)).thenReturn(new RefreshSessions.IssuedSession("access-token", 900, "refresh-token"));

        LoginUser.AuthenticationSuccessResponse response =
                new LoginUser(users, passwords, sessions).execute(" user@example.COM ", "password-123");

        assertThat(response.user().id()).isEqualTo(id);
        assertThat(response.user().email()).isEqualTo("user@example.com");
        assertThat(response.user().displayName()).isEqualTo("User");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }
}
