package com.savemon.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final EmailAddress email;
    private final String passwordHash;
    private final Status status;
    private final Instant createdAt;
    private String displayName;
    private Instant updatedAt;

    public User(
            UUID id,
            EmailAddress email,
            String passwordHash,
            String displayName,
            Status status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.displayName = normalizeDisplayName(displayName);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public UUID id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public Status status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void updateDisplayName(String displayName, Instant updatedAt) {
        Instant timestamp = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.displayName = normalizeDisplayName(displayName);
        this.updatedAt = timestamp;
    }

    private static String normalizeDisplayName(String value) {
        String normalized = Objects.requireNonNull(value, "displayName must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new IllegalArgumentException("displayName must be 100 characters or fewer");
        }
        return normalized;
    }

    public enum Status {
        ACTIVE,
        SUSPENDED
    }
}
