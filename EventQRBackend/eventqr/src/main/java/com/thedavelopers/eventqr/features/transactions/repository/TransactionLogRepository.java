package com.thedavelopers.eventqr.features.transactions.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, UUID> {

    List<TransactionLog> findByEventId(UUID eventId);

    List<TransactionLog> findByRegistrationIdAndScanPurposeIdOrderByScannedAtDesc(UUID registrationId, UUID scanPurposeId);

    long countByRegistrationIdAndScanPurposeIdAndTransactionResult(UUID registrationId, UUID scanPurposeId, TransactionResult transactionResult);

    List<TransactionLog> findByAttendeeUserId(UUID attendeeUserId);

    java.util.Optional<TransactionLog> findFirstByEventIdOrderByScannedAtDesc(UUID eventId);
}