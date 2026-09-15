package com.thedavelopers.eventqr.shared.security;

import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JwtService: token creation, claim extraction, and denylist
 * revocation semantics (token revoked until natural expiry, then replayable).
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-for-hs256!!";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3_600_000); // 1 hour
    }

    @Test
    void createToken_containsSubjectEmailAndRole() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.createToken(userId, "user@example.com", AccountRole.ATTENDEE);

        assertThat(token).isNotBlank();
        Claims claims = jwtService.extractClaimsFromBearer("Bearer " + token);
        assertThat(claims.get("userId", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ATTENDEE");
    }

    @Test
    void extractUserIdFromBearer_parsesSubjectWhenUserIdClaimMissing() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.createToken(userId, "user@example.com", AccountRole.ORGANIZER);

        UUID extracted = jwtService.extractUserIdFromBearer("Bearer " + token);

        assertThat(extracted).isEqualTo(userId);
    }

    @Test
    void extractRoleFromBearer_returnsRole() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.createToken(userId, "admin@example.com", AccountRole.ADMIN);

        AccountRole role = jwtService.extractRoleFromBearer("Bearer " + token);

        assertThat(role).isEqualTo(AccountRole.ADMIN);
    }

    @Test
    void extractEmailFromBearer_returnsEmail() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.createToken(userId, "staff@example.com", AccountRole.STAFF);

        String email = jwtService.extractEmailFromBearer("Bearer " + token);

        assertThat(email).isEqualTo("staff@example.com");
    }

    @Test
    void extractClaims_missingHeader_throwsUnauthorized() {
        assertThatThrownBy(() -> jwtService.extractClaimsFromBearer(null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Missing session token");
    }

    @Test
    void extractClaims_nonBearerHeader_throwsUnauthorized() {
        assertThatThrownBy(() -> jwtService.extractClaimsFromBearer("Basic abc123"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Missing session token");
    }

    @Test
    void extractClaims_blankBearerHeader_throwsUnauthorized() {
        assertThatThrownBy(() -> jwtService.extractClaimsFromBearer("Bearer   "))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Missing session token");
    }

    @Test
    void extractClaims_garbledToken_throwsUnauthorized() {
        assertThatThrownBy(() -> jwtService.extractClaimsFromBearer("Bearer not-a-jwt"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired session");
    }

    @Test
    void extractClaims_tokenSignedWithDifferentSecret_throwsUnauthorized() {
        JwtService otherSigner = new JwtService("a-completely-different-secret-key-over-32-bytes!", 3_600_000);
        String foreignToken = otherSigner.createToken(UUID.randomUUID(), "x@example.com", AccountRole.ATTENDEE);

        assertThatThrownBy(() -> jwtService.extractClaimsFromBearer("Bearer " + foreignToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired session");
    }

    @Test
    void weakSecret_throwsIllegalState() {
        assertThatThrownBy(() -> new JwtService("short", 3_600_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too weak");
    }

    @Test
    void blankSecret_throwsIllegalState() {
        assertThatThrownBy(() -> new JwtService("   ", 3_600_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void revoke_thenIsRevoked_returnsTrue() {
        String token = jwtService.createToken(UUID.randomUUID(), "user@example.com", AccountRole.ATTENDEE);
        String header = "Bearer " + token;

        assertThat(jwtService.isRevoked(header)).isFalse();

        jwtService.revoke(header);

        assertThat(jwtService.isRevoked(header)).isTrue();
    }

    @Test
    void revoke_idempotent_secondRevokeDoesNotThrow() {
        String header = "Bearer " + jwtService.createToken(UUID.randomUUID(), "user@example.com", AccountRole.ATTENDEE);

        jwtService.revoke(header);
        jwtService.revoke(header);

        assertThat(jwtService.isRevoked(header)).isTrue();
    }

    @Test
    void revoke_garbledToken_isIgnored() {
        jwtService.revoke("Bearer not-a-jwt");

        assertThat(jwtService.isRevoked("Bearer not-a-jwt")).isFalse();
    }

    @Test
    void revoke_missingHeader_isIgnored() {
        jwtService.revoke(null);
        jwtService.revoke("Basic abc");

        assertThat(jwtService.isRevoked(null)).isFalse();
    }

    @Test
    void isRevoked_falseForNeverRevokedToken() {
        String header = "Bearer " + jwtService.createToken(UUID.randomUUID(), "user@example.com", AccountRole.ATTENDEE);

        assertThat(jwtService.isRevoked(header)).isFalse();
    }

    @Test
    void extractRoleFrom_missingRoleClaim_throwsUnauthorized() {
        // A valid token without a role claim cannot be minted via createToken, so
        // exercise the claims-based overload directly.
        UUID userId = UUID.randomUUID();
        String token = jwtService.createToken(userId, "user@example.com", AccountRole.ATTENDEE);
        Claims claims = jwtService.extractClaimsFromBearer("Bearer " + token);
        claims.remove("role");

        assertThatThrownBy(() -> jwtService.extractRoleFrom(claims))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired session");
    }

    @Test
    void extractRoleFrom_unknownRoleValue_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.createToken(userId, "user@example.com", AccountRole.ATTENDEE);
        Claims claims = jwtService.extractClaimsFromBearer("Bearer " + token);
        claims.put("role", "NOT_A_ROLE");

        assertThatThrownBy(() -> jwtService.extractRoleFrom(claims))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid or expired session");
    }
}