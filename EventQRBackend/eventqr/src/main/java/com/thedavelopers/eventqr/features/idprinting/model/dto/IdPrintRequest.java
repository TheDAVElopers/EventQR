package com.thedavelopers.eventqr.features.idprinting.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record IdPrintRequest(@NotNull UUID eventId, @NotNull UUID qrCredentialId, @NotNull UUID staffUserId,
                             boolean reprint) {
}