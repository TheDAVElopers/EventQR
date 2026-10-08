package com.thedavelopers.eventqr.shared.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.time.Duration;
import java.util.function.LongSupplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;


/**
 * Rate limiter for event registration ({@code POST /api/v1/registrations} and
 * {@code POST /api/v1/events/{id}/registrations}); both go through
 * {@code RegistrationService#registerAs}, so they share this budget.
 *
 * <p>Registration requires an authenticated caller (any role), but a valid account is cheap to obtain, so the global
 * {@link RateLimitFilter} budget (200 requests per 10s per IP) alone lets an attacker
 * bulk-register and trigger QR-email spam. This limiter applies a much tighter sliding-window budget keyed by
 * the proxy-aware client IP and by the authenticated <em>caller's user id</em>.
 *
 * <p>The key is deliberately the caller, never the email in the request body: a non-admin may only register their
 * own email, and the service runs that ownership check <em>before</em> calling {@link #allow}, so a request naming
 * someone else's email is refused with 403 without touching any bucket. Keying on the body email would let an attacker
 * exhaust a victim's budget (Gmail dot/plus aliases collapse onto one inbox).
 *
 * <p>Both limits must pass; a request is rejected as soon as either budget is exhausted.
 *
 * <p>State is in-memory and per instance: with N instances the effective limit multiplies by N. If the
 * service scales out, move this behind an interface backed by a shared store (e.g. Redis). Window
 * arithmetic uses a monotonic time source so wall-clock steps cannot affect the windows.
 */
@Component
public class RegistrationRateLimiter {

    private static final int MAX_PER_IP = 10;
    private static final int MAX_PER_CALLER = 10;
    private static final long WINDOW_MS = 60_000L;

    private final Cache<String, Deque<Long>> byIp = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterAccess(Duration.ofMinutes(5)).build();
    private final Cache<String, Deque<Long>> byCaller = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterAccess(Duration.ofMinutes(5)).build();
    /** Makes prune/check/record across both windows one atomic step; held only for small in-memory work. */
    private final Object lock = new Object();
    private final LongSupplier monotonicMillis;

    public RegistrationRateLimiter() {
        this(() -> System.nanoTime() / 1_000_000L);
    }

    /** Package-private constructor for tests that need time control. */
    RegistrationRateLimiter(LongSupplier monotonicMillis) {
        this.monotonicMillis = monotonicMillis;
    }

    /**
     * Returns true if the request should be allowed, otherwise false (caller returns 429).
     * Records the attempt under both keys only when both budgets have room.
     *
     * @param clientIp     resolved client IP (see {@link ClientIp#from})
     * @param callerUserId the authenticated caller; a null id is bucketed with IP only
     */
    public boolean allow(String clientIp, UUID callerUserId) {
        String canonical = clientIp == null ? null : ClientIp.canonicalize(clientIp.trim());
        String ip = canonical != null ? canonical : ClientIp.UNKNOWN;
        String caller = callerUserId == null ? null : callerUserId.toString();
        synchronized (lock) {
            long now = monotonicMillis.getAsLong();
            if (!withinBudget(byIp, ip, MAX_PER_IP, now)) {
                return false;
            }
            if (caller != null && !withinBudget(byCaller, caller, MAX_PER_CALLER, now)) {
                return false;
            }
            record(byIp, ip, now);
            if (caller != null) {
                record(byCaller, caller, now);
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
