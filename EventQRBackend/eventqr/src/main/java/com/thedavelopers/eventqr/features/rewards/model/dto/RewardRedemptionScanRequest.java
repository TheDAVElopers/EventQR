package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RewardRedemptionScanRequest(@NotNull UUID eventId, @NotNull UUID scanPurposeId, String qrValue,
                                          String shortId, UUID staffUserId) {

    public RewardRedemptionScanRequest withStaffUserId(UUID callerId) {
        return new RewardRedemptionScanRequest(eventId, scanPurposeId, qrValue, shortId, callerId);
    }

    public String resolveQrValue() {
        if (shortId != null && !shortId.isBlank()) {
            return shortId.trim();
        }
        return qrValue;
    }
}
