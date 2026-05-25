package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record RewardRedemptionRequest(@NotNull UUID eventId, @NotNull UUID attendeeUserId, @NotNull UUID rewardId) {
}