package com.savemon.shared.interfaces.exception;

import com.savemon.identity.application.AuthenticationFailedException;
import com.savemon.identity.application.IdentityConflictException;
import com.savemon.identity.application.TokenIssuerUnavailableException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String VALIDATION_MESSAGE = "Request validation failed.";

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindingException(BindException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            addFieldError(fields, error.getField(), error.getDefaultMessage());
        }
        return validationError(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            addFieldError(fields, violation.getPropertyPath().toString(), violation.getMessage());
        }
        return validationError(fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return validationError(Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse("ACCESS_DENIED", "Access denied.", null, MDC.get("traceId")));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = status == HttpStatus.UNAUTHORIZED ? "UNAUTHENTICATED"
                : status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : status.name();
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, null, MDC.get("traceId")));
    }

    @ExceptionHandler(BusinessApiException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessError(BusinessApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiErrorResponse(exception.code(), exception.getMessage(), null, MDC.get("traceId")));
    }

    @ExceptionHandler(IdentityConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdentityConflict(IdentityConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("IDENTITY_CONFLICT", "Identity already exists.", null, MDC.get("traceId")));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationFailure(AuthenticationFailedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("AUTHENTICATION_FAILED", "Authentication failed.", null, MDC.get("traceId")));
    }

    @ExceptionHandler(TokenIssuerUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenIssuerUnavailable(TokenIssuerUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("TOKEN_ISSUER_UNAVAILABLE", "Token issuer unavailable.", null, MDC.get("traceId")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        String traceId = MDC.get("traceId");
        LOG.error("Unhandled request failure [traceId={}]", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred.",
                        null,
                        traceId));
    }

    private ResponseEntity<ApiErrorResponse> validationError(Map<String, String> fields) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(VALIDATION_ERROR, VALIDATION_MESSAGE, fields, MDC.get("traceId")));
    }

    private void addFieldError(Map<String, String> fields, String name, String message) {
        String safeMessage = message == null ? "Invalid value." : message;
        fields.merge(name, safeMessage, (first, next) -> first.equals(next) ? first : first + "; " + next);
    }
}
