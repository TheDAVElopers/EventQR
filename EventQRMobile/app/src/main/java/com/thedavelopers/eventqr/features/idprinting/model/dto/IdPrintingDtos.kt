package com.thedavelopers.eventqr.features.idprinting.model.dto

import java.time.Instant
import java.util.UUID

/** Request for printing multiple ID cards in one batch (one log row per entry). */
data class IdBatchPrintRequest(
    val attendeeUserIds: List<UUID>,
    val reprint: Boolean,
)

/** Body for PATCH staff/events/{eventId}/print-logs/{logId}: PRINTED, CANCELLED or FAILED. */
data class IdPrintConfirmRequest(
    val outcome: String,
)

data class IdPrintResponse(
    val printLogId: UUID,
    val eventId: UUID,
    val attendeeUserId: UUID,
    val registrationId: UUID,
    val qrCredentialId: UUID,
    val templateId: UUID? = null,
    val reprint: Boolean,
    val success: Boolean,
    val message: String,
    val printedAt: Instant? = null,
    /** PENDING, PRINTED, CANCELLED or FAILED; null from servers that predate print confirmation. */
    val status: String? = null,
    /** When the print outcome was confirmed; null while PENDING or from older servers. */
    val confirmedAt: Instant? = null,
)
