package com.thedavelopers.eventqr.features.monitor.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.features.monitor.config.MonitorEnabled;
import com.thedavelopers.eventqr.features.monitor.model.CrudEvent;
import com.thedavelopers.eventqr.features.monitor.model.CrudOperation;
import com.thedavelopers.eventqr.features.monitor.model.CrudResult;
import com.thedavelopers.eventqr.features.monitor.model.MonitorEventsResponse;

/**
 * Thread-safe, bounded, in-memory ring buffer of recent {@link CrudEvent}s. Sequence numbers start at 1 and only
 * ever increase for the life of the process; {@link #bootId()} changes on every restart so a polling client can
 * tell a restart (sequence reset) from "no new events". Nothing is persisted and no database is touched.
 */
@Component
@MonitorEnabled
public class CrudEventRecorder {

    public static final int MAX_BUFFER_SIZE = 5000;
    public static final int MAX_LIMIT = 500;
    public static final int DEFAULT_LIMIT = 200;
    /** Page-size bounds applied by the controller while the feed is public. */
    public static final int PUBLIC_MAX_LIMIT = 100;
    public static final int PUBLIC_DEFAULT_LIMIT = 100;

    private final String bootId = UUID.randomUUID().toString();
    private final CrudEvent[] ring;
    private final Object lock = new Object();
    private long latestSeq;

    public CrudEventRecorder(@Value("${app.monitor.buffer-size:2000}") int bufferSize) {
        this.ring = new CrudEvent[Math.max(1, Math.min(bufferSize, MAX_BUFFER_SIZE))];
    }

    public String bootId() {
        return bootId;
    }

    public int bufferSize() {
        return ring.length;
    }

    public long latestSeq() {
        synchronized (lock) {
            return latestSeq;
        }
    }

    /** Appends an event, assigning its sequence number, and evicts the oldest one when the buffer is full. */
    public CrudEvent record(Instant timestamp, CrudOperation operation, String table, String entityId,
                            CrudResult result, String description, String route, String actorRole,
                            Integer rowCount) {
        synchronized (lock) {
            long seq = ++latestSeq;
            CrudEvent event = new CrudEvent(seq, timestamp, operation, table, entityId, result, description, route,
                    actorRole, rowCount);
            ring[slot(seq)] = event;
            return event;
        }
    }

    /**
     * Events with {@code seq > after}, ascending, at most {@code limit}. {@code after} is clamped to
     * [0, latestSeq] (so a negative value behaves as 0 and {@code Long.MAX_VALUE} cannot overflow); an {@code after}
     * beyond the latest sequence yields no events (the client compares {@code bootId} to detect a restart).
     * {@code limit} is clamped to 1..{@value #MAX_LIMIT}.
     */
    public MonitorEventsResponse read(long after, int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<CrudEvent> events = new ArrayList<>();
        long latest;
        synchronized (lock) {
            latest = latestSeq;
            long from = Math.max(0L, Math.min(after, latest));
            long oldestRetained = Math.max(1L, latest - ring.length + 1);
            long first = Math.max(from + 1, oldestRetained);
            for (long seq = first; seq <= latest && events.size() < effectiveLimit; seq++) {
                events.add(ring[slot(seq)]);
            }
        }
        return new MonitorEventsResponse(bootId, Instant.now(), latest, ring.length, List.copyOf(events));
    }

    private int slot(long seq) {
        return (int) ((seq - 1) % ring.length);
    }
}
