package com.thedavelopers.eventqr.features.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationCommandPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort;

/** Attendee event-detail transaction lookup must be scoped by event + attendee in the query, not in memory. */
class TransactionServiceAttendeeEventQueryTest {

    private TransactionLogRepository logs;
    private TransactionService service;

    private final UUID eventId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        logs = mock(TransactionLogRepository.class);
        service = new TransactionService(logs, mock(TransactionRuleRepository.class), mock(EventLookupPort.class),
                mock(ScanPurposePort.class), mock(QrCredentialPort.class), mock(RegistrationLookupPort.class),
                mock(RegistrationCommandPort.class), mock(AttendeeDirectoryPort.class),
                mock(EventStaffAssignmentRepository.class), mock(ApplicationEventPublisher.class), "Asia/Manila");
    }

    private TransactionLog log(UUID logEventId, UUID logAttendeeId, TransactionType type) {
        TransactionLog l = new TransactionLog();
        l.setId(UUID.randomUUID());
        l.setEventId(logEventId);
        l.setAttendeeUserId(logAttendeeId);
        l.setRegistrationId(UUID.randomUUID());
        l.setQrCredentialId(UUID.randomUUID());
        l.setScanPurposeId(UUID.randomUUID());
        l.setTransactionType(type);
        l.setTransactionResult(TransactionResult.APPROVED);
        l.setScannedAt(Instant.parse("2026-01-01T10:00:00Z"));
        return l;
    }

    @Test
    void queriesByEventAndAttendeeAndNeverLoadsTheWholeEvent() {
        TransactionLog entry = log(eventId, attendeeId, TransactionType.ENTRY);
        when(logs.findByEventIdAndAttendeeUserIdOrderByScannedAtDesc(eventId, attendeeId))
                .thenReturn(List.of(entry, log(eventId, attendeeId, TransactionType.ATTENDANCE)));

        List<TransactionResponse> result = service.findByEventAndAttendee(eventId, attendeeId);

        verify(logs).findByEventIdAndAttendeeUserIdOrderByScannedAtDesc(eventId, attendeeId);
        verify(logs, never()).findByEventId(any());
        assertThat(result).hasSize(2);
        assertThat(result.get(0).transactionId()).isEqualTo(entry.getId());
        assertThat(result.get(0).eventId()).isEqualTo(eventId);
        assertThat(result.get(0).attendeeUserId()).isEqualTo(attendeeId);
        assertThat(result.get(0).transactionType()).isEqualTo(TransactionType.ENTRY);
        assertThat(result.get(0).scannedAt()).isEqualTo(entry.getScannedAt());
    }

    @Test
    void returnsEmptyListWhenTheAttendeeHasNoTransactionsForTheEvent() {
        when(logs.findByEventIdAndAttendeeUserIdOrderByScannedAtDesc(eventId, attendeeId)).thenReturn(List.of());

        assertThat(service.findByEventAndAttendee(eventId, attendeeId)).isEmpty();
        verify(logs, never()).findByEventId(any());
    }
}
