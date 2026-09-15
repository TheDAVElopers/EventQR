package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitFilterBucketTest {

    private static final long START = 1_000_000L;

    @Test
    void burstAllowsFullCapacityImmediately() {
        RateLimitFilter.Bucket bucket = RateLimitFilter.Bucket.empty(START);
        assertThat(bucket.milliTokens()).isEqualTo(200_000L); // 200 tokens * 1000 milli-tokens
        for (int i = 0; i < 200; i++) {
            assertThat(bucket.tryConsume())
                    .as("burst should allow consumption %d", i + 1)
                    .isTrue();
        }
        assertThat(bucket.tryConsume()).isFalse();
        assertThat(bucket.milliTokens()).isEqualTo(0L);
    }

    @Test
    void sustainedRefillKeepsRatePerSecond() {
        RateLimitFilter.Bucket bucket = RateLimitFilter.Bucket.empty(START);
        while (bucket.tryConsume()) {
            // drain the burst so we measure only refill throughput
        }
        assertThat(bucket.milliTokens()).isEqualTo(0L);

        int consumed = 0;
        long now = START;
        // Advance in 1s steps: refill adds 20 tokens/s. If consumption matches refill,
        // the bucket must sustain ~20 requests per second indefinitely without drying up.
        for (int second = 0; second < 10; second++) {
            now += 1000L;
            bucket.refill(now);
            for (int i = 0; i < 20; i++) {
                if (bucket.tryConsume()) {
                    consumed++;
                }
            }
        }
        assertThat(consumed).isEqualTo(200); // 20/s * 10s
        assertThat(bucket.milliTokens()).isEqualTo(0L);
    }

    @Test
    void recoversAfterExhaustion() {
        RateLimitFilter.Bucket bucket = RateLimitFilter.Bucket.empty(START);
        while (bucket.tryConsume()) {
            // drain
        }
        assertThat(bucket.tryConsume()).isFalse();

        // Wait 1 second: refill adds 20 tokens (20,000 milli-tokens).
        bucket.refill(START + 1000L);
        assertThat(bucket.milliTokens()).isEqualTo(20_000L);
        for (int i = 0; i < 20; i++) {
            assertThat(bucket.tryConsume()).isTrue();
        }
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillCapsAtFullBurstCapacity_RegressionForCapUnitBug() {
        // Regression: this would have caught the CRITICAL bug where refill clamped to
        // BURST (200) in token units against a milli-token running total, collapsing the
        // bucket to 200 milli-tokens (0.2 tokens). It must reach 200,000 milli-tokens.
        RateLimitFilter.Bucket bucket = RateLimitFilter.Bucket.empty(START);
        // Exhaust the burst partially, then wait enough for refill to exceed capacity.
        for (int i = 0; i < 100; i++) {
            bucket.tryConsume();
        }
        assertThat(bucket.milliTokens()).isEqualTo(100_000L);

        // Refill after a long idle window would normally add way more than capacity.
        bucket.refill(START + 60_000L);

        // MUST be full burst: 200 tokens = 200,000 milli-tokens, NOT 200 milli-tokens.
        assertThat(bucket.milliTokens()).isEqualTo(200_000L);
        assertThat(bucket.milliTokens()).isNotEqualTo(200L);
    }
}
