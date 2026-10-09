package com.savemon.identity.domain;

import java.util.Objects;

/** Validates password policy without changing the value that will be hashed. */
public record Password(String value) {
    public Password {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        int characterCount = value.codePointCount(0, value.length());
        if (characterCount < 8 || characterCount > 128) {
            throw new IllegalArgumentException("password length must be between 8 and 128 characters");
        }
    }
}
