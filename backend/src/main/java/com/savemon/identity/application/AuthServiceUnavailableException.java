package com.savemon.identity.application;

/** The authoritative auth session store cannot serve the request. */
public final class AuthServiceUnavailableException extends RuntimeException {
    public AuthServiceUnavailableException(Throwable cause) {
        super("Auth service unavailable.", cause);
    }
}
