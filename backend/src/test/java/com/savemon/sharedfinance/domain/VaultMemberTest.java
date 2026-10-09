package com.savemon.sharedfinance.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VaultMemberTest {
    private static VaultMember member(VaultMember.Role role) {
        return new VaultMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), role,
                VaultMember.Status.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"), null);
    }
    @Test void onlyActiveOwnerCanRemoveAndLastOwnerCannotBeRemovedOrLeave() {
        var owner = member(VaultMember.Role.OWNER);
        assertThrows(IllegalStateException.class, () -> owner.remove(Instant.now(), false, 2));
        assertThrows(IllegalStateException.class, () -> owner.remove(Instant.now(), true, 1));
        assertThrows(IllegalStateException.class, () -> owner.leave(Instant.now(), 1));
        owner.remove(Instant.now(), true, 2);
        assertEquals(VaultMember.Status.REMOVED, owner.status());
    }
    @Test void reactivatesSameMembershipAndKeepsPriorRole() {
        var member = member(VaultMember.Role.MEMBER);
        member.remove(Instant.now(), true, 1);
        member.reactivate(Instant.now(), true);
        assertEquals(VaultMember.Role.MEMBER, member.role());
        assertEquals(VaultMember.Status.ACTIVE, member.status());
        assertNull(member.leftAt());
    }
}
