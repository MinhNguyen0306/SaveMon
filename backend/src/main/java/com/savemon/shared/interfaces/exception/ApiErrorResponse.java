package com.savemon.shared.interfaces.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String code, String message, Map<String, String> fields, String traceId,
        UUID existingTransactionId) {
    public ApiErrorResponse(String code, String message, Map<String, String> fields, String traceId) {
        this(code, message, fields, traceId, null);
    }
}
