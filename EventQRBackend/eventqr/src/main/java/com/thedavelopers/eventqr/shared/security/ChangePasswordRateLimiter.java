package com.thedavelopers.eventqr.shared.security;

import java.util.UUID;
import java.util.function.LongSupplier;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Brute-force defense for {@code POST /api/v1/auth/change-password}: a stolen access token must not become an
 * oracle for the current password. Same reserve-then-refund sliding windows as {@link LoginRateLimiter} (15
 * minutes), keyed on the authenticated user id instead of an email:
 * <ol>
 *   <li>(user, IP) pair, default 5 wrong current passwords,</li>
 *   <li>user only, default 10,</li>
 *   <li>IP only, default 50.</li>
 * </ol>
 * The controller records an attempt before the check, clears the user's counters on success, keeps the slot only
 * when the current password was wrong and refunds it for any other failure. A full window answers 429 with
 * Retry-After, like login. State is in-memory and per instance.
 */
@Component
public class ChangePasswordRateLimiter {

    static final String BLOCKED_MESSAGE = "Too many password change attempts. Please try again later.";

    private final LoginRateLimiter delegate;

    @Autowired
    public ChangePasswordRateLimiter(@Value("${app.change-password-rate-limit.max-per-user:10}") int maxPerUser,
                                     @Value("${app.change-password-rate-limit.max-per-pair:5}") int maxPerPair,
                                     @Value("${app.change-password-rate-limit.max-per-ip:50}") int maxPerIp) {
        this(() -> System.nanoTime() / 1_000_000L, maxPerUser, maxPerPair, maxPerIp);
    }

    ChangePasswordRateLimiter(LongSupplier monotonicMillis, int maxPerUser, int maxPerPair, int maxPerIp) {
        this.delegate = new LoginRateLimiter(monotonicMillis, maxPerUser, maxPerPair, maxPerIp, BLOCKED_MESSAGE);
    }

    /** Reserves one attempt or throws {@code TooManyRequestsException} (429 + Retry-After) without recording it. */
    public LoginRateLimiter.Permit acquire(HttpServletRequest request, UUID userId) {
        return delegate.acquire(request, "user:" + userId);
    }

    /** The password was changed: clears the user and pair counters, refunds this slot in the IP window. */
    public void onSuccess(LoginRateLimiter.Permit permit) {
        delegate.onSuccess(permit);
    }

    /** The attempt failed for a reason other than a wrong current password: returns only this slot. */
    public void refund(LoginRateLimiter.Permit permit) {
        delegate.refund(permit);
    }
}
