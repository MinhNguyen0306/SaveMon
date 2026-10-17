package com.savemon.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.savemon.identity.domain.EmailAddress;
import com.savemon.identity.domain.User;
import com.savemon.identity.infrastructure.persistence.JdbcRefreshSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Exercises V4 and the real JDBC session repository in a unique, never-dropped schema. */
@EnabledIfEnvironmentVariable(named = "SAVEMON_TEST_DB_URL", matches = ".+")
@ResourceLock(Resources.TIME_ZONE)
class RefreshSessionsPostgresTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    private static TimeZone previousTimeZone;

    @BeforeAll
    static void useUtc() {
        // PgJDBC sends the JVM default zone during connection startup.
        previousTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(previousTimeZone);
    }

    @Test
    void rotationConsumesOldTokenAndReuseCommitsFamilyRevocation() {
        Fixture f = fixture();
        String first = f.sessions.create(f.user.id()).refreshToken();
        String firstHash = hash(first);
        UUID family = f.repository.findFamilyId(firstHash).orElseThrow();

        RefreshSessions.RefreshedSession rotated = f.sessions.refresh(first);
        assertThat(rotated.refreshToken()).isNotEqualTo(first);
        assertThat(rotated.user().id()).isEqualTo(f.user.id());
        assertThat(f.repository.findToken(firstHash).orElseThrow().consumedAt()).isEqualTo(NOW);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM session_refresh_tokens WHERE session_id=?",
                Integer.class, family)).isEqualTo(2);
        assertThat(f.repository.lockFamily(family).orElseThrow().revokedAt()).isNull();

        assertThrows(RefreshTokenReusedException.class, () -> f.sessions.refresh(first));
        assertThat(f.repository.lockFamily(family).orElseThrow().revokedAt()).isEqualTo(NOW);
        assertThrows(RefreshTokenInvalidException.class, () -> f.sessions.refresh(rotated.refreshToken()));
    }

    @Test
    void logoutUsesTokenPossessionAndUnknownWellFormedTokenIsIdempotent() {
        Fixture f = fixture();
        String token = f.sessions.create(f.user.id()).refreshToken();
        UUID family = f.repository.findFamilyId(hash(token)).orElseThrow();
        String unknown = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        f.sessions.logout(unknown);
        assertThat(f.repository.lockFamily(family).orElseThrow().revokedAt()).isNull();
        f.sessions.logout(token);
        f.sessions.logout(token);
        f.sessions.logout(unknown);
        assertThat(f.repository.lockFamily(family).orElseThrow().revokedAt()).isEqualTo(NOW);
        assertThrows(RefreshTokenInvalidException.class, () -> f.sessions.refresh(token));
    }

    @Test
    void refreshWaitsForFamilyRowLockAndThenCommitsRotation() throws Exception {
        Fixture f = fixture();
        String token = f.sessions.create(f.user.id()).refreshToken();
        UUID family = f.repository.findFamilyId(hash(token)).orElseThrow();
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var blocker = pool.submit(() -> f.transactions.execute(status -> {
                f.repository.lockFamily(family).orElseThrow();
                held.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("lock release timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return null;
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            var started = new CountDownLatch(1);
            var rotation = pool.submit(() -> {
                started.countDown();
                return f.sessions.refresh(token);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> rotation.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            blocker.get(5, TimeUnit.SECONDS);
            assertThat(rotation.get(5, TimeUnit.SECONDS).refreshToken()).isNotEqualTo(token);
        } finally {
            release.countDown();
        }
    }

    private Fixture fixture() {
        String url = System.getenv("SAVEMON_TEST_DB_URL");
        String username = System.getenv("SAVEMON_TEST_DB_USERNAME");
        String password = System.getenv("SAVEMON_TEST_DB_PASSWORD");
        String schema = "refresh_test_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, username, password).schemas(schema)
                .locations("classpath:db/migration").load().migrate();
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setUrl(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        source.setUsername(username);
        source.setPassword(password);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SHOW TimeZone", String.class)).isEqualTo("UTC");
        UUID userId = UUID.randomUUID();
        User user = new User(userId, new EmailAddress("refresh@example.invalid"), "hash", "Refresh",
                User.Status.ACTIVE, NOW, NOW);
        jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) "
                + "VALUES (?,?,?,?,?,now(),now())", userId, user.email().value(), "hash", "Refresh", "ACTIVE");
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        org.mockito.Mockito.when(users.findById(userId)).thenReturn(java.util.Optional.of(user));
        JdbcRefreshSessionRepository repository = new JdbcRefreshSessionRepository(jdbc);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        RefreshSessions sessions = new RefreshSessions(repository, users,
                subject -> new TokenIssuer.IssuedAccessToken("signed-access", 900), manager,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(jdbc, repository, new TransactionTemplate(manager), sessions, user);
    }

    private static String hash(String token) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private record Fixture(JdbcTemplate jdbc, JdbcRefreshSessionRepository repository,
                           TransactionTemplate transactions, RefreshSessions sessions, User user) { }
}
