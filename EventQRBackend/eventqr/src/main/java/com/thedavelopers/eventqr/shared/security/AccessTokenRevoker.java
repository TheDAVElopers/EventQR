package com.thedavelopers.eventqr.shared.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;

/**
 * Kills every access token a user holds by moving their {@code user_token_revocation} marker to now; the JWT filter
 * (via {@link UserTokenRevocationChecker}) rejects any token issued at or before the marker, and refresh-token
 * rotation applies the same gate to refresh tokens. Used when the password changes or is reset.
 *
 * <p>Precision: the marker is truncated to milliseconds, matching the {@code iatMs} claim, so it compares exactly.
 * Tokens minted later in the same request must be issued strictly after the returned marker
 * ({@link JwtService#createTokenIssuedAfter}); otherwise a token minted in the same millisecond would be rejected.
 *
 * <p>The marker only ever moves forward, so it never re-validates tokens killed by an earlier disable/suspend.
 */
@Component
public class AccessTokenRevoker {

    private final UserTokenRevocationRepository userTokenRevocationRepository;
    private final UserTokenRevocationChecker userTokenRevocationChecker;

    public AccessTokenRevoker(UserTokenRevocationRepository userTokenRevocationRepository,
                              UserTokenRevocationChecker userTokenRevocationChecker) {
        this.userTokenRevocationRepository = userTokenRevocationRepository;
        this.userTokenRevocationChecker = userTokenRevocationChecker;
    }

    /**
     * Revokes every token issued up to now and returns the effective marker. The write is a single race-safe
     * {@code INSERT ... ON CONFLICT DO UPDATE SET revoked_at = GREATEST(...)}. This instance's cached access decision
     * is dropped once the transaction commits (immediately when there is none).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Instant revokeAllIssuedUntilNow(UUID userId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        long effectiveMillis = userTokenRevocationRepository.upsertNotBackwards(userId, now);
        userTokenRevocationChecker.invalidateAfterCommit(userId);
        return Instant.ofEpochMilli(effectiveMillis);
    }
}
