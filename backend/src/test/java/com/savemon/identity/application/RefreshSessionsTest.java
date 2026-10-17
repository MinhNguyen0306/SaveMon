package com.savemon.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.savemon.identity.domain.EmailAddress;
import com.savemon.identity.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RefreshSessionsTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    @Test
    void mapsSessionStoreFailureWithoutMislabelingTokenIssuer() {
        RefreshSessionRepository unavailable = org.mockito.Mockito.mock(RefreshSessionRepository.class);
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("store down"))
                .when(unavailable).create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        RefreshSessions service = new RefreshSessions(unavailable, users,
                subject -> new TokenIssuer.IssuedAccessToken("signed-access", 900),
                new NoopTransactions(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.create(UUID.randomUUID()))
                .isInstanceOf(AuthServiceUnavailableException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void rotatesAndConsumedReuseRevokesFamilyWithoutReturningReplacement() {
        Fixture fixture = new Fixture(NOW);
        RefreshSessions.IssuedSession first = fixture.service.create(fixture.user.id());
        RefreshSessions.RefreshedSession second = fixture.service.refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.user().id()).isEqualTo(fixture.user.id());
        assertThat(second.expiresIn()).isEqualTo(900);
        assertThatThrownBy(() -> fixture.service.refresh(first.refreshToken()))
                .isInstanceOf(RefreshTokenReusedException.class);
        assertThatThrownBy(() -> fixture.service.refresh(second.refreshToken()))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void logoutByPossessionIsIdempotentAndInvalidatesFamily() {
        Fixture fixture = new Fixture(NOW);
        String token = fixture.service.create(fixture.user.id()).refreshToken();
        fixture.service.logout(token);
        fixture.service.logout(token);
        assertThatThrownBy(() -> fixture.service.refresh(token)).isInstanceOf(RefreshTokenInvalidException.class);
        String unknown = fixture.service.create(fixture.user.id()).refreshToken();
        Fixture separate = new Fixture(NOW);
        separate.service.logout(unknown);
    }

    @Test
    void absoluteThirtyDayExpiryAndSuspensionRejectRefresh() {
        Fixture fixture = new Fixture(NOW);
        String token = fixture.service.create(fixture.user.id()).refreshToken();
        fixture.clock = Clock.fixed(NOW.plusSeconds(30L * 24 * 3600), ZoneOffset.UTC);
        fixture.rebuild();
        assertThatThrownBy(() -> fixture.service.refresh(token)).isInstanceOf(RefreshTokenInvalidException.class);

        Fixture suspended = new Fixture(NOW);
        String activeToken = suspended.service.create(suspended.user.id()).refreshToken();
        suspended.user = new User(suspended.user.id(), suspended.user.email(), suspended.user.passwordHash(),
                suspended.user.displayName(), User.Status.SUSPENDED, NOW, NOW);
        assertThatThrownBy(() -> suspended.service.refresh(activeToken))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    private static final class Fixture {
        final MemorySessions sessions = new MemorySessions();
        User user = new User(UUID.randomUUID(), new EmailAddress("user@example.com"), "hash", "User",
                User.Status.ACTIVE, NOW, NOW);
        Clock clock;
        RefreshSessions service;

        Fixture(Instant instant) { clock = Clock.fixed(instant, ZoneOffset.UTC); rebuild(); }

        void rebuild() {
            UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
            org.mockito.Mockito.when(users.findById(user.id())).thenAnswer(call -> Optional.of(user));
            service = new RefreshSessions(sessions, users,
                    subject -> new TokenIssuer.IssuedAccessToken("signed-access", 900),
                    new NoopTransactions(), clock);
        }
    }

    private static final class NoopTransactions implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }

    private static final class MemorySessions implements RefreshSessionRepository {
        private final Map<UUID, Family> families = new HashMap<>();
        private final Map<String, Token> tokens = new HashMap<>();
        private final Map<UUID, String> hashes = new HashMap<>();

        @Override public void create(UUID familyId, UUID userId, UUID tokenId, String tokenHash,
                Instant now, Instant expiresAt) {
            families.put(familyId, new Family(familyId, userId, expiresAt, null));
            tokens.put(tokenHash, new Token(tokenId, familyId, null));
            hashes.put(tokenId, tokenHash);
        }
        @Override public Optional<UUID> findFamilyId(String hash) {
            return Optional.ofNullable(tokens.get(hash)).map(Token::sessionId);
        }
        @Override public Optional<Family> lockFamily(UUID familyId) { return Optional.ofNullable(families.get(familyId)); }
        @Override public Optional<Token> findToken(String hash) { return Optional.ofNullable(tokens.get(hash)); }
        @Override public void consumeAndReplace(UUID oldId, UUID newId, UUID familyId, String newHash, Instant now) {
            String oldHash = hashes.get(oldId);
            tokens.put(oldHash, new Token(oldId, familyId, now));
            tokens.put(newHash, new Token(newId, familyId, null));
            hashes.put(newId, newHash);
        }
        @Override public void revoke(UUID familyId, Instant now) {
            Family old = families.get(familyId);
            families.put(familyId, new Family(old.id(), old.userId(), old.expiresAt(), now));
        }
    }
}
