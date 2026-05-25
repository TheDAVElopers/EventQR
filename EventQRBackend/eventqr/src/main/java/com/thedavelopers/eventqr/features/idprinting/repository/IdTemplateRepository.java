package com.thedavelopers.eventqr.features.idprinting.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.idprinting.model.entity.IdTemplate;

public interface IdTemplateRepository extends JpaRepository<IdTemplate, UUID> {

    Optional<IdTemplate> findFirstByEventIdAndActiveTrue(UUID eventId);
}