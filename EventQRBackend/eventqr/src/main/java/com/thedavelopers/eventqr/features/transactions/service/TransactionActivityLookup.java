package com.thedavelopers.eventqr.features.transactions.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.interfaces.ActivityLookupPort;

/**
 * An APPROVED scan for the registration counts as recorded activity; rejected scans do not. Scope: any APPROVED scan,
 * including 0-point tracking-only scans, blocks an attendee self-cancel.
 */
@Component
class TransactionActivityLookup implements ActivityLookupPort {

    private final TransactionLogRepository transactionLogRepository;

    TransactionActivityLookup(TransactionLogRepository transactionLogRepository) {
        this.transactionLogRepository = transactionLogRepository;
    }

    @Override
    public boolean hasRecordedActivity(UUID eventId, UUID attendeeUserId, UUID registrationId) {
        return transactionLogRepository.existsByRegistrationIdAndTransactionResult(registrationId, TransactionResult.APPROVED);
    }
}
