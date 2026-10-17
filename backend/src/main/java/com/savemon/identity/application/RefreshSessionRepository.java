package com.savemon.identity.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** All calls after lockFamily must run in the same database transaction. */
public interface RefreshSessionRepository {
    record Token(UUID id, UUID sessionId, Instant consumedAt) { }
    record Family(UUID id, UUID userId, Instant expiresAt, Instant revokedAt) { }

    void create(UUID familyId, UUID userId, UUID tokenId, String tokenHash, Instant now, Instant expiresAt);
    Optional<UUID> findFamilyId(String tokenHash);
    Optional<Family> lockFamily(UUID familyId);
    Optional<Token> findToken(String tokenHash);
    void consumeAndReplace(UUID tokenId, UUID newTokenId, UUID familyId, String newHash, Instant now);
    void revoke(UUID familyId, Instant now);
}
