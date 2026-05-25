package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.RewardStatus;

public record RewardResponse(UUID rewardId, UUID eventId, String name, int pointsRequired, RewardStatus status,
                             Integer stockQuantity) {
}