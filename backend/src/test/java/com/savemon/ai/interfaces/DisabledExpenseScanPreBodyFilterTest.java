package com.savemon.ai.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savemon.ai.application.ExpenseScanEntitlementGate;
import com.savemon.ai.application.PremiumEntitlementQuery;
import com.savemon.ai.infrastructure.DisabledPremiumEntitlementQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DisabledExpenseScanPreBodyFilterTest {
    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void rejectsAuthenticatedScanBeforeReadingMultipartBody() throws Exception {
        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(UUID.randomUUID().toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
        MockHttpServletRequest request = spy(new MockHttpServletRequest("POST", "/api/v1/expense-scans"));
        request.setServletPath("/api/v1/expense-scans");
        request.setContentType("multipart/form-data; boundary=example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);

        new DisabledExpenseScanPreBodyFilter(
                new ExpenseScanEntitlementGate(new DisabledPremiumEntitlementQuery(), Clock.systemUTC()),
                new ObjectMapper()).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("PREMIUM_ENTITLEMENT_UNAVAILABLE");
        verify(request, never()).getInputStream();
        verify(request, never()).getParts();
        verifyNoInteractions(chain);
    }

    @Test
    void deniesAliasPathsAndOtherMethodsWithoutMultipartAccess() throws Exception {
        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(UUID.randomUUID().toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
        var filter = new DisabledExpenseScanPreBodyFilter(
                new ExpenseScanEntitlementGate(new DisabledPremiumEntitlementQuery(), Clock.systemUTC()),
                new ObjectMapper());
        for (String path : new String[]{"/api/v1/expense-scans/", "/api/v1/expense-scans;variant",
                "/api/v1/expense-scans%2F", "/api/v1/expense-scans/confirm"}) {
            MockHttpServletRequest request = spy(new MockHttpServletRequest("POST", path));
            request.setContentType("multipart/form-data; boundary=example");
            MockHttpServletResponse response = new MockHttpServletResponse();
            var chain = mock(jakarta.servlet.FilterChain.class);
            filter.doFilter(request,response,chain);
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentAsString()).contains("SCAN_CAPACITY_UNAVAILABLE");
            verify(request,never()).getInputStream();
            verify(request,never()).getParts();
            verifyNoInteractions(chain);
        }
        MockHttpServletRequest request = spy(new MockHttpServletRequest("PUT", "/api/v1/expense-scans"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        filter.doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(503);
        verify(request,never()).getInputStream();
        verifyNoInteractions(chain);
    }

    @Test
    void unauthenticatedRequestCannotPassTowardMultipartResolver() throws Exception {
        MockHttpServletRequest request = spy(new MockHttpServletRequest("POST", "/api/v1/expense-scans"));
        request.setContentType("multipart/form-data; boundary=example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        new DisabledExpenseScanPreBodyFilter(
                new ExpenseScanEntitlementGate(new DisabledPremiumEntitlementQuery(), Clock.systemUTC()),
                new ObjectMapper()).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTHENTICATION_FAILED");
        verify(request,never()).getInputStream();
        verify(request,never()).getParts();
        verifyNoInteractions(chain);
    }

    @Test
    void evenApprovedPaidEntitlementCannotOpenTheProductionUploadRoute() throws Exception {
        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(UUID.randomUUID().toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
        MockHttpServletRequest request = spy(new MockHttpServletRequest("POST", "/api/v1/expense-scans"));
        request.setContentType("multipart/form-data; boundary=example");
        request.addHeader("Idempotency-Key", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        var entitlement = new ExpenseScanEntitlementGate(ignored ->
                new PremiumEntitlementQuery.Entitlement(PremiumEntitlementQuery.Status.ACTIVE,
                        PremiumEntitlementQuery.BillingInterval.MONTHLY,
                        Clock.systemUTC().instant().plusSeconds(3600)), Clock.systemUTC());
        new DisabledExpenseScanPreBodyFilter(entitlement, new ObjectMapper())
                .doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("SCAN_CAPACITY_UNAVAILABLE");
        verify(request, never()).getInputStream();
        verify(request, never()).getParts();
        verifyNoInteractions(chain);
    }
}
