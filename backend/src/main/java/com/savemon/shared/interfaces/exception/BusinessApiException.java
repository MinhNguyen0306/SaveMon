package com.savemon.shared.interfaces.exception;

import org.springframework.http.HttpStatus;

public final class BusinessApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
}
