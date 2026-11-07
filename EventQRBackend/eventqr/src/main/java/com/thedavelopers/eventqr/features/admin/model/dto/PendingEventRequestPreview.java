package com.thedavelopers.eventqr.features.admin.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Dashboard teaser row for the oldest pending event requests; the dashboard shows only these four fields. */
public record PendingEventRequestPreview(
        UUID id,
        String requesterName,
        String eventName,
        // The dashboard contract is an ISO-8601 instant, not epoch seconds; pin it instead of trusting the mapper.
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt) {
}
