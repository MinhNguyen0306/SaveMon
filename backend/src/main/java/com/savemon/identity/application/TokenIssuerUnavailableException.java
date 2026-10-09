package com.savemon.identity.application;

public final class TokenIssuerUnavailableException extends RuntimeException {
    public TokenIssuerUnavailableException() {
        super("Token issuer unavailable.");
    }
}
