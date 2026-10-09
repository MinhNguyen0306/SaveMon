package com.savemon.sharedfinance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Membership history is retained; leaving/removal changes state rather than deleting the row. */
public final class VaultMember {
    private final UUID id;
    private final UUID vaultId;
    private final UUID userId;
    private Role role;
    private Status status;
    private Instant joinedAt;
    private Instant leftAt;

    public VaultMember(UUID id, UUID vaultId, UUID userId, Role role, Status status,
                       Instant joinedAt, Instant leftAt) {
        this.id = Objects.requireNonNull(id);
        this.vaultId = Objects.requireNonNull(vaultId);
        this.userId = Objects.requireNonNull(userId);
        this.role = Objects.requireNonNull(role);
        this.status = Objects.requireNonNull(status);
        this.joinedAt = Objects.requireNonNull(joinedAt);
        this.leftAt = leftAt;
    }
    public void remove(Instant at, boolean actorIsActiveOwner, long activeOwnerCount) {
        requireActive();
        MembershipRules.requireOwner(actorIsActiveOwner);
        MembershipRules.requireNotLastOwner(role == Role.OWNER, activeOwnerCount);
        status = Status.REMOVED;
        leftAt = Objects.requireNonNull(at);
    }
    public void leave(Instant at, long activeOwnerCount) {
        requireActive();
        MembershipRules.requireNotLastOwner(role == Role.OWNER, activeOwnerCount);
        status = Status.REMOVED;
        leftAt = Objects.requireNonNull(at);
    }
    /** Reactivation keeps the prior role and membership identity, retaining history. */
    public void reactivate(Instant at, boolean actorIsActiveOwner) {
        MembershipRules.requireOwner(actorIsActiveOwner);
        if (status == Status.ACTIVE) throw new IllegalStateException("Member is already active");
        status = Status.ACTIVE;
        joinedAt = Objects.requireNonNull(at);
        leftAt = null;
    }
    private void requireActive() {
        if (status != Status.ACTIVE) throw new IllegalStateException("Member is not active");
    }
    public UUID id() { return id; }
    public UUID vaultId() { return vaultId; }
    public UUID userId() { return userId; }
    public Role role() { return role; }
    public Status status() { return status; }
    public Instant joinedAt() { return joinedAt; }
    public Instant leftAt() { return leftAt; }
    public enum Role { OWNER, MEMBER }
    public enum Status { ACTIVE, REMOVED }
}
