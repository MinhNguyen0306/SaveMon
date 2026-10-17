package com.savemon.identity.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.savemon.identity.application.CurrentUserProvider;
import com.savemon.identity.application.SubjectStatusQuery;
import com.savemon.shared.interfaces.exception.SecurityErrorResponseWriter;
import com.savemon.shared.interfaces.logging.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtSecurityConfigurationTest.TestApplication.class, properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class JwtSecurityConfigurationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({JwtSecurityConfiguration.class, SecurityErrorResponseWriter.class,
            TraceIdFilter.class, SecurityProbeController.class})
    static class TestApplication { }

    private static final String KEY_ID = "security-test-key";
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private SubjectStatusQuery subjectStatus;

    @BeforeEach
    void activeSubjectByDefault() {
        when(subjectStatus.isActive(any(UUID.class))).thenReturn(true);
    }

    @DynamicPropertySource
    static void configureJwtKey(DynamicPropertyRegistry registry) {
        registry.add("savemon.security.jwt.public-key", JwtSecurityConfigurationTest::publicKeyPem);
        registry.add("savemon.security.jwt.key-id", () -> KEY_ID);
    }

    @Test
    void permitsOnlyApprovedPublicRouteMethods() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void requiresAuthenticationAndReturnsContractedUnauthorizedError() throws Exception {
        mockMvc.perform(get("/api/v1/security-probe/current-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Authentication failed."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void authenticatesRs256TokenAndExposesSubjectAsCurrentUserId() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/security-probe/current-user")
                        .header("Authorization", "Bearer " + token(userId.toString(), KEY_ID, Instant.now().plusSeconds(900))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void rejectsSuspendedOrMissingSubjectDespiteValidJwt() throws Exception {
        UUID suspended = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(subjectStatus.isActive(suspended)).thenReturn(false);
        when(subjectStatus.isActive(missing)).thenReturn(false);

        assertUnauthorized(token(suspended.toString(), KEY_ID, Instant.now().plusSeconds(60)));
        assertUnauthorized(token(missing.toString(), KEY_ID, Instant.now().plusSeconds(60)));
    }

    @Test
    void returnsAuthServiceUnavailableWhenSubjectStoreFails() throws Exception {
        UUID userId = UUID.randomUUID();
        when(subjectStatus.isActive(userId))
                .thenThrow(new DataAccessResourceFailureException("identity store unavailable"));

        mockMvc.perform(get("/api/v1/security-probe/current-user")
                        .header("Authorization", "Bearer "
                                + token(userId.toString(), KEY_ID, Instant.now().plusSeconds(60))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"));
    }

    @Test
    void rejectsInvalidKidExpiredTokenAndNonUuidSubject() throws Exception {
        assertUnauthorized(token(UUID.randomUUID().toString(), "unknown-key", Instant.now().plusSeconds(60)));
        assertUnauthorized(token(UUID.randomUUID().toString(), KEY_ID, Instant.now().minusSeconds(120)));
        assertUnauthorized(token("not-a-uuid", KEY_ID, Instant.now().plusSeconds(60)));
        assertUnauthorized(withInvalidSignature(token(
                UUID.randomUUID().toString(),
                KEY_ID,
                Instant.now().plusSeconds(60))));
    }

    @Test
    void rejectsUnsupportedAlgorithmMissingExpiryAndExpiryBeyondLifetime() throws Exception {
        assertUnauthorized(token(
                UUID.randomUUID().toString(),
                KEY_ID,
                Instant.now().plusSeconds(60),
                JWSAlgorithm.RS384));
        assertUnauthorized(token(UUID.randomUUID().toString(), KEY_ID, null));
        assertUnauthorized(token(UUID.randomUUID().toString(), KEY_ID, Instant.now().plusSeconds(901)));
    }

    @Test
    void rejectsInvalidIssuerAndAudience() throws Exception {
        assertUnauthorized(token(
                UUID.randomUUID().toString(),
                KEY_ID,
                Instant.now().plusSeconds(60),
                JWSAlgorithm.RS256,
                "untrusted-issuer",
                "savemon-api"));
        assertUnauthorized(token(
                UUID.randomUUID().toString(),
                KEY_ID,
                Instant.now().plusSeconds(60),
                JWSAlgorithm.RS256,
                "savemon",
                "untrusted-audience"));
    }

    @Test
    void returnsContractedForbiddenErrorForAuthorizationDenial() throws Exception {
        mockMvc.perform(get("/api/v1/security-probe/forbidden")
                        .header("Authorization", "Bearer " + token(
                                UUID.randomUUID().toString(),
                                KEY_ID,
                                Instant.now().plusSeconds(60))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void passwordEncoderUsesArgon2idAndCanVerifyHashes() {
        String rawPassword = "approved test password";
        String encoded = passwordEncoder.encode(rawPassword);

        assertThat(encoded).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches(rawPassword, encoded)).isTrue();
        assertThat(passwordEncoder.matches("incorrect password", encoded)).isFalse();
    }

    private void assertUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/api/v1/security-probe/current-user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private static String withInvalidSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char firstSignatureCharacter = token.charAt(signatureStart);
        char replacement = firstSignatureCharacter == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private static String token(String subject, String keyId, Instant expiration) {
        return token(subject, keyId, expiration, JWSAlgorithm.RS256);
    }

    private static String token(
            String subject,
            String keyId,
            Instant expiration,
            JWSAlgorithm algorithm) {
        return token(subject, keyId, expiration, algorithm, "savemon", "savemon-api");
    }

    private static String token(
            String subject,
            String keyId,
            Instant expiration,
            JWSAlgorithm algorithm,
            String issuer,
            String audience) {
        try {
            Instant issuedAt = Instant.now();
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject(subject)
                    .issueTime(Date.from(issuedAt));
            if (expiration != null) {
                claimsBuilder.expirationTime(Date.from(expiration));
            }
            JWTClaimsSet claims = claimsBuilder.build();
            SignedJWT signedJwt = new SignedJWT(
                    new JWSHeader.Builder(algorithm).keyID(keyId).build(),
                    claims);
            signedJwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return signedJwt.serialize();
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("Unable to sign test JWT.", exception);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to generate test JWT key.", exception);
        }
    }

    private static String publicKeyPem() {
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(KEY_PAIR.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }

    @RestController
    static class SecurityProbeController {

        private final CurrentUserProvider currentUserProvider;

        SecurityProbeController(CurrentUserProvider currentUserProvider) {
            this.currentUserProvider = currentUserProvider;
        }

        @PostMapping({
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout"
        })
        void publicAuthRouteProbe() {
        }

        @GetMapping("/api/v1/security-probe/current-user")
        UserIdResponse currentUser() {
            return new UserIdResponse(currentUserProvider.currentUserId().orElseThrow());
        }

        @GetMapping("/api/v1/security-probe/forbidden")
        @PreAuthorize("denyAll()")
        void forbidden() {
        }
    }

    record UserIdResponse(UUID userId) {
    }
}
