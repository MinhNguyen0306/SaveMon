package com.savemon.identity.infrastructure.security;

import com.savemon.identity.application.CurrentUserProvider;
import com.savemon.shared.interfaces.exception.SecurityErrorResponseWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtSecurityConfiguration {

    private static final long ACCESS_TOKEN_LIFETIME_SECONDS = 900;
    private static final String INVALID_TOKEN = "Invalid access token.";
    private static final String ISSUER = "savemon";
    private static final String AUDIENCE = "savemon-api";

    @Bean
    JwtDecoder jwtDecoder(JwtSecurityProperties properties) {
        if (properties.publicKey() == null || properties.publicKey().isBlank()) {
            throw new IllegalStateException("SAVEMON_JWT_PUBLIC_KEY must contain an RSA public key in PEM format.");
        }
        if (properties.keyId() == null || properties.keyId().isBlank()) {
            throw new IllegalStateException("SAVEMON_JWT_KEY_ID must contain the active JWT key identifier.");
        }

        RSAPublicKey publicKey = parsePublicKey(properties.publicKey());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setClaimSetConverter(MappedJwtClaimSetConverter.withDefaults(Map.of("iss", value -> value)));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                requiredClaimsValidator(properties.keyId())));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            SecurityErrorResponseWriter errorResponseWriter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorResponseWriter)
                        .accessDeniedHandler(errorResponseWriter))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(errorResponseWriter)
                        .accessDeniedHandler(errorResponseWriter));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    CurrentUserProvider currentUserProvider() {
        return new SecurityCurrentUserProvider();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> new JwtAuthenticationToken(
                jwt,
                AuthorityUtils.NO_AUTHORITIES,
                jwt.getSubject());
    }

    private OAuth2TokenValidator<Jwt> requiredClaimsValidator(String configuredKeyId) {
        return jwt -> {
            Object keyId = jwt.getHeaders().get("kid");
            if (!configuredKeyId.equals(keyId)) {
                return invalidToken();
            }

            if (!ISSUER.equals(jwt.getClaims().get("iss"))
                    || !jwt.getAudience().contains(AUDIENCE)) {
                return invalidToken();
            }

            String subject = jwt.getSubject();
            try {
                if (subject == null || !UUID.fromString(subject).toString().equalsIgnoreCase(subject)) {
                    return invalidToken();
                }
            } catch (IllegalArgumentException exception) {
                return invalidToken();
            }

            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            if (issuedAt == null || issuedAt.isAfter(Instant.now())
                    || expiresAt == null || !expiresAt.isAfter(issuedAt)
                    || expiresAt.isAfter(issuedAt.plusSeconds(ACCESS_TOKEN_LIFETIME_SECONDS))) {
                return invalidToken();
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", INVALID_TOKEN, null));
    }

    private RSAPublicKey parsePublicKey(String pem) {
        try {
            String encodedKey = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "SAVEMON_JWT_PUBLIC_KEY must be a valid RSA X.509 public key in PEM format.",
                    exception);
        }
    }
}
