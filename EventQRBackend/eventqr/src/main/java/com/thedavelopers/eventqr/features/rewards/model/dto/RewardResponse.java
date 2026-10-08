package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;

/**
 * {@code stockQuantity} is the REMAINING stock (decremented per redemption; null = unlimited).
 * {@code claimedCount} is the number of REDEEMED redemptions; {@code totalQuantity} is
 * {@code stockQuantity + claimedCount} (null when unlimited).
 * {@code claimedCount} and {@code totalQuantity} are populated only on organizer/staff routes; on
 * attendee-facing and unguarded reads they are null.
 */
public record RewardResponse(UUID rewardId, UUID eventId, String name, String description, int pointsRequired,
                             RewardStatus status, Integer stockQuantity, boolean allowDuplicateClaims,
                             Long claimedCount, Integer totalQuantity) {

    /** Attendee-facing variant: claimedCount/totalQuantity are not exposed. */
    public static RewardResponse withoutCounts(Reward reward) {
        return new RewardResponse(reward.getId(), reward.getEventId(), reward.getName(), reward.getDescription(),
                reward.getPointsRequired(), reward.getStatus(), reward.getStockQuantity(),
                reward.isAllowDuplicateClaims(), null, null);
    }

    public static RewardResponse of(Reward reward, long claimedCount) {
        Integer stock = reward.getStockQuantity();
        Integer total = stock == null ? null : (int) Math.min(Integer.MAX_VALUE, (long) stock + claimedCount);
        return new RewardResponse(reward.getId(), reward.getEventId(), reward.getName(), reward.getDescription(),
                reward.getPointsRequired(), reward.getStatus(), stock, reward.isAllowDuplicateClaims(),
                claimedCount, total);
    }
}
