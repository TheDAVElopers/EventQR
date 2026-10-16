package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.thedavelopers.eventqr.features.rewards.service.RewardService;

public record PointAdjustmentRequest(@NotNull UUID attendeeUserId,
                                     @Positive(message = "Points must be between 1 and " + RewardService.MAX_MANUAL_POINT_ADJUSTMENT)
                                     @Max(value = RewardService.MAX_MANUAL_POINT_ADJUSTMENT,
                                             message = "Points must be between 1 and " + RewardService.MAX_MANUAL_POINT_ADJUSTMENT)
                                     int points,
                                     @Size(max = 255) String reason) {
}
