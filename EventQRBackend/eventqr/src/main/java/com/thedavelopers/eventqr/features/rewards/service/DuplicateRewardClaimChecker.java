package com.thedavelopers.eventqr.features.rewards.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.features.rewards.model.entity.RewardRedemption;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;

@Service
public class DuplicateRewardClaimChecker {

    public static final String DUPLICATE_CLAIM_MESSAGE =
            "Reward already claimed. Duplicate claims are not allowed for this reward.";

    private final RewardRedemptionRepository rewardRedemptionRepository;

    public DuplicateRewardClaimChecker(RewardRedemptionRepository rewardRedemptionRepository) {
        this.rewardRedemptionRepository = rewardRedemptionRepository;
    }

    /**
     * Rejects a duplicate claim when the attendee has already REDEEMED this reward and the
     * reward does not allow duplicate claims. Returns null when the claim is allowed.
     */
    public String checkDuplicate(Reward reward, UUID attendeeUserId) {
        List<RewardRedemption> redeemed = rewardRedemptionRepository
                .findByAttendeeUserIdAndRewardIdAndStatus(attendeeUserId, reward.getId(), RedemptionStatus.REDEEMED);
        if (!redeemed.isEmpty() && !reward.isAllowDuplicateClaims()) {
            return DUPLICATE_CLAIM_MESSAGE;
        }
        return null;
    }
}
