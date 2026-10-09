package com.savemon.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTest {

    @Test
    void storesApprovedUserIdentityAndStatus() {
        UUID id = UUID.randomUUID();
        EmailAddress email = new EmailAddress("user@example.com");
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");

        User user = new User(
                id,
                email,
                "encoded-password",
                "Minh",
                User.Status.ACTIVE,
                createdAt,
                updatedAt);

        assertEquals(id, user.id());
        assertEquals(email, user.email());
        assertEquals("encoded-password", user.passwordHash());
        assertEquals("Minh", user.displayName());
        assertEquals(User.Status.ACTIVE, user.status());
        assertEquals(createdAt, user.createdAt());
        assertEquals(updatedAt, user.updatedAt());
    }

    @Test
    void updatesOnlyProfileNameAndUpdatedTimestamp() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-02-01T00:00:00Z");
        User user = new User(
                id,
                new EmailAddress("user@example.com"),
                "encoded-password",
                "Minh",
                User.Status.SUSPENDED,
                createdAt,
                createdAt);

        user.updateDisplayName("New Name", updatedAt);

        assertEquals("New Name", user.displayName());
        assertEquals(updatedAt, user.updatedAt());
        assertEquals(id, user.id());
        assertEquals("user@example.com", user.email().value());
        assertEquals("encoded-password", user.passwordHash());
        assertEquals(User.Status.SUSPENDED, user.status());
        assertEquals(createdAt, user.createdAt());
    }
}
