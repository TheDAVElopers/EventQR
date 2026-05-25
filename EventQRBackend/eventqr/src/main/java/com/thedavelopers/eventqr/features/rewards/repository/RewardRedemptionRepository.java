package com.thedavelopers.eventqr.features.rewards.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.rewards.model.entity.RewardRedemption;

public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, UUID> {

    Optional<RewardRedemption> findByEventIdAndAttendeeUserIdAndRewardId(UUID eventId, UUID attendeeUserId, UUID rewardId);

    List<RewardRedemption> findByEventId(UUID eventId);
}