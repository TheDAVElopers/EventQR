package com.thedavelopers.eventqr.features.monitor.model;

import java.time.Instant;

/**
 * One observed database operation. Deliberately metadata only: no field values, bound parameters, SQL text,
 * request data or tokens ever reach this record.
 */
public record CrudEvent(
        long seq,
        Instant timestamp,
        CrudOperation operation,
        String table,
        String entityId,
        CrudResult result,
        String description,
        String route,
        String actorRole,
        Integer rowCount) {
}
