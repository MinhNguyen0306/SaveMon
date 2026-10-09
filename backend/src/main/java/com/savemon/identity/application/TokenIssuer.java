package com.savemon.identity.application;

import java.util.UUID;

/** Application port for access-token issuance; transport/deployment belong to its adapter. */
public interface TokenIssuer {
    IssuedAccessToken issue(UUID subject);

    record IssuedAccessToken(String accessToken, long expiresIn) { }
}
