package com.savemon.identity.application;

import com.savemon.identity.domain.User;
import java.util.UUID;

public final class GetCurrentUser {
    private final UserRepository users;
    private final CurrentUserProvider currentUser;

    public GetCurrentUser(UserRepository users, CurrentUserProvider currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    public User execute() {
        UUID userId = currentUser.currentUserId()
                .orElseThrow(UnauthenticatedException::new);
        return users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    public static final class UnauthenticatedException extends RuntimeException { }

    public static final class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(UUID userId) { super("User not found: " + userId); }
    }
}
