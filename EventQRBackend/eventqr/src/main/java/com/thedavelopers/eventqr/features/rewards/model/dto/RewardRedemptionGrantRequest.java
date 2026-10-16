package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Staff redemption against a prior redemption scan. {@code staffUserId} is kept for wire compatibility but ignored:
 * the controller always replaces it with the authenticated caller.
 */
public record RewardRedemptionGrantRequest(@NotNull UUID eventId, @NotNull UUID attendeeUserId, @NotNull UUID rewardId,
                                           UUID staffUserId, @NotNull UUID redemptionScanLogId) {

    public RewardRedemptionGrantRequest withStaffUserId(UUID callerId) {
        return new RewardRedemptionGrantRequest(eventId, attendeeUserId, rewardId, callerId, redemptionScanLogId);
    }
}
