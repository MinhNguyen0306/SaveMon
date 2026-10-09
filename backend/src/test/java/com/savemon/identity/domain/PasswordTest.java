package com.savemon.identity.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordTest {
    @Test void acceptsLengthBoundariesAndPreservesExactPassword() {
        String exact = "  123456  ";
        assertEquals(exact, new Password(exact).value());
        assertDoesNotThrow(() -> new Password("a".repeat(8)));
        assertDoesNotThrow(() -> new Password("a".repeat(128)));
    }
    @Test void rejectsBlankAndOutOfRangePasswords() {
        assertThrows(IllegalArgumentException.class, () -> new Password("        "));
        assertThrows(IllegalArgumentException.class, () -> new Password("short"));
        assertThrows(IllegalArgumentException.class, () -> new Password("a".repeat(129)));
    }
}
