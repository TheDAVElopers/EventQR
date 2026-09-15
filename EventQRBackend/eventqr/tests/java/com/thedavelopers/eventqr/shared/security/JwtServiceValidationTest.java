package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtServiceValidationTest {

    @Test
    void nullSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(null, 86400000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret is not configured");
    }

    @Test
    void blankSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService("   ", 86400000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret is not configured");
    }

    @Test
    void shortSecretFailsFast() {
        // 8 characters => 8 bytes, well under the 32-byte HS256 minimum.
        assertThatThrownBy(() -> new JwtService("too-short", 86400000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void exactlyThirtyTwoBytesIsAccepted() {
        // 32 ASCII characters => 32 bytes: the HS256 minimum.
        new JwtService("01234567890123456789012345678901", 86400000L);
    }
}
