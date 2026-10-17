package com.savemon.identity.application;

import com.savemon.identity.domain.EmailAddress;
import com.savemon.identity.domain.Password;
import com.savemon.identity.domain.User;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(EntityManagerFactory.class)
public class LoginUser {
    private static final String DUMMY_PASSWORD_HASH;

    static {
        PasswordEncoder encoder = org.springframework.security.crypto.argon2.Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        DUMMY_PASSWORD_HASH = encoder.encode("savemon-dummy-password");
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessions sessions;

    public LoginUser(UserRepository users, PasswordEncoder passwordEncoder, RefreshSessions sessions) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
    }

    public AuthenticationSuccessResponse execute(String email, String password) {
        EmailAddress canonicalEmail = new EmailAddress(email);
        new Password(password);

        User user = users.findByEmail(canonicalEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches("savemon-dummy-password", DUMMY_PASSWORD_HASH);
            throw new AuthenticationFailedException();
        }

        if (user.status() != User.Status.ACTIVE || !passwordEncoder.matches(password, user.passwordHash())) {
            passwordEncoder.matches("savemon-dummy-password", DUMMY_PASSWORD_HASH);
            throw new AuthenticationFailedException();
        }

        RefreshSessions.IssuedSession issued = sessions.create(user.id());
        return new AuthenticationSuccessResponse(
                new UserSummary(user.id(), user.email().value(), user.displayName()),
                issued.accessToken(),
                issued.expiresIn(),
                issued.refreshToken());
    }

    public record AuthenticationSuccessResponse(UserSummary user, String accessToken, long expiresIn,
            String refreshToken) { }

    public record UserSummary(UUID id, String email, String displayName) { }
}
