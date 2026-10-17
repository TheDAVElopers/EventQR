package com.thedavelopers.eventqr.features.idprinting.model.dto;

import com.thedavelopers.eventqr.shared.constants.IdPrintStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Body of PATCH /api/v1/staff/events/{eventId}/print-logs/{logId}: the outcome of the print dialog.
 * Must be PRINTED, CANCELLED or FAILED; PENDING is rejected with 400.
 */
public record IdPrintConfirmRequest(@NotNull IdPrintStatus outcome) {
}
