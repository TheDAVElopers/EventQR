package com.thedavelopers.eventqr.features.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.auth.model.entity.RefreshToken;
import com.thedavelopers.eventqr.features.auth.repository.RefreshTokenRepository;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.security.AccessTokenRevoker;
import com.thedavelopers.eventqr.shared.security.UserTokenRevocationChecker;

/**
 * Issues and rotates single-use refresh tokens.
 *
 * <p>Presenting a token that was already used normally means it was stolen, so the whole
 * family is revoked. The one exception is a retry window ({@code jwt.refresh-retry-window}, default 60 s): a phone
 * that lost (or timed out waiting for) the response to a successful refresh will resend the same token, and that
 * must not log the user out. The window must exceed the app's network timeouts (30 s read, 45 s call), or a slow
 * but committed refresh (e.g. a cold start) would be retried after the window and revoke the whole family. Each rotation
 * records its successor ({@code replaced_by_hash}); a retry revokes that earlier successor before issuing a new one,
 * so a family never has more than one live branch. If the earlier successor was already used, the retry cannot be
 * a lost response, so it is treated as reuse.
 *
 * <p>Absolute lifetime: a family (one login) can be rotated for at most {@code jwt.refresh-absolute-lifetime-days}
 * (default 90) after the login, however often it is refreshed; after that the user must sign in again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** A used token presented again within this window is treated as a retry, not theft. */
    static final Duration DEFAULT_RETRY_WINDOW = Duration.ofSeconds(60);

    private static final long PURGE_INTERVAL_MS = 60L * 60L * 1000L;

    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository repository;
    private final UserTokenRevocationChecker accountAccessChecker;
    private final AccessTokenRevoker accessTokenRevoker;
    private final Duration lifetime;
    private final Duration absoluteLifetime;
    private final Duration retryWindow;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repository,
                               UserTokenRevocationChecker accountAccessChecker,
                               AccessTokenRevoker accessTokenRevoker,
                               @Value("${jwt.refresh-expiration-days:30}") long lifetimeDays,
                               @Value("${jwt.refresh-absolute-lifetime-days:90}") long absoluteLifetimeDays,
                               @Value("${jwt.refresh-retry-window:PT60S}") Duration retryWindow) {
        this.repository = repository;
        this.accountAccessChecker = accountAccessChecker;
        this.accessTokenRevoker = accessTokenRevoker;
        this.lifetime = Duration.ofDays(lifetimeDays);
        this.absoluteLifetime = Duration.ofDays(absoluteLifetimeDays);
        this.retryWindow = retryWindow == null || retryWindow.isNegative() ? DEFAULT_RETRY_WINDOW : retryWindow;
    }

    /** Default 90-day absolute lifetime and 60 s retry window; for tests. */
    RefreshTokenService(RefreshTokenRepository repository, UserTokenRevocationChecker accountAccessChecker,
                        AccessTokenRevoker accessTokenRevoker, long lifetimeDays) {
        this(repository, accountAccessChecker, accessTokenRevoker, lifetimeDays, 90, DEFAULT_RETRY_WINDOW);
    }

    /** Default 60 s retry window; for tests. */
    RefreshTokenService(RefreshTokenRepository repository, UserTokenRevocationChecker accountAccessChecker,
                        AccessTokenRevoker accessTokenRevoker, long lifetimeDays, long absoluteLifetimeDays) {
        this(repository, accountAccessChecker, accessTokenRevoker, lifetimeDays, absoluteLifetimeDays,
                DEFAULT_RETRY_WINDOW);
    }

    Duration retryWindow() {
        return retryWindow;
    }

    /** The outcome of a successful rotation. */
    public record Rotated(UUID userId, String refreshToken) {
    }

    /** Starts a new family for a fresh login and returns the raw refresh token. */
    @Transactional
    public String issueForLogin(UUID userId) {
        Instant now = Instant.now();
        return issue(userId, UUID.randomUUID(), now, now).raw();
    }

    /**
     * Starts a new family whose token is created strictly after {@code revokedAt}, so rotation's account gate
     * (created_at must be after the revocation marker) accepts it. Used for the session handed back by
     * change-password right after {@link #revokeAllForUser} moved the marker.
     */
    @Transactional
    public String issueForLoginAfter(UUID userId, Instant revokedAt) {
        Instant now = Instant.now();
        Instant floor = revokedAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plusMillis(1);
        Instant createdAt = now.isBefore(floor) ? floor : now;
        return issue(userId, UUID.randomUUID(), createdAt, createdAt).raw();
    }

    /**
     * Consumes {@code rawToken} and returns its successor. Throws {@link UnauthorizedException}
     * for unknown, expired, revoked or reused tokens. Reuse revokes the family, so this must
     * commit even though it throws.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotated rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Invalid or expired session");
        }
        Instant now = Instant.now();
        RefreshToken current = repository.findByTokenHash(sha256(rawToken.trim()))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired session"));
        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException("Invalid or expired session");
        }
        // Same gate as access tokens: a disabled or suspended account, or a token issued before
        // such a freeze, can no longer be exchanged.
        if (!accountAccessChecker.isAccessAllowed(current.getUserId(), current.getCreatedAt())) {
            throw new UnauthorizedException("Invalid or expired session");
        }
        Instant familyStart = current.getFamilyCreatedAt() != null ? current.getFamilyCreatedAt() : current.getCreatedAt();
        if (!familyStart.plus(absoluteLifetime).isAfter(now)) {
            throw new UnauthorizedException("Invalid or expired session");
        }
        if (current.getUsedAt() != null) {
            if (current.getUsedAt().plus(retryWindow).isBefore(now) || !revokeEarlierSuccessor(current, now)) {
                repository.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse detected for user {}; revoked its whole session family",
                        current.getUserId());
                throw new UnauthorizedException("Invalid or expired session");
            }
        } else {
            current.setUsedAt(now);
        }
        Issued successor = issue(current.getUserId(), current.getFamilyId(), now, familyStart);
        current.setReplacedByHash(successor.hash());
        return new Rotated(current.getUserId(), successor.raw());
    }

    /**
     * Grace retry: revokes the successor issued by the earlier rotation so only the one about to be issued stays
     * alive. Returns false when that successor was already used (it has descendants, so this is not a lost-response
     * retry). Rows rotated before V43 have no successor link; nothing can be revoked for them and the retry proceeds.
     */
    private boolean revokeEarlierSuccessor(RefreshToken current, Instant now) {
        if (current.getReplacedByHash() == null) {
            return true;
        }
        RefreshToken earlier = repository.findByTokenHash(current.getReplacedByHash()).orElse(null);
        if (earlier == null) {
            return true;
        }
        if (earlier.getUsedAt() != null) {
            return false;
        }
        if (earlier.getRevokedAt() == null) {
            earlier.setRevokedAt(now);
        }
        return true;
    }

    /** Ends the login that {@code rawToken} belongs to. Unknown tokens are ignored. */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(sha256(rawToken.trim()))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    /**
     * Ends every login for the user, e.g. after a password change or reset: revokes all refresh tokens and moves the
     * access-token revocation marker so every access token issued until now is rejected immediately (not only when
     * it expires). Returns the marker; tokens minted afterwards for the caller must be issued strictly after it
     * ({@link #issueForLoginAfter}, {@code JwtService.createTokenIssuedAfter}).
     */
    @Transactional
    public Instant revokeAllForUser(UUID userId) {
        Instant revokedAt = accessTokenRevoker.revokeAllIssuedUntilNow(userId);
        repository.revokeAllForUser(userId, Instant.now());
        return revokedAt;
    }

    @Scheduled(fixedDelay = PURGE_INTERVAL_MS, initialDelay = PURGE_INTERVAL_MS)
    @Transactional
    public void purgeExpired() {
        repository.deleteExpired(Instant.now());
    }

    private record Issued(String raw, String hash) {
    }

    /** Persists a new token; it never outlives the family's absolute lifetime. */
    private Issued issue(UUID userId, UUID familyId, Instant now, Instant familyCreatedAt) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256(raw);
        Instant expiresAt = now.plus(lifetime);
        Instant familyEnd = familyCreatedAt.plus(absoluteLifetime);
        if (familyEnd.isBefore(expiresAt)) {
            expiresAt = familyEnd;
        }
        repository.save(new RefreshToken(userId, familyId, hash, now, expiresAt, familyCreatedAt));
        return new Issued(raw, hash);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
