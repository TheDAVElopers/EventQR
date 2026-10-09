package com.thedavelopers.eventqr.features.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;

class TransactionActivityLookupTest {

    private final TransactionLogRepository logs = mock(TransactionLogRepository.class);
    private final TransactionActivityLookup lookup = new TransactionActivityLookup(logs);
    private final UUID registrationId = UUID.randomUUID();

    @Test
    void anApprovedScanCountsAsActivity() {
        when(logs.existsByRegistrationIdAndTransactionResult(registrationId, TransactionResult.APPROVED)).thenReturn(true);
        assertThat(lookup.hasRecordedActivity(UUID.randomUUID(), UUID.randomUUID(), registrationId)).isTrue();
    }

    @Test
    void onlyRejectedScansDoNotCountAsActivity() {
        when(logs.existsByRegistrationIdAndTransactionResult(registrationId, TransactionResult.REJECTED)).thenReturn(true);
        assertThat(lookup.hasRecordedActivity(UUID.randomUUID(), UUID.randomUUID(), registrationId)).isFalse();
    }
}
