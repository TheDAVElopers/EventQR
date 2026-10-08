package com.thedavelopers.eventqr.shared.security;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Controllable stand-in for the limiters' monotonic millisecond source. */
final class TestMillis implements LongSupplier {

    private final AtomicLong millis;

    TestMillis() {
        this(1_000_000_000L);
    }

    TestMillis(long start) {
        this.millis = new AtomicLong(start);
    }

    long advance(long ms) {
        return millis.addAndGet(ms);
    }

    @Override
    public long getAsLong() {
        return millis.get();
    }
}
