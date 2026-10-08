package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationRateLimiterTest {

    private static final String IP = "203.0.113.9";

    private TestMillis clock;
    private RegistrationRateLimiter limiter;
    private final UUID caller = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock = new TestMillis();
        limiter = new RegistrationRateLimiter(clock);
    }

    @Test
    void burstAllowsTenThenRejectsEleventhWithinWindow() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allow(IP, caller)).isTrue();
        }
        assertThat(limiter.allow(IP, caller)).isFalse();
    }

    @Test
    void cooldownAfterWindowAllowsAgain() {
        for (int i = 0; i < 10; i++) {
            limiter.allow(IP, caller);
        }
        assertThat(limiter.allow(IP, caller)).isFalse();

        clock.advance(61_000L);
        assertThat(limiter.allow(IP, caller)).isTrue();
    }

    @Test
    void perIpBudgetBlocksManyCallersFromOneAddress() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allow(IP, UUID.randomUUID())).isTrue();
        }
        assertThat(limiter.allow(IP, UUID.randomUUID())).isFalse();
    }

    @Test
    void aThrottledCallerDoesNotBlockAnotherUserOnAnotherIp() {
        for (int i = 0; i < 10; i++) {
            limiter.allow(IP, caller);
        }
        assertThat(limiter.allow(IP, caller)).isFalse();

        assertThat(limiter.allow("198.51.100.77", UUID.randomUUID())).isTrue();
    }

    @Test
    void aCallerBudgetFollowsTheUserAcrossIps() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allow("198.51.100." + i, caller)).isTrue();
        }
        assertThat(limiter.allow("198.51.100.200", caller)).isFalse();
    }

    @Test
    void aRejectedRequestRecordsNothing() {
        for (int i = 0; i < 10; i++) {
            limiter.allow(IP, caller);
        }
        UUID other = UUID.randomUUID();
        // Blocked on the IP: the other caller's own bucket must not be charged.
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.allow(IP, other)).isFalse();
        }
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allow("198.51.100." + i, other)).isTrue();
        }
    }

    @Test
    void worksOnAMonotonicSourceThatStartsNegative() {
        TestMillis negative = new TestMillis(-5_000_000L);
        RegistrationRateLimiter l = new RegistrationRateLimiter(negative);
        for (int i = 0; i < 10; i++) {
            assertThat(l.allow(IP, caller)).isTrue();
        }
        assertThat(l.allow(IP, caller)).isFalse();
        negative.advance(61_000L);
        assertThat(l.allow(IP, caller)).isTrue();
    }

    @Test
    void anUnknownIpIsBucketedTogetherAndNullCallerIsIpOnly() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allow(null, null)).isTrue();
        }
        assertThat(limiter.allow("  ", null)).isFalse();
    }

    @Test
    void hundredParallelRequestsLetAtMostTenThrough() throws Exception {
        int threads = 100;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger allowed = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (limiter.allow(IP, caller)) {
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

        assertThat(allowed.get()).isEqualTo(10);
    }

    @Test
    void differentSpellingsOfOneAddressShareOneBucket() {
        String[] spellings = {"1.2.3.4", "::ffff:1.2.3.4", "0:0:0:0:0:ffff:102:304", " 1.2.3.4 "};
        int allowed = 0;
        for (int i = 0; i < 12; i++) {
            if (limiter.allow(spellings[i % spellings.length], UUID.randomUUID())) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(10);
    }
}
