package com.thedavelopers.eventqr.shared.security;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-IP and per-canonical-email limiter (default 10 requests per 60s each) for
 * {@code POST /api/v1/auth/reset-password/verify}. Its own budget, independent of
 * {@link ResetPasswordRateLimiter} and {@link ForgotPasswordRateLimiter}. This is a request-rate bound layered on
 * top of the per-code 5-wrong-guess lockout in {@code PasswordResetService}.
 */
@Component
public class ResetCodeVerifyRateLimiter extends EmailIpSlidingWindowLimiter {

    static final int DEFAULT_MAX = 10;
    static final long WINDOW_MS = 60_000L;

    @Autowired
    public ResetCodeVerifyRateLimiter(
            @Value("${app.reset-verify-rate-limit.max-per-ip:10}") int maxPerIp,
            @Value("${app.reset-verify-rate-limit.max-per-email:10}") int maxPerEmail) {
        super(() -> System.nanoTime() / 1_000_000L, maxPerIp, maxPerEmail, WINDOW_MS);
    }

    /** Package-private constructor for tests that need time control. */
    ResetCodeVerifyRateLimiter(LongSupplier monotonicMillis) {
        super(monotonicMillis, DEFAULT_MAX, DEFAULT_MAX, WINDOW_MS);
    }
}
