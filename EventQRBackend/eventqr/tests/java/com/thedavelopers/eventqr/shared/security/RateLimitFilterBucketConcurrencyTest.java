package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class RateLimitFilterBucketConcurrencyTest {

    private static final long START = 1_000_000L;

    @Test
    void concurrentRefillsNeverDoubleCountTheSameElapsedWindow() throws Exception {
        // Regression for the refill race: the pre-fix code assigned lastRefillAt = now
        // BEFORE the token CAS loop, so N concurrent refills all read the same elapsed
        // window and each credited the full refill (~2x intended rate).
        RateLimitFilter.Bucket bucket = RateLimitFilter.Bucket.empty(START);
        for (int i = 0; i < 100; i++) {
            bucket.tryConsume(); // leave 100 tokens (100_000 milli-tokens) in the bucket
        }
        assertThat(bucket.milliTokens()).isEqualTo(100_000L);

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    bucket.refill(START + 1000L); // 1s elapsed => +20 tokens (20_000 milli-tokens)
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // Exactly ONE window must have been credited, regardless of thread interleaving:
        // 100_000 + 20_000 = 120_000 milli-tokens (120 tokens).
        // Any double-counting lands strictly above this value.
        assertThat(bucket.milliTokens()).isEqualTo(120_000L);
    }
}