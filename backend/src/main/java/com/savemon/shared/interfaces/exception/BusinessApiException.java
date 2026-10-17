package com.savemon.shared.interfaces.exception;

import org.springframework.http.HttpStatus;
import java.util.UUID;

public final class BusinessApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final UUID existingTransactionId;

    public BusinessApiException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public BusinessApiException(String code, String message, HttpStatus status, UUID existingTransactionId) {
        super(message);
        this.code = code;
        this.status = status;
        this.existingTransactionId = existingTransactionId;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public UUID existingTransactionId() { return existingTransactionId; }
}
