package com.savemon.identity.application;

import java.util.Optional;
import java.util.UUID;

public interface CurrentUserProvider {

    Optional<UUID> currentUserId();
}
