package com.thedavelopers.eventqr.features.events.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.EventRequestStatus;

public record EventRequestResponse(
        UUID eventRequestId,
        UUID requesterUserId,
        String eventName,
        String eventDescription,
        String eventCategory,
        String targetAudience,
        Integer capacity,
        String venue,
        Instant startDateTime,
        Instant endDateTime,
        Instant registrationStartDateTime,
        Instant registrationEndDateTime,
        String requesterName,
        String contactEmail,
        String contactNumber,
        List<String> requestedFeatures,
        String eventLogoUrl,
        String additionalNotes,
        String reasonForRequest,
        EventRequestStatus status,
        String adminRemarks,
        UUID reviewedByUserId,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt) {
}
