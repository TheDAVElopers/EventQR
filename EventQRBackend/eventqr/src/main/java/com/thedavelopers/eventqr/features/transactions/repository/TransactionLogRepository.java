package com.thedavelopers.eventqr.features.transactions.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, UUID> {

    // List variants (preserved for existing service calls)
    java.util.Optional<TransactionLog> findByClientRequestId(UUID clientRequestId);
    List<TransactionLog> findByEventId(UUID eventId);
    List<TransactionLog> findByEventIdAndScannedAtGreaterThanEqual(UUID eventId, Instant scannedAt);
    List<TransactionLog> findByEventIdOrderByScannedAtDesc(UUID eventId);
    List<TransactionLog> findByRegistrationIdAndScanPurposeIdOrderByScannedAtDesc(UUID registrationId, UUID scanPurposeId);
    List<TransactionLog> findByAttendeeUserId(UUID attendeeUserId);
    List<TransactionLog> findByStaffUserIdOrderByScannedAtDesc(UUID staffUserId);
    List<TransactionLog> findByStaffUserIdAndScannedAtGreaterThanEqual(UUID staffUserId, Instant scannedAt);
    List<TransactionLog> findByStaffUserIdAndEventIdOrderByScannedAtDesc(UUID staffUserId, UUID eventId);
    List<TransactionLog> findByStaffUserIdAndScanPurposeIdOrderByScannedAtDesc(UUID staffUserId, UUID scanPurposeId);
    List<TransactionLog> findByStaffUserIdAndEventIdAndScanPurposeIdOrderByScannedAtDesc(UUID staffUserId, UUID eventId, UUID scanPurposeId);

    // Page variants (added for pagination)
    Page<TransactionLog> findByEventId(UUID eventId, Pageable pageable);
    Page<TransactionLog> findByEventIdAndScannedAtGreaterThanEqual(UUID eventId, Instant scannedAt, Pageable pageable);
    Page<TransactionLog> findByEventIdOrderByScannedAtDesc(UUID eventId, Pageable pageable);
    Page<TransactionLog> findByRegistrationIdAndScanPurposeIdOrderByScannedAtDesc(UUID registrationId, UUID scanPurposeId, Pageable pageable);
    Page<TransactionLog> findByAttendeeUserId(UUID attendeeUserId, Pageable pageable);
    Page<TransactionLog> findByStaffUserIdOrderByScannedAtDesc(UUID staffUserId, Pageable pageable);
    Page<TransactionLog> findByStaffUserIdAndScannedAtGreaterThanEqual(UUID staffUserId, Instant scannedAt, Pageable pageable);
    Page<TransactionLog> findByStaffUserIdAndEventIdOrderByScannedAtDesc(UUID staffUserId, UUID eventId, Pageable pageable);
    Page<TransactionLog> findByStaffUserIdAndScanPurposeIdOrderByScannedAtDesc(UUID staffUserId, UUID scanPurposeId, Pageable pageable);
    Page<TransactionLog> findByStaffUserIdAndEventIdAndScanPurposeIdOrderByScannedAtDesc(UUID staffUserId, UUID eventId, UUID scanPurposeId, Pageable pageable);

    Page<TransactionLog> findByEventIdAndAttendeeUserId(UUID eventId, UUID attendeeUserId, Pageable pageable);

    // COUNT queries for the staff summary (caller's own scans).
    long countByStaffUserId(UUID staffUserId);
    long countByStaffUserIdAndTransactionResult(UUID staffUserId, TransactionResult result);
    long countByStaffUserIdAndEventId(UUID staffUserId, UUID eventId);
    long countByStaffUserIdAndEventIdAndTransactionResult(UUID staffUserId, UUID eventId, TransactionResult result);
    long countByStaffUserIdAndScannedAtGreaterThanEqual(UUID staffUserId, Instant scannedAt);
    long countByStaffUserIdAndScanPurposeId(UUID staffUserId, UUID scanPurposeId);
    long countByStaffUserIdAndScanPurposeIdAndTransactionResult(UUID staffUserId, UUID scanPurposeId, TransactionResult result);
    long countByStaffUserIdAndEventIdAndScanPurposeId(UUID staffUserId, UUID eventId, UUID scanPurposeId);
    long countByStaffUserIdAndEventIdAndScanPurposeIdAndTransactionResult(UUID staffUserId, UUID eventId, UUID scanPurposeId, TransactionResult result);

    /** Distinct attendees with an APPROVED scan of one of the given types by the staff member since {@code since}. */
    @Query("SELECT COUNT(DISTINCT t.attendeeUserId) FROM TransactionLog t WHERE t.staffUserId = :staffUserId "
            + "AND t.scannedAt >= :since AND t.transactionResult = :result AND t.transactionType IN :types")
    long countDistinctAttendeesSince(@Param("staffUserId") UUID staffUserId, @Param("since") Instant since,
                                     @Param("result") TransactionResult result,
                                     @Param("types") java.util.Collection<TransactionType> types);

    long countByEventId(UUID eventId);
    long countByAttendeeUserId(UUID attendeeUserId);
    long countByAttendeeUserIdAndTransactionResult(UUID attendeeUserId, TransactionResult result);

    boolean existsByScanPurposeId(UUID scanPurposeId);
    boolean existsByEventIdAndScanPurposeId(UUID eventId, UUID scanPurposeId);

    Optional<TransactionLog> findFirstByEventIdOrderByScannedAtDesc(UUID eventId);

    List<TransactionLog> findTop5ByEventIdAndAttendeeUserIdOrderByScannedAtDesc(UUID eventId, UUID attendeeUserId);

    @Query("SELECT t FROM TransactionLog t WHERE t.eventId = :eventId ORDER BY t.scannedAt DESC")
    List<TransactionLog> streamForExport(@Param("eventId") UUID eventId);
}
