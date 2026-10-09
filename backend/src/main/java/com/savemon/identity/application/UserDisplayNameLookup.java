package com.savemon.identity.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Identity-owned query boundary for display names needed by other modules. */
public interface UserDisplayNameLookup {
    Map<UUID,String> displayNames(Collection<UUID> userIds);
}
