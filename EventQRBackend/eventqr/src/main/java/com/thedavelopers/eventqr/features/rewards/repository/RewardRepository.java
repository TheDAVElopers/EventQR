package com.thedavelopers.eventqr.features.rewards.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;

public interface RewardRepository extends JpaRepository<Reward, UUID> {

    List<Reward> findByEventId(UUID eventId);
}