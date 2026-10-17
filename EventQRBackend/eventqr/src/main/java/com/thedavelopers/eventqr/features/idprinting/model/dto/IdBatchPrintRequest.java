package com.thedavelopers.eventqr.features.idprinting.model.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/**
 * Request for printing multiple ID cards in a single batch. Each entry in
 * {@code attendeeUserIds} produces one individual {@code id_print_logs} row,
 * even when the same attendee is repeated (e.g. printing spare copies).
 *
 * {@code reprint} is accepted for backward compatibility but ignored: each row's reprint flag is derived from
 * whether that attendee already has a PRINTED ID.
 */
public record IdBatchPrintRequest(@NotEmpty List<UUID> attendeeUserIds, boolean reprint) {
}
