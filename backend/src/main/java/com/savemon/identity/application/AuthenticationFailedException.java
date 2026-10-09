package com.savemon.identity.application;

public final class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Authentication failed.");
    }
}
