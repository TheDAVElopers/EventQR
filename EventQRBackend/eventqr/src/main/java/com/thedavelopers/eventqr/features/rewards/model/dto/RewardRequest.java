package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RewardRequest(@NotNull UUID eventId, @NotBlank String name, @Min(0) int pointsRequired,
                            Integer stockQuantity) {
}