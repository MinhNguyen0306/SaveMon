package com.savemon.identity.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailAddressTest {
    @Test
    void trimsAndLowercasesUsingRootLocale() {
        assertEquals("user@example.com", new EmailAddress("  User@Example.COM ").value());
    }
    @Test
    void rejectsNullOrBlankValue() {
        assertThrows(NullPointerException.class, () -> new EmailAddress(null));
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("  "));
    }
}
