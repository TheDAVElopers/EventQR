package com.thedavelopers.eventqr.features.events.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.EventStatus;

public record EventResponse(UUID eventId, String title, String description, String location,
                            Instant registrationOpenAt, Instant registrationCloseAt, Instant eventStartAt,
                            Instant eventEndAt, int capacity, int currentAttendeeCount, EventStatus status,
                            boolean rewardsEnabled, UUID organizerUserId, UUID approvedByUserId,
                            Instant approvedAt, String rejectionReason) {
}