package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;

public record RewardRedemptionResponse(UUID redemptionId, UUID eventId, UUID attendeeUserId, UUID rewardId,
                                       int pointsSpent, RedemptionStatus status, Instant redeemedAt, String reason) {
}