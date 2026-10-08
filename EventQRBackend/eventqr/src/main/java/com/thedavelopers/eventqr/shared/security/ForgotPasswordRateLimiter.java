package com.thedavelopers.eventqr.shared.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.time.Duration;
import java.util.function.LongSupplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.shared.utils.EmailNormalizer;

/**
 * Endpoint-specific rate limiter for {@code POST /api/v1/auth/forgot-password}.
 *
 * <p>The global {@link RateLimitFilter} allows 200 requests per 10s per IP, which is far
 * too permissive for a password-reset endpoint and allows email bombing / Brevo quota
 * exhaustion. This limiter deliberately operates separately from the global filter and
 * applies much tighter sliding-window budgets keyed by both the proxied-aware client IP
 * and the canonicalized email address being addressed, so abuse is throttled per source
 * attacker and per victim. The email key is canonicalized (see {@link EmailNormalizer}) so
 * Gmail {@code +tag}/dot aliases that land in the same inbox share one per-recipient
 * budget and cannot be used to bomb the victim with many distinct keys.
 *
 * <p>Both limits must pass; a request is rejected as soon as either budget is exhausted.
 * State is in-memory and suitable for the single-instance deployment only. *
 * <p>State is in-memory and per instance: with N instances the effective limit multiplies by N. If the
 * service scales out, move this behind an interface backed by a shared store (e.g. Redis). Window
 * arithmetic uses a monotonic time source so wall-clock steps cannot affect the windows.
 */
@Component
public class ForgotPasswordRateLimiter {

    private static final int MAX_PER_IP = 5;
    private static final int MAX_PER_EMAIL = 5;
    private static final long WINDOW_MS = 60_000L;

    private final Cache<String, Deque<Long>> byIp = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterAccess(Duration.ofMinutes(5)).build();
    private final Cache<String, Deque<Long>> byEmail = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterAccess(Duration.ofMinutes(5)).build();
    /** Makes prune/check/record across both windows one atomic step; held only for small in-memory work. */
    private final Object lock = new Object();
    private final LongSupplier monotonicMillis;

    public ForgotPasswordRateLimiter() {
        this(() -> System.nanoTime() / 1_000_000L);
    }

    /** Package-private constructor for tests that need time control. */
    ForgotPasswordRateLimiter(LongSupplier monotonicMillis) {
        this.monotonicMillis = monotonicMillis;
    }

    /**
     * Returns true if the request should be allowed, otherwise false (caller returns 429).
     * Records the attempt under both keys when allowed relative to each individual budget.
     */
    public boolean allow(HttpServletRequest request, String email) {
        String ip = ClientIp.from(request);
        // Use canonical email form so Gmail +tag/dot aliases that land in the same inbox
        // share one per-recipient budget (prevents alias-based email bombing). Must match
        // the canonical form used by PasswordResetService for the actual recipient.
        String canonicalEmail = email == null ? "" : EmailNormalizer.canonicalize(email);
        synchronized (lock) {
            long now = monotonicMillis.getAsLong();
            if (!withinBudget(byIp, ip, MAX_PER_IP, now)) {
                return false;
            }
            if (!canonicalEmail.isEmpty() && !withinBudget(byEmail, canonicalEmail, MAX_PER_EMAIL, now)) {
                return false;
            }
            record(byIp, ip, now);
            if (!canonicalEmail.isEmpty()) {
                record(byEmail, canonicalEmail, now);
            }
            return true;
        }
    }

    private boolean withinBudget(Cache<String, Deque<Long>> window, String key, int max, long now) {
        Deque<Long> deque = window.get(key, k -> new ArrayDeque<>());
        prune(deque, now);
        return deque.size() < max;
    }

    private void record(Cache<String, Deque<Long>> window, String key, long now) {
        window.get(key, k -> new ArrayDeque<>()).addLast(now);
    }

    private void prune(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && deque.peekFirst() < now - WINDOW_MS) {
            deque.removeFirst();
        }
    }
}
