package com.savemon.identity.application;

import com.savemon.identity.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnBean(EntityManagerFactory.class)
public class RefreshSessions {
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshSessionRepository sessions;
    private final UserRepository users;
    private final TokenIssuer issuer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public RefreshSessions(RefreshSessionRepository sessions, UserRepository users, TokenIssuer issuer,
            PlatformTransactionManager transactionManager) {
        this(sessions, users, issuer, transactionManager, Clock.systemUTC());
    }

    RefreshSessions(RefreshSessionRepository sessions, UserRepository users, TokenIssuer issuer,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.issuer = issuer;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public IssuedSession create(UUID userId) {
        return withStoreAvailability(() -> transactions.execute(status -> {
            Instant now = clock.instant();
            String refreshToken = newToken();
            TokenIssuer.IssuedAccessToken access = issue(userId);
            sessions.create(UUID.randomUUID(), userId, UUID.randomUUID(), hash(refreshToken), now,
                    now.plus(30, ChronoUnit.DAYS));
            return new IssuedSession(access.accessToken(), access.expiresIn(), refreshToken);
        }));
    }

    public RefreshedSession refresh(String presented) {
        String tokenHash = hashChecked(presented);
        RefreshOutcome outcome = withStoreAvailability(() -> transactions.execute(status -> {
            UUID familyId = sessions.findFamilyId(tokenHash).orElse(null);
            if (familyId == null) return RefreshOutcome.invalidResult();
            RefreshSessionRepository.Family family = sessions.lockFamily(familyId).orElse(null);
            Instant now = clock.instant();
            if (family == null || family.revokedAt() != null || !now.isBefore(family.expiresAt())) {
                return RefreshOutcome.invalidResult();
            }
            RefreshSessionRepository.Token token = sessions.findToken(tokenHash).orElse(null);
            if (token == null || !familyId.equals(token.sessionId())) return RefreshOutcome.invalidResult();
            if (token.consumedAt() != null) {
                sessions.revoke(familyId, now);
                return RefreshOutcome.reusedResult();
            }
            User user = users.findById(family.userId()).orElse(null);
            if (user == null || user.status() != User.Status.ACTIVE) return RefreshOutcome.invalidResult();
            String replacement = newToken();
            TokenIssuer.IssuedAccessToken access = issue(user.id());
            sessions.consumeAndReplace(token.id(), UUID.randomUUID(), familyId, hash(replacement), now);
            return RefreshOutcome.success(new RefreshedSession(
                    new UserSummary(user.id(), user.email().value(), user.displayName()),
                    access.accessToken(), access.expiresIn(), replacement));
        }));
        if (outcome == null || outcome.invalid) throw new RefreshTokenInvalidException();
        if (outcome.reused) throw new RefreshTokenReusedException();
        return outcome.response;
    }

    public void logout(String presented) {
        String tokenHash = hashChecked(presented);
        withStoreAvailability(() -> transactions.execute(status -> {
            UUID familyId = sessions.findFamilyId(tokenHash).orElse(null);
            if (familyId != null) {
                RefreshSessionRepository.Family family = sessions.lockFamily(familyId).orElse(null);
                if (family != null) sessions.revoke(familyId, clock.instant());
            }
            return null;
        }));
    }

    private <T> T withStoreAvailability(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException | TransactionException exception) {
            throw new AuthServiceUnavailableException(exception);
        }
    }

    private TokenIssuer.IssuedAccessToken issue(UUID userId) {
        try {
            return issuer.issue(userId);
        } catch (TokenIssuerUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TokenIssuerUnavailableException();
        }
    }

    private String hashChecked(String presented) {
        if (presented == null || !presented.matches("[A-Za-z0-9_-]{43}")) {
            throw new RefreshTokenInvalidException();
        }
        try {
            if (Base64.getUrlDecoder().decode(presented).length != TOKEN_BYTES) {
                throw new RefreshTokenInvalidException();
            }
        } catch (IllegalArgumentException exception) {
            throw new RefreshTokenInvalidException();
        }
        return hash(presented);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record IssuedSession(String accessToken, long expiresIn, String refreshToken) { }
    public record UserSummary(UUID id, String email, String displayName) { }
    public record RefreshedSession(UserSummary user, String accessToken, long expiresIn, String refreshToken) { }
    private record RefreshOutcome(boolean invalid, boolean reused, RefreshedSession response) {
        static RefreshOutcome invalidResult() { return new RefreshOutcome(true, false, null); }
        static RefreshOutcome reusedResult() { return new RefreshOutcome(false, true, null); }
        static RefreshOutcome success(RefreshedSession response) { return new RefreshOutcome(false, false, response); }
    }
}
