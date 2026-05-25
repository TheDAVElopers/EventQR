package com.thedavelopers.eventqr.features.scanpurposes.model.dto;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScanPurposeRequest(@NotNull UUID eventId, @NotBlank String name, @NotNull ScanPurposeCode code,
                                 boolean active, boolean trackingOnly, String description) {
}