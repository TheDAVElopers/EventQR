package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.shared.constants.AccountRole;

class JwtServiceDenylistTest {

    private static final String SECRET = "01234567890123456789012345678901"; // 32 bytes (HS256 min)

    private static JwtService service(long expirationMs) {
        return new JwtService(SECRET, expirationMs);
    }

    private static String bearer(JwtService service) {
        return "Bearer " + service.createToken(UUID.randomUUID(), "user@example.com", AccountRole.ATTENDEE);
    }

    @Test
    void revokeMakesTokenRejected() {
        JwtService service = service(86400000L);
        String bearer = bearer(service);
        assertThat(service.isRevoked(bearer)).isFalse();
        service.revoke(bearer);
        assertThat(service.isRevoked(bearer)).isTrue();
    }

    @Test
    void revokingOneTokenDoesNotAffectOthers() {
        JwtService service = service(86400000L);
        String revoked = bearer(service);
        String other = bearer(service);
        service.revoke(revoked);
        assertThat(service.isRevoked(revoked)).isTrue();
        assertThat(service.isRevoked(other)).isFalse();
    }

    @Test
    void revokingAlreadyExpiredTokenIsNoOp() {
        // Negative expiration => every created token is already expired, so revocation
        // must not store it (TTL <= 0) and the token stays "not revoked".
        JwtService service = service(-1000L);
        String bearer = bearer(service);
        service.revoke(bearer);
        assertThat(service.isRevoked(bearer)).isFalse();
    }

    @Test
    void revokingInvalidOrMissingHeaderIsNoOp() {
        JwtService service = service(86400000L);
        service.revoke(null);
        service.revoke("Basic dXNlcjpwYXNz");
        service.revoke("Bearer   ");
        assertThat(service.isRevoked(null)).isFalse();
        assertThat(service.isRevoked("Basic dXNlcjpwYXNz")).isFalse();
        assertThat(service.isRevoked("Bearer   ")).isFalse();
    }
}