package com.savemon.identity.infrastructure.persistence;

import com.savemon.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserJpaEntity() { }

    public UserJpaEntity(User user) {
        id = user.id(); email = user.email().value(); passwordHash = user.passwordHash();
        displayName = user.displayName(); status = user.status().name();
        createdAt = user.createdAt(); updatedAt = user.updatedAt();
    }

    public User toDomain() {
        return new User(id, new com.savemon.identity.domain.EmailAddress(email), passwordHash,
                displayName, User.Status.valueOf(status), createdAt, updatedAt);
    }
}
