package com.savemon.identity.application;

public final class IdentityConflictException extends RuntimeException {
    public IdentityConflictException() {
        super("Identity already exists.");
    }
}
