package com.thedavelopers.eventqr.features.idprinting.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.IdPrintStatus;

/**
 * One id_print_logs row. {@code status} is the effective status: a PENDING row older than the confirmation grace
 * period (default 10 minutes) is reported as PRINTED, because app builds that predate print confirmation never
 * confirm. {@code success} is true exactly when {@code status} is PRINTED, and {@code printedAt} is non-null exactly
 * then (falling back to the row's creation time for a stale PENDING row), so readers that only know {@code success}
 * and {@code printedAt} keep their old meaning. Fields are appended at the end for backward compatibility.
 */
public record IdPrintResponse(UUID printLogId, UUID eventId, UUID attendeeUserId, UUID registrationId,
                              UUID qrCredentialId, UUID templateId, boolean reprint, boolean success,
                              String message, Instant printedAt, IdPrintStatus status, Instant confirmedAt) {
}
