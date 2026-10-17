package com.thedavelopers.eventqr.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final int MAX_REVOKED_TOKENS = 25_000;
    private static final Duration NOT_REVOKED_TTL = Duration.ofSeconds(30);

    private final SecretKey secretKey;
    private final Duration expiration;

    /**
     * Revoked-token denylist keyed by the raw signed JWT string. Entries live exactly
     * as long as the token's own remaining lifetime (computed at revocation), so a
     * logged-out client cannot replay a token until natural expiry. Memory is bounded
     * by {@link #MAX_REVOKED_TOKENS} and each entry self-expires.
     *
     * <p>Note: account-disable/suspend revocation is DB-backed via
     * {@link UserTokenRevocationChecker}; per-user markers no longer live in this
     * class.
     */
    private final Cache<String, Long> revokedTokens = Caffeine.newBuilder()
            .maximumSize(MAX_REVOKED_TOKENS)
            .expireAfter(new Expiry<String, Long>() {
                @Override
                public long expireAfterCreate(String key, Long ttlMillis, long currentTime) {
                    return TimeUnit.MILLISECONDS.toNanos(ttlMillis);
                }

                @Override
                public long expireAfterUpdate(String key, Long value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(String key, Long value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    /**
     * Short-lived "known not revoked" markers so the per-request denylist check does not hit
     * the database every time. Bounds how long a logout made on another instance can be missed.
     */
    private final Cache<String, Boolean> notRevoked = Caffeine.newBuilder()
            .maximumSize(MAX_REVOKED_TOKENS)
            .expireAfterWrite(NOT_REVOKED_TTL)
            .build();

    /** Durable denylist; absent in plain unit tests, where only the in-memory cache is used. */
    private RevokedTokenStore revokedTokenStore;

    @Autowired(required = false)
    void setRevokedTokenStore(RevokedTokenStore revokedTokenStore) {
        this.revokedTokenStore = revokedTokenStore;
    }

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret is not configured: set the JWT_SECRET environment variable");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret is too weak: it must be at least 32 bytes (HS256), but was " + secretBytes.length + " bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = Duration.ofMillis(expirationMs);
    }

    /**
     * Private claim carrying the issue time in epoch milliseconds. The registered {@code iat} claim is whole
     * seconds, which is too coarse to tell a token minted just before a password change from one minted just after
     * it in the same second. Signed like every other claim, so clients cannot forge it.
     */
    static final String ISSUED_AT_MILLIS_CLAIM = "iatMs";

    public String createToken(UUID userId, String email, AccountRole role) {
        return createToken(userId, email, role, Instant.now());
    }

    /**
     * Mints a token whose issue time is strictly after {@code revokedAt}, so it survives a revocation marker written
     * in the same request (e.g. the fresh session handed back by change-password). Never earlier than now.
     */
    public String createTokenIssuedAfter(UUID userId, String email, AccountRole role, Instant revokedAt) {
        Instant now = Instant.now();
        Instant floor = revokedAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plusMillis(1);
        return createToken(userId, email, role, now.isBefore(floor) ? floor : now);
    }

    /**
     * Issue time of already-parsed claims, at millisecond precision when the token carries {@code iatMs}
     * (every token minted since it was introduced), otherwise the whole-second {@code iat}. Null if neither exists.
     */
    public Instant issuedAtOf(Claims claims) {
        Object millis = claims.get(ISSUED_AT_MILLIS_CLAIM);
        if (millis instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        Date issuedAt = claims.getIssuedAt();
        return issuedAt == null ? null : issuedAt.toInstant();
    }

    private String createToken(UUID userId, String email, AccountRole role, Instant issuedAt) {
        Instant now = issuedAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Instant expiresAt = now.plus(expiration);
        return Jwts.builder()
                // Unique per token: without it two logins in the same second yield the identical
                // string, so logging out and straight back in would hand back a revoked token.
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("userId", userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .claim(ISSUED_AT_MILLIS_CLAIM, now.toEpochMilli())
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Adds the bearer token to the denylist until its natural expiration. Invalid or
     * already-expired tokens are ignored (nothing to revoke, no-op for idempotency).
     */
    public void revoke(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        if (token == null) {
            return;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date expiresAt = claims.getExpiration();
            if (expiresAt == null) {
                return;
            }
            long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();
            if (ttlMillis > 0) {
                revokedTokens.put(token, ttlMillis);
                notRevoked.invalidate(token);
                if (revokedTokenStore != null) {
                    revokedTokenStore.save(sha256(token), expiresAt.toInstant());
                }
            }
        } catch (JwtException | IllegalArgumentException exception) {
            // Not a valid signed token: nothing to revoke.
        }
    }

    /** Returns true when the bearer token was revoked via {@link #revoke(String)}. */
    public boolean isRevoked(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        if (token == null) {
            return false;
        }
        if (revokedTokens.getIfPresent(token) != null) {
            return true;
        }
        if (revokedTokenStore == null || notRevoked.getIfPresent(token) != null) {
            return false;
        }
        Optional<Instant> expiresAt = revokedTokenStore.findActive(sha256(token));
        if (expiresAt.isPresent()) {
            // Revoked on another instance or before a restart: remember it locally.
            long ttlMillis = expiresAt.get().toEpochMilli() - System.currentTimeMillis();
            if (ttlMillis > 0) {
                revokedTokens.put(token, ttlMillis);
            }
            return true;
        }
        notRevoked.put(token, Boolean.TRUE);
        return false;
    }

    private static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * Parses and validates the bearer token, returning its claims. Invalid,
     * expired, or malformed headers throw {@link UnauthorizedException}, mirroring
     * the other extract/check helpers. Single parse point so filters only ever
     * parse a token once per request.
     */
    public Claims extractClaimsFromBearer(String authorizationHeader) {
        return extractClaims(authorizationHeader);
    }

    public UUID extractUserIdFromBearer(String authorizationHeader) {
        Claims claims = extractClaims(authorizationHeader);
        String userId = claims.get("userId", String.class);
        if (userId == null || userId.isBlank()) {
            userId = claims.getSubject();
        }
        return UUID.fromString(userId);
    }

    public AccountRole extractRoleFromBearer(String authorizationHeader) {
        return extractRoleFrom(extractClaims(authorizationHeader));
    }

    /** Role lookup from already-parsed claims; mirrors {@link #extractRoleFromBearer(String)}. */
    public AccountRole extractRoleFrom(Claims claims) {
        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            throw new UnauthorizedException("Invalid or expired session");
        }
        try {
            return AccountRole.valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Invalid or expired session");
        }
    }

    public String extractEmailFromBearer(String authorizationHeader) {
        Claims claims = extractClaims(authorizationHeader);
        String email = claims.get("email", String.class);
        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("Invalid or expired session");
        }
        return email;
    }

    private Claims extractClaims(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing session token");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new UnauthorizedException("Missing session token");
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("Invalid or expired session");
        }
    }

    /** Raw token extraction without parsing, so revocation checks never parse/throw. */
    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }
}

