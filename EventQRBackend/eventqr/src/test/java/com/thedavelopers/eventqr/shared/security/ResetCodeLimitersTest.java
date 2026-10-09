package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.function.LongSupplier;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The verify and reset limiters: 10/min per IP and per canonical email, each with its own independent budget. */
@ExtendWith(MockitoExtension.class)
class ResetCodeLimitersTest {

    @Mock
    private HttpServletRequest request;

    private long now;
    private final LongSupplier clock = () -> now;

    @BeforeEach
    void setUp() {
        now = 1_000_000L;
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9");
        lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
    }

    @Test
    void verifyLimiterAllowsTenPerEmailThenRejectsAndRecovers() {
        var limiter = new ResetCodeVerifyRateLimiter(clock);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allow(request, "victim@example.com")).isTrue();
        }
        assertThat(limiter.allow(request, "victim@example.com")).isFalse();
        now += 61_000L;
        assertThat(limiter.allow(request, "victim@example.com")).isTrue();
    }

    @Test
    void verifyLimiterCapsPerIpAcrossDifferentEmails() {
        var limiter = new ResetCodeVerifyRateLimiter(clock);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allow(request, "v" + i + "@example.com")).isTrue();
        }
        assertThat(limiter.allow(request, "another@example.com")).isFalse();
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.5");
        assertThat(limiter.allow(request, "another@example.com")).isTrue();
    }

    @Test
    void resetLimiterAllowsTenPerEmailThenRejects() {
        var limiter = new ResetPasswordRateLimiter(clock);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allow(request, "victim@example.com")).isTrue();
        }
        assertThat(limiter.allow(request, "victim@example.com")).isFalse();
    }

    @Test
    void canonicalAliasesShareOneEmailBudget() {
        var limiter = new ResetCodeVerifyRateLimiter(clock);
        for (int i = 0; i < 10; i++) {
            when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100." + i);
            assertThat(limiter.allow(request, "user+" + i + "@gmail.com")).isTrue();
        }
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.200");
        assertThat(limiter.allow(request, "u.s.e.r@gmail.com")).isFalse();
    }

    @Test
    void verifyResetAndForgotBudgetsAreIndependent() {
        var verify = new ResetCodeVerifyRateLimiter(clock);
        var reset = new ResetPasswordRateLimiter(clock);
        var forgot = new ForgotPasswordRateLimiter(clock);
        for (int i = 0; i < 10; i++) {
            verify.allow(request, "victim@example.com");
        }
        assertThat(verify.allow(request, "victim@example.com")).isFalse();
        assertThat(reset.allow(request, "victim@example.com")).isTrue();
        assertThat(forgot.allow(request, "victim@example.com")).isTrue();
    }

    @Test
    void configuredLimitsAreHonoured() {
        var limiter = new ResetCodeVerifyRateLimiter(2, 2);
        assertThat(limiter.allow(request, "a@example.com")).isTrue();
        assertThat(limiter.allow(request, "a@example.com")).isTrue();
        assertThat(limiter.allow(request, "a@example.com")).isFalse();
    }
}
