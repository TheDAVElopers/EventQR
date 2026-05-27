package com.thedavelopers.eventqr.features.rewards.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.rewards.model.entity.EventBenefit;

public interface EventBenefitRepository extends JpaRepository<EventBenefit, UUID> {

    List<EventBenefit> findByEventId(UUID eventId);

    Optional<EventBenefit> findByIdAndEventId(UUID id, UUID eventId);
}