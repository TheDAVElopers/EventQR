package com.thedavelopers.eventqr.features.idprinting.model.dto;

import java.time.Instant;
import java.util.UUID;

public record IdPrintResponse(UUID printLogId, UUID eventId, UUID attendeeUserId, UUID registrationId,
                              UUID qrCredentialId, UUID templateId, boolean reprint, boolean success,
                              String message, Instant printedAt) {
}