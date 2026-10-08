package com.thedavelopers.eventqr.features.rewards.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.rewards.model.entity.RewardRedemption;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;

public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, UUID> {

    Optional<RewardRedemption> findByEventIdAndAttendeeUserIdAndRewardId(UUID eventId, UUID attendeeUserId, UUID rewardId);

    List<RewardRedemption> findByEventId(UUID eventId);

    List<RewardRedemption> findByEventIdAndAttendeeUserId(UUID eventId, UUID attendeeUserId);

    List<RewardRedemption> findByAttendeeUserIdAndRewardIdAndStatus(UUID attendeeUserId, UUID rewardId, RedemptionStatus status);

    long countByRewardIdAndStatus(UUID rewardId, RedemptionStatus status);

    /** One batched query: redemption count per reward for an event, filtered by status. */
    @Query("SELECT r.rewardId AS rewardId, COUNT(r) AS total FROM RewardRedemption r "
            + "WHERE r.eventId = :eventId AND r.status = :status GROUP BY r.rewardId")
    List<ClaimedCountRow> countByEventIdAndStatusGroupedByReward(@Param("eventId") UUID eventId,
                                                                 @Param("status") RedemptionStatus status);

    interface ClaimedCountRow {
        UUID getRewardId();

        Long getTotal();
    }
}