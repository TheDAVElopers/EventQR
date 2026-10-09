package com.thedavelopers.eventqr.features.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import com.thedavelopers.eventqr.shared.constants.TransactionType;
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

/**
 * Two concurrent scans of the same QR must not both be APPROVED: the registration row is locked before the
 * approved history is read, and same-key retries are serialized before the idempotency lookup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceScanLockTest {

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
        when(attendeeDirectoryPort.findById(staffId)).thenReturn(Optional.of(
                new AttendeeSnapshot(staffId, "s@example.com", "Staff", null, AccountRole.ADMIN, AccountStatus.ACTIVE)));
        when(qrCredentialPort.findByQrValue("qr")).thenReturn(Optional.of(new QrCredentialSnapshot(qrId, eventId,
                attendeeId, registrationId, "qr", true, QrDisplayStatus.PENDING, QrDeliveryStatus.PENDING, false)));
        RegistrationSnapshot registered = snapshot(RegistrationStatus.REGISTERED);
        when(registrationLookupPort.findByQrCredentialId(qrId)).thenReturn(Optional.of(registered));
        when(registrationCommandPort.lockForUpdate(registrationId)).thenReturn(registered);
        when(transactionLogRepository.save(any(TransactionLog.class))).thenAnswer(i -> {
            TransactionLog l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
    }

    private RegistrationSnapshot snapshot(RegistrationStatus status) {
        return new RegistrationSnapshot(registrationId, eventId, attendeeId, "a@example.com", "Jane", status, qrId,
                Instant.now(), null, null, null, 0, 1);
    }

    private void givenPurpose(ScanPurposeCode code) {
        when(scanPurposePort.requireActive(purposeId)).thenReturn(
                new ScanPurposeSnapshot(purposeId, eventId, code.name(), code, true, false, null));
    }

    private TransactionRequest request(UUID clientRequestId) {
        return new TransactionRequest(eventId, purposeId, "qr", null, staffId, null, clientRequestId);
    }

    private TransactionLog approvedEntry() {
        TransactionLog log = new TransactionLog();
        log.setId(UUID.randomUUID());
        log.setEventId(eventId);
        log.setRegistrationId(registrationId);
        log.setScanPurposeId(purposeId);
        log.setTransactionType(TransactionType.ENTRY);
        log.setTransactionResult(TransactionResult.APPROVED);
        log.setScannedAt(Instant.now());
        return log;
    }

    @Test
    void registrationIsLockedBeforeTheDuplicateHistoryIsRead() {
        givenPurpose(ScanPurposeCode.ENTRY);

        TransactionResponse response = service.recordNew(request(null));

        assertThat(response.transactionResult()).isEqualTo(TransactionResult.APPROVED);
        InOrder order = inOrder(registrationCommandPort, transactionLogRepository);
        order.verify(registrationCommandPort).lockForUpdate(registrationId);
        order.verify(transactionLogRepository)
                .findByRegistrationIdAndScanPurposeIdOrderByScannedAtDesc(registrationId, purposeId);
        order.verify(registrationCommandPort).markEntered(registrationId);
        order.verify(transactionLogRepository).save(any(TransactionLog.class));
    }

    @Test
    void scanThatLostTheRaceSeesTheWinnersApprovalAndIsRejectedAsDuplicate() {
        givenPurpose(ScanPurposeCode.ENTRY);
        // What the second scan reads once the first one's transaction has committed and released the lock.
        when(transactionLogRepository.findByRegistrationIdAndScanPurposeIdOrderByScannedAtDesc(registrationId, purposeId))
                .thenReturn(List.of(approvedEntry()));

        TransactionResponse response = service.recordNew(request(null));

        assertThat(response.transactionResult()).isEqualTo(TransactionResult.REJECTED);
        assertThat(response.pointsDelta()).isZero();
        verify(registrationCommandPort).lockForUpdate(registrationId);
        verify(registrationCommandPort, never()).markEntered(any());
    }

    @Test
    void statusIsCheckedAgainstTheLockedRowNotThePreLockRead() {
        // Read as REGISTERED, but a concurrent cancel committed before the lock was granted.
        givenPurpose(ScanPurposeCode.ENTRY);
        when(registrationCommandPort.lockForUpdate(registrationId)).thenReturn(snapshot(RegistrationStatus.CANCELLED));

        TransactionResponse response = service.recordNew(request(null));

        assertThat(response.transactionResult()).isEqualTo(TransactionResult.REJECTED);
        assertThat(response.reason()).isEqualTo("Registration is not active");
        verify(registrationCommandPort, never()).markEntered(any());
    }

    @Test
    void wrongEventRejectionsTakeNoLock() {
        givenPurpose(ScanPurposeCode.ENTRY);
        when(registrationLookupPort.findByQrCredentialId(qrId)).thenReturn(Optional.of(new RegistrationSnapshot(
                registrationId, UUID.randomUUID(), attendeeId, "a@example.com", "Jane", RegistrationStatus.REGISTERED,
                qrId, Instant.now(), null, null, null, 0, 1)));

        service.recordNew(request(null));

        verify(registrationCommandPort, never()).lockForUpdate(any());
    }

    @Test
    void rewardRedemptionScanIsAlsoSerializedPerRegistration() {
        givenPurpose(ScanPurposeCode.REWARD_REDEMPTION_SCAN);

        service.recordNew(request(null));

        verify(registrationCommandPort).lockForUpdate(registrationId);
    }

    @Test
    void sameKeyRetriesAreSerializedBeforeTheIdempotencyLookup() {
        givenPurpose(ScanPurposeCode.ENTRY);
        UUID key = UUID.randomUUID();
        when(transactionLogRepository.findByClientRequestId(key)).thenReturn(Optional.empty());

        service.record(request(key));

        InOrder order = inOrder(transactionLogRepository);
        order.verify(transactionLogRepository).boundLockWaits();
        order.verify(transactionLogRepository)
                .acquireTransactionLock(TransactionService.CLIENT_REQUEST_LOCK_NAMESPACE, key.hashCode());
        order.verify(transactionLogRepository).findByClientRequestId(key);
        order.verify(transactionLogRepository).save(any(TransactionLog.class));
    }

    @Test
    void scansWithoutAKeyTakeNoIdempotencyLock() {
        givenPurpose(ScanPurposeCode.ENTRY);

        service.record(request(null));

        verify(transactionLogRepository, never()).acquireTransactionLock(any(Integer.class), any(Integer.class));
    }
}
