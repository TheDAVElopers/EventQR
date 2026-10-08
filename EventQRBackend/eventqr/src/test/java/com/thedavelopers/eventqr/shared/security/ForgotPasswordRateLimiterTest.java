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
        limiter = new ForgotPasswordRateLimiter(clock);
        // getRemoteAddr() is only used on the fallback path, so stub leniently.
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9");
        lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
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
