package com.thedavelopers.eventqr.features.idprinting.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Internal print command. {@code staffUserId} is the authenticated caller (recorded as printed_by_user_id). Whether
 * the print is a reprint is decided by IdPrintingService from the attendee's PRINTED history, not by the caller.
 */
public record IdPrintRequest(@NotNull UUID eventId, @NotNull UUID qrCredentialId, @NotNull UUID staffUserId) {
}
