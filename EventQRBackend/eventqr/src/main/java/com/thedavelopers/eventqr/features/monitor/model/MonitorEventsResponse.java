package com.thedavelopers.eventqr.features.monitor.model;

import java.time.Instant;
import java.util.List;

/** Plain (non-{@code ApiResponse}) payload of {@code GET /api/v1/monitor/events}. */
public record MonitorEventsResponse(
        String bootId,
        Instant serverTime,
        long latestSeq,
        int bufferSize,
        List<CrudEvent> events) {
}
