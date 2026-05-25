package com.thedavelopers.eventqr.features.idprinting.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.idprinting.model.entity.IdPrintLog;

public interface IdPrintLogRepository extends JpaRepository<IdPrintLog, UUID> {

    List<IdPrintLog> findByEventId(UUID eventId);
}