package com.thedavelopers.eventqr.features.transactions.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionRequest(@NotNull UUID eventId, @NotNull UUID scanPurposeId, String qrValue,
                                 String shortId, UUID staffUserId, @Size(max = 500) String notes,
                                 UUID clientRequestId) {

    /** Backward-compatible constructor for callers that predate the idempotency key. */
    public TransactionRequest(UUID eventId, UUID scanPurposeId, String qrValue, String shortId,
                              UUID staffUserId, String notes) {
        this(eventId, scanPurposeId, qrValue, shortId, staffUserId, notes, null);
    }

    /** Normalize short ID input: strip "#" prefix and leading zeros, parse to integer. Returns null if invalid. */
    public Integer parsedShortId() {
        if (shortId == null || shortId.isBlank()) return null;
        String cleaned = shortId.trim().replaceFirst("^#+", "").replaceFirst("^0+", "");
        if (cleaned.isEmpty()) return null;
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean hasShortId() {
        return parsedShortId() != null;
    }
}