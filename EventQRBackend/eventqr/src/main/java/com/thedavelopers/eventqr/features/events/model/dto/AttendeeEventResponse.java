package com.thedavelopers.eventqr.features.events.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.EventStatus;

public record AttendeeEventResponse(UUID eventId, String title, String description, String category, String location,
                                    String eventLogoUrl, Instant registrationOpenAt, Instant registrationCloseAt,
                                    Instant eventStartAt, Instant eventEndAt, int capacity,
                                    int currentAttendeeCount, EventStatus status, UUID organizerUserId,
                                    boolean isOwnedByCurrentUser) {
}