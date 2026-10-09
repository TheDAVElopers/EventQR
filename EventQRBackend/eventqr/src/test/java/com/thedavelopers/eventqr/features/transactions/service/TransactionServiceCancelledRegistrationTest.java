package com.thedavelopers.eventqr.features.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort.AttendeeSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort.EventSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationCommandPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort.RegistrationSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort.ScanPurposeSnapshot;

/** Scans must be refused for cancelled and no-show registrations, even if the QR is still active. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceCancelledRegistrationTest {

    @Mock private TransactionLogRepository transactionLogRepository;
    @Mock private TransactionRuleRepository transactionRuleRepository;
    @Mock private EventLookupPort eventLookupPort;
    @Mock private ScanPurposePort scanPurposePort;
    @Mock private QrCredentialPort qrCredentialPort;
    @Mock private RegistrationLookupPort registrationLookupPort;
    @Mock private RegistrationCommandPort registrationCommandPort;
    @Mock private AttendeeDirectoryPort attendeeDirectoryPort;
    @Mock private EventStaffAssignmentRepository eventStaffAssignmentRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private TransactionService service;

    private final UUID eventId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID qrId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionLogRepository, transactionRuleRepository, eventLookupPort,
                scanPurposePort, qrCredentialPort, registrationLookupPort, registrationCommandPort,
                attendeeDirectoryPort, eventStaffAssignmentRepository, applicationEventPublisher, "Asia/Manila");
        when(eventLookupPort.requireEvent(eventId)).thenReturn(new EventSnapshot(eventId, "Event", "Loc",
                EventStatus.ACTIVE, Instant.now(), Instant.now(), Instant.now(), Instant.now(), 10, 1, true, UUID.randomUUID()));
        when(scanPurposePort.requireActive(purposeId)).thenReturn(
                new ScanPurposeSnapshot(purposeId, eventId, "Entry", ScanPurposeCode.ENTRY, true, false, null));
        when(attendeeDirectoryPort.findById(staffId)).thenReturn(Optional.of(
                new AttendeeSnapshot(staffId, "s@example.com", "Staff", null, AccountRole.ADMIN, AccountStatus.ACTIVE)));
        when(qrCredentialPort.findByQrValue("qr")).thenReturn(Optional.of(new QrCredentialSnapshot(qrId, eventId,
                attendeeId, registrationId, "qr", true, QrDisplayStatus.PENDING, QrDeliveryStatus.PENDING, false)));
        when(transactionLogRepository.save(any(TransactionLog.class))).thenAnswer(i -> {
            TransactionLog l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
    }

    private void givenRegistration(RegistrationStatus status) {
        RegistrationSnapshot snapshot = new RegistrationSnapshot(
                registrationId, eventId, attendeeId, "a@example.com", "Jane", status, qrId,
                Instant.now(), null, null, null, 0, 1);
        when(registrationLookupPort.findByQrCredentialId(qrId)).thenReturn(Optional.of(snapshot));
        when(registrationCommandPort.lockForUpdate(registrationId)).thenReturn(snapshot);
    }

    private TransactionRequest request() {
        return new TransactionRequest(eventId, purposeId, "qr", null, staffId, null, null);
    }

    @Test
    void verifyRejectsCancelledAndNoShowRegistrations() {
        for (RegistrationStatus status : new RegistrationStatus[] {RegistrationStatus.CANCELLED, RegistrationStatus.NO_SHOW}) {
            givenRegistration(status);
            assertThatThrownBy(() -> service.verify(request())).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void verifyStillAcceptsRegisteredAttendees() {
        givenRegistration(RegistrationStatus.REGISTERED);
        assertThat(service.verify(request()).registrationId()).isEqualTo(registrationId);
    }

    @Test
    void recordRejectsCancelledRegistrationWithoutApplyingEffects() {
        givenRegistration(RegistrationStatus.CANCELLED);

        TransactionResponse response = service.recordNew(request());

        assertThat(response.transactionResult()).isEqualTo(TransactionResult.REJECTED);
        verify(registrationCommandPort, never()).markEntered(any());
        verify(registrationCommandPort, never()).markAttended(any());
    }
}
