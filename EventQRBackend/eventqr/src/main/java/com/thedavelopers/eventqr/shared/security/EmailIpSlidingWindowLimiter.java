package com.thedavelopers.eventqr.shared.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;

import com.thedavelopers.eventqr.shared.utils.EmailNormalizer;

/**
 * Shared sliding-window limiter keyed by client IP and by canonical email (see {@link EmailNormalizer}, so
 * Gmail +tag/dot aliases share one budget). Both budgets must pass; a rejected request records nothing. The key is
 * the <em>submitted</em> email and no account lookup happens, so a rejection never reveals whether an account exists.
 *
 * <p>Each password-reset endpoint gets its own subclass instance so budgets are never shared between endpoints.
 * Subclasses may add further per-email rules through {@link #extraAllowed} / {@link #extraRecord}; those run inside
 * the same lock, so the whole decision (check both windows, check extras, record) is one atomic step.
 *
 * <p>State is in-memory and per instance: with N instances the effective limit multiplies by N. If the service
 * scales out, move this behind an interface backed by a shared store (e.g. Redis). Window arithmetic uses a
 * monotonic time source so wall-clock steps cannot affect the windows.
 */
public abstract class EmailIpSlidingWindowLimiter {

    private final Cache<String, Deque<Long>> byIp = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterAccess(Duration.ofMinutes(5)).build();
    private final Cache<String, Deque<Long>> byEmail = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterAccess(Duration.ofMinutes(5)).build();
    /** Held only for small in-memory work. */
    protected final Object lock = new Object();
    protected final LongSupplier monotonicMillis;
    private final int maxPerIp;
    private final int maxPerEmail;
    private final long windowMs;

    protected EmailIpSlidingWindowLimiter(LongSupplier monotonicMillis, int maxPerIp, int maxPerEmail, long windowMs) {
        this.monotonicMillis = monotonicMillis;
        this.maxPerIp = maxPerIp;
        this.maxPerEmail = maxPerEmail;
        this.windowMs = windowMs;
    }

    /** Returns true if the request is allowed (and records it), otherwise false (caller answers 429). */
    public boolean allow(HttpServletRequest request, String email) {
        String ip = ClientIp.from(request);
        String canonicalEmail = email == null ? "" : EmailNormalizer.canonicalize(email);
        synchronized (lock) {
            long now = monotonicMillis.getAsLong();
            if (!withinBudget(byIp, ip, maxPerIp, now)) {
                return false;
            }
            if (!canonicalEmail.isEmpty() && !withinBudget(byEmail, canonicalEmail, maxPerEmail, now)) {
                return false;
            }
            if (!canonicalEmail.isEmpty() && !extraAllowed(canonicalEmail, now)) {
                return false;
            }
            record(byIp, ip, now);
            if (!canonicalEmail.isEmpty()) {
                record(byEmail, canonicalEmail, now);
                extraRecord(canonicalEmail, now);
            }
            return true;
        }
    }

    /** Extra per-email rule; called under {@link #lock} after the standard budgets passed. */
    protected boolean extraAllowed(String canonicalEmail, long now) {
        return true;
    }

    /** Records an allowed request for the extra rule; called under {@link #lock}. */
    protected void extraRecord(String canonicalEmail, long now) {
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
        while (!deque.isEmpty() && deque.peekFirst() < now - windowMs) {
            deque.removeFirst();
        }
    }
}
