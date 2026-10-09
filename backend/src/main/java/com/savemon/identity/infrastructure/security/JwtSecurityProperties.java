package com.savemon.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("savemon.security.jwt")
public record JwtSecurityProperties(String publicKey, String keyId) {
}
