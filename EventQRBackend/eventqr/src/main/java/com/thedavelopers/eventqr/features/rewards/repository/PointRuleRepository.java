package com.thedavelopers.eventqr.features.rewards.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.rewards.model.entity.PointRule;

public interface PointRuleRepository extends JpaRepository<PointRule, UUID> {

    Optional<PointRule> findByEventIdAndScanPurposeId(UUID eventId, UUID scanPurposeId);

    List<PointRule> findByEventId(UUID eventId);
}