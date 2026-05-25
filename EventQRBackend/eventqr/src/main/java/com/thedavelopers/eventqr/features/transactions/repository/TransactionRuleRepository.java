package com.thedavelopers.eventqr.features.transactions.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionRule;

public interface TransactionRuleRepository extends JpaRepository<TransactionRule, UUID> {

    Optional<TransactionRule> findByEventIdAndScanPurposeId(UUID eventId, UUID scanPurposeId);
}