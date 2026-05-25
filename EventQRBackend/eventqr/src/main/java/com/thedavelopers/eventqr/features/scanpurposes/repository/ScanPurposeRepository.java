package com.thedavelopers.eventqr.features.scanpurposes.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.scanpurposes.model.entity.ScanPurpose;

public interface ScanPurposeRepository extends JpaRepository<ScanPurpose, UUID> {

    List<ScanPurpose> findByEventId(UUID eventId);
}