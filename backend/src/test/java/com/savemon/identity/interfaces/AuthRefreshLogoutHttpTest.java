package com.savemon.identity.interfaces;

import com.savemon.identity.application.LoginUser;
import com.savemon.identity.application.AuthServiceUnavailableException;
import com.savemon.identity.application.RefreshSessions;
import com.savemon.identity.application.RefreshTokenInvalidException;
import com.savemon.identity.application.RefreshTokenReusedException;
import com.savemon.identity.application.RegisterUser;
import com.savemon.shared.interfaces.exception.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthRefreshLogoutHttpTest {
    private RefreshSessions sessions;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        sessions = mock(RefreshSessions.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(
                        mock(RegisterUser.class), mock(LoginUser.class), sessions))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void refreshMapsRotatedSessionToContractResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        when(sessions.refresh("presented")).thenReturn(new RefreshSessions.RefreshedSession(
                new RefreshSessions.UserSummary(userId, "user@example.invalid", "User"),
                "signed-access", 900, "replacement"));

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"presented\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.accessToken").value("signed-access"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value("replacement"));
        verify(sessions).refresh("presented");
    }

    @Test
    void refreshMapsInvalidAndReuseToDistinctUnauthorizedErrors() throws Exception {
        when(sessions.refresh("invalid")).thenThrow(new RefreshTokenInvalidException());
        when(sessions.refresh("reused")).thenThrow(new RefreshTokenReusedException());

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"reused\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void logoutNeedsOnlyPresentedRefreshTokenAndIsIdempotentAtHttpBoundary() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"unknown-well-formed\"}"))
                    .andExpect(status().isNoContent());
        }
        verify(sessions, org.mockito.Mockito.times(2)).logout("unknown-well-formed");
    }

    @Test
    void rejectsMissingRefreshToken() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void mapsSessionStoreFailureToDocumentedUnavailableError() throws Exception {
        when(sessions.refresh("presented")).thenThrow(new AuthServiceUnavailableException(
                new DataAccessResourceFailureException("store unavailable")));

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"presented\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"));
    }
}
