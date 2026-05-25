package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PointRuleRequest(@NotNull UUID eventId, @NotNull UUID scanPurposeId, @Min(0) int points,
                               boolean active) {
}