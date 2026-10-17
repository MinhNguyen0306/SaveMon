package com.savemon.identity.infrastructure.persistence;

import com.savemon.identity.application.RefreshSessionRepository;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(EntityManagerFactory.class)
public class JdbcRefreshSessionRepository implements RefreshSessionRepository {
    private final JdbcTemplate jdbc;

    public JdbcRefreshSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void create(UUID familyId, UUID userId, UUID tokenId, String tokenHash, Instant now, Instant expiresAt) {
        jdbc.update("INSERT INTO refresh_sessions(id,user_id,created_at,expires_at) VALUES (?,?,?,?)",
                familyId, userId, Timestamp.from(now), Timestamp.from(expiresAt));
        jdbc.update("INSERT INTO session_refresh_tokens(id,session_id,token_hash,created_at) VALUES (?,?,?,?)",
                tokenId, familyId, tokenHash, Timestamp.from(now));
    }

    @Override
    public Optional<UUID> findFamilyId(String tokenHash) {
        return jdbc.query("SELECT session_id FROM session_refresh_tokens WHERE token_hash=?",
                (rs, row) -> rs.getObject(1, UUID.class), tokenHash).stream().findFirst();
    }

    @Override
    public Optional<Family> lockFamily(UUID familyId) {
        return jdbc.query("SELECT id,user_id,expires_at,revoked_at FROM refresh_sessions WHERE id=? FOR UPDATE",
                (rs, row) -> new Family(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant()), familyId)
                .stream().findFirst();
    }

    @Override
    public Optional<Token> findToken(String tokenHash) {
        return jdbc.query("SELECT id,session_id,consumed_at FROM session_refresh_tokens WHERE token_hash=?",
                (rs, row) -> new Token(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()), tokenHash)
                .stream().findFirst();
    }

    @Override
    public void consumeAndReplace(UUID tokenId, UUID newTokenId, UUID familyId, String newHash, Instant now) {
        jdbc.update("INSERT INTO session_refresh_tokens(id,session_id,token_hash,created_at) VALUES (?,?,?,?)",
                newTokenId, familyId, newHash, Timestamp.from(now));
        jdbc.update("UPDATE session_refresh_tokens SET consumed_at=?,replaced_by=? WHERE id=? AND consumed_at IS NULL",
                Timestamp.from(now), newTokenId, tokenId);
    }

    @Override
    public void revoke(UUID familyId, Instant now) {
        jdbc.update("UPDATE refresh_sessions SET revoked_at=? WHERE id=? AND revoked_at IS NULL",
                Timestamp.from(now), familyId);
    }
}
