package com.thedavelopers.eventqr.features.idprinting.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.idprinting.model.entity.IdTemplate;
import com.thedavelopers.eventqr.shared.persistence.LockTimeoutSupport;

public interface IdTemplateRepository extends JpaRepository<IdTemplate, UUID>, LockTimeoutSupport {

    java.util.List<IdTemplate> findByEventId(UUID eventId);

    Optional<IdTemplate> findFirstByEventIdAndActiveTrue(UUID eventId);

    /**
     * Transaction-scoped Postgres advisory lock on (namespace, key); released at commit/rollback. Serializes
     * "find the active template or create one" per event, since id_templates has no unique guard on the active row.
     * Uses the two-int key space, like TransactionLogRepository. Always returns 1.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:namespace, :key)) AS lock_taken", nativeQuery = true)
    int acquireTemplateLock(@Param("namespace") int namespace, @Param("key") int key);
}
