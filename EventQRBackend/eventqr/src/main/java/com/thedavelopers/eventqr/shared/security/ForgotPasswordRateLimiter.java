package com.thedavelopers.eventqr.shared.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Endpoint-specific rate limiter for {@code POST /api/v1/auth/forgot-password}.
 *
 * <p>The global {@link RateLimitFilter} allows 200 requests per 10s per IP, which is far too permissive for a
 * password-reset endpoint and allows email bombing / Brevo quota exhaustion. On top of the shared per-IP and
 * per-canonical-email 60s windows (see {@link EmailIpSlidingWindowLimiter}) this limiter governs code
 * <em>issuance</em> (resend) per canonical email:
 * <ul>
 *   <li>a minimum interval between two accepted requests (default 30s), and</li>
 *   <li>a cap of accepted requests per hour (default 5).</li>
 * </ul>
 * Both are keyed on the submitted email, never on account existence, so a 429 reveals nothing about the account.
 * Canonicalisation means alias spellings landing in one inbox share the budget.
 */
@Component
public class ForgotPasswordRateLimiter extends EmailIpSlidingWindowLimiter {

    static final int DEFAULT_MAX_PER_IP = 5;
    static final int DEFAULT_MAX_PER_EMAIL = 5;
    static final long DEFAULT_MIN_INTERVAL_SECONDS = 30;
    static final int DEFAULT_MAX_PER_HOUR = 5;
    private static final long WINDOW_MS = 60_000L;
    private static final long HOUR_MS = Duration.ofHours(1).toMillis();

    private final long minIntervalMs;
    private final int maxPerHour;
    private final Cache<String, Deque<Long>> issuedByEmail = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterAccess(Duration.ofHours(2)).build();

    @Autowired
    public ForgotPasswordRateLimiter(
            @Value("${app.forgot-password-rate-limit.max-per-ip:5}") int maxPerIp,
            @Value("${app.forgot-password-rate-limit.max-per-email:5}") int maxPerEmail,
            @Value("${app.forgot-password-rate-limit.min-interval-seconds:30}") long minIntervalSeconds,
            @Value("${app.forgot-password-rate-limit.max-per-hour:5}") int maxPerHour) {
        this(() -> System.nanoTime() / 1_000_000L, maxPerIp, maxPerEmail, minIntervalSeconds, maxPerHour);
    }

    /** Package-private constructor for tests that need time control. */
    ForgotPasswordRateLimiter(LongSupplier monotonicMillis) {
        this(monotonicMillis, DEFAULT_MAX_PER_IP, DEFAULT_MAX_PER_EMAIL, DEFAULT_MIN_INTERVAL_SECONDS,
                DEFAULT_MAX_PER_HOUR);
    }

    ForgotPasswordRateLimiter(LongSupplier monotonicMillis, int maxPerIp, int maxPerEmail,
                              long minIntervalSeconds, int maxPerHour) {
        super(monotonicMillis, maxPerIp, maxPerEmail, WINDOW_MS);
        this.minIntervalMs = Duration.ofSeconds(minIntervalSeconds).toMillis();
        this.maxPerHour = maxPerHour;
    }

    @Override
    protected boolean extraAllowed(String canonicalEmail, long now) {
        Deque<Long> issued = issuedByEmail.get(canonicalEmail, k -> new ArrayDeque<>());
        while (!issued.isEmpty() && issued.peekFirst() < now - HOUR_MS) {
            issued.removeFirst();
        }
        if (issued.size() >= maxPerHour) {
            return false;
        }
        return issued.isEmpty() || now - issued.peekLast() >= minIntervalMs;
    }

    @Override
    protected void extraRecord(String canonicalEmail, long now) {
        issuedByEmail.get(canonicalEmail, k -> new ArrayDeque<>()).addLast(now);
    }
}
