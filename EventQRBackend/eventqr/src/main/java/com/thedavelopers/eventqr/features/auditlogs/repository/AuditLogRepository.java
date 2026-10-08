package com.thedavelopers.eventqr.features.auditlogs.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.thedavelopers.eventqr.features.auditlogs.model.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByEventIdOrderByCreatedAtDesc(UUID eventId);
    List<AuditLog> findAllByOrderByCreatedAtDesc();
    org.springframework.data.domain.Page<AuditLog> findByActionStartingWith(String actionPrefix, org.springframework.data.domain.Pageable pageable);
}
