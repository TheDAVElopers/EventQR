package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordRateLimiterTest {

    @Mock
    private HttpServletRequest request;

    private TestMillis clock;
    private ForgotPasswordRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new TestMillis();
        // Relaxed resend rules: the legacy window tests below exercise only the per-IP / per-email windows.
        limiter = new ForgotPasswordRateLimiter(clock, 5, 5, 0, 1000);
        // getRemoteAddr() is only used on the fallback path, so stub leniently.
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9");
        lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
    }

    /** Distinct IPs and 31s spacing isolate the rule under test from the per-IP / min-interval rules. */
    private boolean resendFrom(String ip, String email) {
        when(request.getHeader("X-Forwarded-For")).thenReturn(ip);
        return limiter.allow(request, email);
    }

    @Test
    void minimumIntervalBlocksAResendWithin30SecondsAndAllowsItAfter() {
        limiter = new ForgotPasswordRateLimiter(clock);
        assertThat(limiter.allow(request, "victim@example.com")).isTrue();
        clock.advance(29_000L);
        assertThat(limiter.allow(request, "victim@example.com")).isFalse();
        clock.advance(1_500L);
        assertThat(limiter.allow(request, "victim@example.com")).isTrue();
    }

    @Test
    void aRejectedResendDoesNotExtendTheMinimumInterval() {
        limiter = new ForgotPasswordRateLimiter(clock);
        assertThat(limiter.allow(request, "victim@example.com")).isTrue();
        clock.advance(20_000L);
        assertThat(limiter.allow(request, "victim@example.com")).isFalse();
        clock.advance(11_000L); // 31s after the last accepted request
        assertThat(limiter.allow(request, "victim@example.com")).isTrue();
    }

    @Test
    void minimumIntervalIsKeyedOnTheCanonicalSubmittedEmail() {
        limiter = new ForgotPasswordRateLimiter(clock);
        assertThat(limiter.allow(request, "user+a@gmail.com")).isTrue();
        assertThat(limiter.allow(request, "u.s.e.r+b@gmail.com")).isFalse();
        // A different mailbox is unaffected.
        assertThat(resendFrom("198.51.100.1", "other@example.com")).isTrue();
    }

    @Test
    void hourlyCapAllowsFivePerHourThenRejectsUntilTheHourRolls() {
        limiter = new ForgotPasswordRateLimiter(clock);
        // 5 accepted codes spaced 31s apart (stay inside the hour).
        for (int i = 0; i < 5; i++) {
            assertThat(resendFrom("198.51.100." + i, "victim@example.com")).isTrue();
            clock.advance(31_000L);
        }
        // The per-email 60s window has long since passed for the first ones; the hourly cap now blocks.
        clock.advance(60_000L);
        assertThat(resendFrom("198.51.100.99", "victim@example.com")).isFalse();
        // Another mailbox is unaffected.
        assertThat(resendFrom("198.51.100.98", "someone@example.com")).isTrue();
        // After the first issuance leaves the 1h window, one more is allowed.
        clock.advance(3_600_000L);
        assertThat(resendFrom("198.51.100.97", "victim@example.com")).isTrue();
    }

    @Test
    void configuredLimitsAreHonoured() {
        limiter = new ForgotPasswordRateLimiter(clock, 100, 100, 5, 2);
        assertThat(limiter.allow(request, "v@example.com")).isTrue();
        clock.advance(6_000L);
        assertThat(limiter.allow(request, "v@example.com")).isTrue();
        clock.advance(6_000L);
        assertThat(limiter.allow(request, "v@example.com")).isFalse();
    }

    @Test
    void burstAllowsFiveThenRejectsSixthWithinWindow() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allow(request, "victim@example.com")).isTrue();
        }
        assertThat(limiter.allow(request, "victim@example.com")).isFalse();
    }

    @Test
    void cooldownAfterWindowAllowsAgain() {
        for (int i = 0; i < 5; i++) {
            limiter.allow(request, "victim@example.com");
        }
        assertThat(limiter.allow(request, "victim@example.com")).isFalse();

        // Advance past the 60s window; the same IP/email should be allowed again.
        clock.advance(61_000L);
        assertThat(limiter.allow(request, "victim@example.com")).isTrue();
    }

    @Test
    void perIpAndPerEmailBudgetsAreIndependent() {
        // Five distinct emails from the same IP exhaust only the per-IP budget.
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allow(request, "victim" + i + "@example.com")).isTrue();
        }
        // 6th request from the same IP (any email) is blocked by the per-IP budget.
        assertThat(limiter.allow(request, "victim99@example.com")).isFalse();
    }

    @Test
    void differentIpHasIndependentBudget() {
        for (int i = 0; i < 5; i++) {
            limiter.allow(request, "victim@example.com");
        }
        assertThat(limiter.allow(request, "victim@example.com")).isFalse();

        // Another attacker with a different IP and a fresh victim email is allowed.
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.77");
        assertThat(limiter.allow(request, "other-victim@example.com")).isTrue();
    }

    @Test
    void gmailAliasesShareOnePerEmailBudget() {
        // user+a@gmail.com and user.b+c@gmail.com both canonicalize to user@gmail.com.
        String[] aliases = { "user+a@gmail.com", "user.b+c@gmail.com", "user+different@gmail.com",
                "u.s.e.r@gmail.com", "user+last@gmail.com" };
        for (String alias : aliases) {
            assertThat(limiter.allow(request, alias)).isTrue();
        }
        // Alias 6th hits the email budget even though each alias string is distinct.
        assertThat(limiter.allow(request, "user+sixth@gmail.com")).isFalse();
    }

    @Test
    void hundredParallelRequestsLetAtMostFiveThrough() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(100);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger allowed = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (limiter.allow(request, "victim@example.com")) {
                    allowed.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> f : futures) {
            f.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(allowed.get()).isEqualTo(5);
    }
}
