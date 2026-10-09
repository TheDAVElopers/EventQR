package com.thedavelopers.eventqr.shared.security;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-IP and per-canonical-email limiter (default 10 requests per 60s each) for
 * {@code POST /api/v1/auth/reset-password}. Its own budget, independent of {@link ResetCodeVerifyRateLimiter} and
 * {@link ForgotPasswordRateLimiter}.
 */
@Component
public class ResetPasswordRateLimiter extends EmailIpSlidingWindowLimiter {

    static final int DEFAULT_MAX = 10;
    static final long WINDOW_MS = 60_000L;

    @Autowired
    public ResetPasswordRateLimiter(
            @Value("${app.reset-password-rate-limit.max-per-ip:10}") int maxPerIp,
            @Value("${app.reset-password-rate-limit.max-per-email:10}") int maxPerEmail) {
        super(() -> System.nanoTime() / 1_000_000L, maxPerIp, maxPerEmail, WINDOW_MS);
    }

    /** Package-private constructor for tests that need time control. */
    ResetPasswordRateLimiter(LongSupplier monotonicMillis) {
        super(monotonicMillis, DEFAULT_MAX, DEFAULT_MAX, WINDOW_MS);
    }
}
