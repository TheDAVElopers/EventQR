package com.thedavelopers.eventqr.features.transactions.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransactionRequest(@NotNull UUID eventId, @NotNull UUID scanPurposeId, @NotBlank String qrValue,
                                 UUID staffUserId, String notes) {
}