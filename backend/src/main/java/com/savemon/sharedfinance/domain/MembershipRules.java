package com.savemon.sharedfinance.domain;

import java.util.List;
import java.util.Objects;

/** Rules requiring a locked/current membership set are enforced by the application transaction. */
public final class MembershipRules {
    private MembershipRules() { }
    public static void requireOwner(boolean actorIsActiveOwner) {
        if (!actorIsActiveOwner) throw new IllegalStateException("Only an active OWNER may manage members");
    }
    public static void requireNotLastOwner(boolean targetIsActiveOwner, long activeOwnerCount) {
        if (targetIsActiveOwner && activeOwnerCount <= 1) {
            throw new IllegalStateException("The last active owner cannot be removed or leave");
        }
    }
    public static void requireActiveMemberPaidPayer(String authenticatedMemberId, String payerMemberId) {
        if (!Objects.equals(authenticatedMemberId, payerMemberId)) {
            throw new IllegalStateException("Payer member must match the authenticated caller");
        }
    }
}
