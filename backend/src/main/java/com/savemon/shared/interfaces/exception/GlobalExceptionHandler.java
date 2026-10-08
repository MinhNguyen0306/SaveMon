package com.savemon.shared.interfaces.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
