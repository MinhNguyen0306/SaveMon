package com.savemon.identity.application;

import com.savemon.identity.domain.EmailAddress;
import com.savemon.identity.domain.Password;
import com.savemon.identity.domain.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnBean(EntityManagerFactory.class)
public class RegisterUser {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final TransactionTemplate transactionTemplate;

    public RegisterUser(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            TokenIssuer tokenIssuer,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AuthenticationSuccessResponse execute(String email, String password, String displayName) {
        EmailAddress canonicalEmail = new EmailAddress(email);
        new Password(password);
        String normalizedDisplayName = normalizeDisplayName(displayName);

        if (users.findByEmail(canonicalEmail).isPresent()) {
            throw new IdentityConflictException();
        }

        User createdUser;
        try {
            createdUser = transactionTemplate.execute(status -> {
                if (users.findByEmail(canonicalEmail).isPresent()) {
                    throw new IdentityConflictException();
                }
                Instant now = Instant.now();
                User user = new User(
                        UUID.randomUUID(),
                        canonicalEmail,
                        passwordEncoder.encode(password),
                        normalizedDisplayName,
                        User.Status.ACTIVE,
                        now,
                        now);
                return users.save(user);
            });
        } catch (DataIntegrityViolationException exception) {
            // The unique canonical-email index is the final arbiter for concurrent registrations.
            throw new IdentityConflictException();
        }

        try {
            TokenIssuer.IssuedAccessToken issued = tokenIssuer.issue(createdUser.id());
            return new AuthenticationSuccessResponse(
                    new UserSummary(createdUser.id(), createdUser.email().value(), createdUser.displayName()),
                    issued.accessToken(),
                    issued.expiresIn());
        } catch (TokenIssuerUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TokenIssuerUnavailableException();
        }
    }

    private String normalizeDisplayName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new IllegalArgumentException("displayName must be 100 characters or fewer");
        }
        return normalized;
    }

    public record AuthenticationSuccessResponse(UserSummary user, String accessToken, long expiresIn) { }

    public record UserSummary(UUID id, String email, String displayName) { }
}
