package com.thedavelopers.eventqr.features.registrations.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;

public record RegistrationResponse(UUID registrationId, UUID eventId, UUID attendeeUserId, String attendeeEmail,
                                   String attendeeName, RegistrationStatus status, UUID qrCredentialId,
                                   Instant registeredAt, String eventTitle, String eventLocation,
                                   Instant eventStartAt, Instant eventEndAt) {
}