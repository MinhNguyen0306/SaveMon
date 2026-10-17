package com.savemon.identity.infrastructure.security;

import com.savemon.identity.application.SubjectStatusQuery;
import com.savemon.shared.interfaces.exception.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rechecks the authoritative identity state after JWT authentication, before application entry. */
public final class ActiveSubjectFilter extends OncePerRequestFilter {
    private final SubjectStatusQuery subjects;
    private final SecurityErrorResponseWriter errors;

    public ActiveSubjectFilter(SubjectStatusQuery subjects, SecurityErrorResponseWriter errors) {
        this.subjects = subjects;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isPublicEntry(request)) {
            chain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        if (!subjects.isActive(UUID.fromString(jwt.getName()))) {
            SecurityContextHolder.clearContext();
            errors.commence(request, response,
                    new InsufficientAuthenticationException("Subject is not active."));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isPublicEntry(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (HttpMethod.GET.matches(request.getMethod()) && "/actuator/health".equals(path)) return true;
        return HttpMethod.POST.matches(request.getMethod()) && switch (path) {
            case "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                    "/api/v1/auth/logout" -> true;
            default -> false;
        };
    }
}
