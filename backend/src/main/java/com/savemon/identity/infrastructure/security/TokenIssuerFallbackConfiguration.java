package com.savemon.identity.infrastructure.security;

import com.savemon.identity.application.TokenIssuer;
import com.savemon.identity.application.TokenIssuerUnavailableException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps authentication routes available when the deployment has not configured a
 * token issuer. Registration still commits its user before this port is invoked.
 */
@Configuration
public class TokenIssuerFallbackConfiguration {
    @Bean
    @ConditionalOnMissingBean(TokenIssuer.class)
    TokenIssuer unavailableTokenIssuer() {
        return new TokenIssuer() {
            @Override
            public IssuedAccessToken issue(UUID subject) {
                throw new TokenIssuerUnavailableException();
            }
        };
    }
}
