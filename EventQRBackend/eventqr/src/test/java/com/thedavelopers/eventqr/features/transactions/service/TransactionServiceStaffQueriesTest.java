package com.thedavelopers.eventqr.features.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.ScanVerificationResponse;
import com.thedavelopers.eventqr.features.transactions.model.dto.StaffTodaySummary;
import com.thedavelopers.eventqr.features.transactions.model.dto.StaffTransactionSummary;
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

/** Staff transaction paging/summary queries and the honesty of the scan verify response. */
class TransactionServiceStaffQueriesTest {

    private TransactionLogRepository logs;
    private EventLookupPort events;
    private ScanPurposePort purposes;
    private QrCredentialPort qrPort;
    private RegistrationLookupPort registrations;
    private AttendeeDirectoryPort directory;
    private TransactionService service;

    private final UUID staffId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();
    private final UUID qrId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        logs = mock(TransactionLogRepository.class);
        events = mock(EventLookupPort.class);
        purposes = mock(ScanPurposePort.class);
        qrPort = mock(QrCredentialPort.class);
        registrations = mock(RegistrationLookupPort.class);
        directory = mock(AttendeeDirectoryPort.class);
        service = new TransactionService(logs, mock(TransactionRuleRepository.class), events, purposes, qrPort,
                registrations, mock(RegistrationCommandPort.class), directory, mock(EventStaffAssignmentRepository.class),
                mock(ApplicationEventPublisher.class), "Asia/Manila");
    }

    private static TransactionLog log() {
        TransactionLog l = new TransactionLog();
        l.setId(UUID.randomUUID());
        l.setScannedAt(Instant.now());
        l.setTransactionType(TransactionType.ENTRY);
        l.setTransactionResult(TransactionResult.APPROVED);
        return l;
    }

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("scannedAt"), Sort.Order.asc("id"));

    // ----- paging and sort -----

    @Test
    void staffTransactionsAreSortedNewestFirstWithIdTiebreakForEveryFilterCombination() {
        UUID p = UUID.randomUUID();
        Page<TransactionLog> page = new PageImpl<>(List.of(log(), log()), PageRequest.of(2, 7), 30);
        when(logs.findByStaffUserIdOrderByScannedAtDesc(eq(staffId), any(Pageable.class))).thenReturn(page);
        when(logs.findByStaffUserIdAndEventIdOrderByScannedAtDesc(eq(staffId), eq(eventId), any(Pageable.class))).thenReturn(page);
        when(logs.findByStaffUserIdAndScanPurposeIdOrderByScannedAtDesc(eq(staffId), eq(p), any(Pageable.class))).thenReturn(page);
        when(logs.findByStaffUserIdAndEventIdAndScanPurposeIdOrderByScannedAtDesc(eq(staffId), eq(eventId), eq(p), any(Pageable.class)))
                .thenReturn(page);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        Page<TransactionResponse> none = service.findForStaff(staffId, null, null, PageRequest.of(2, 7));
        verify(logs).findByStaffUserIdOrderByScannedAtDesc(eq(staffId), pageable.capture());
        assertThat(none.getContent()).hasSize(2);
        assertThat(none.getTotalElements()).isEqualTo(30);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(7);
        assertThat(pageable.getValue().getSort()).isEqualTo(NEWEST_FIRST);

        service.findForStaff(staffId, eventId, null, PageRequest.of(0, 20));
        verify(logs).findByStaffUserIdAndEventIdOrderByScannedAtDesc(eq(staffId), eq(eventId), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(NEWEST_FIRST);

        service.findForStaff(staffId, null, p, PageRequest.of(0, 20));
        verify(logs).findByStaffUserIdAndScanPurposeIdOrderByScannedAtDesc(eq(staffId), eq(p), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(NEWEST_FIRST);

        service.findForStaff(staffId, eventId, p, PageRequest.of(0, 20));
        verify(logs).findByStaffUserIdAndEventIdAndScanPurposeIdOrderByScannedAtDesc(eq(staffId), eq(eventId), eq(p), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(NEWEST_FIRST);
    }

    @Test
    void eventTransactionsFilterByAttendeeOnlyWhenGiven() {
        when(logs.findByEventId(eq(eventId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(log())));
        when(logs.findByEventIdAndAttendeeUserId(eq(eventId), eq(attendeeId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log(), log()), PageRequest.of(0, 20), 2));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        assertThat(service.findForEventStaff(eventId, null, PageRequest.of(0, 20)).getContent()).hasSize(1);
        verify(logs, never()).findByEventIdAndAttendeeUserId(any(), any(), any());

        Page<TransactionResponse> filtered = service.findForEventStaff(eventId, attendeeId, PageRequest.of(0, 20));
        assertThat(filtered.getTotalElements()).isEqualTo(2);
        verify(logs).findByEventIdAndAttendeeUserId(eq(eventId), eq(attendeeId), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(NEWEST_FIRST);
    }

    // ----- summary -----

    @Test
    void summaryUsesCountQueriesForTheCallersOwnScans() {
        when(logs.countByStaffUserId(staffId)).thenReturn(10L);
        when(logs.countByStaffUserIdAndTransactionResult(staffId, TransactionResult.APPROVED)).thenReturn(7L);
        when(logs.countByStaffUserIdAndTransactionResult(staffId, TransactionResult.REJECTED)).thenReturn(3L);

        assertThat(service.summarizeForStaff(staffId, null, null)).isEqualTo(new StaffTransactionSummary(10, 7, 3));
        verify(logs, never()).findByStaffUserIdOrderByScannedAtDesc(any(UUID.class));
    }

    @Test
    void summaryCanBeLimitedToOneEvent() {
        when(logs.countByStaffUserIdAndEventId(staffId, eventId)).thenReturn(4L);
        when(logs.countByStaffUserIdAndEventIdAndTransactionResult(staffId, eventId, TransactionResult.APPROVED)).thenReturn(3L);
        when(logs.countByStaffUserIdAndEventIdAndTransactionResult(staffId, eventId, TransactionResult.REJECTED)).thenReturn(1L);

        assertThat(service.summarizeForStaff(staffId, eventId, null)).isEqualTo(new StaffTransactionSummary(4, 3, 1));
        verify(logs, never()).countByStaffUserId(any());
    }

    @Test
    void summaryCanBeLimitedToOnePurposeSoTheTilesMatchTheFilteredList() {
        UUID purposeId = UUID.randomUUID();
        when(logs.countByStaffUserIdAndScanPurposeId(staffId, purposeId)).thenReturn(6L);
        when(logs.countByStaffUserIdAndScanPurposeIdAndTransactionResult(staffId, purposeId, TransactionResult.APPROVED)).thenReturn(5L);
        when(logs.countByStaffUserIdAndScanPurposeIdAndTransactionResult(staffId, purposeId, TransactionResult.REJECTED)).thenReturn(1L);

        assertThat(service.summarizeForStaff(staffId, null, purposeId)).isEqualTo(new StaffTransactionSummary(6, 5, 1));
        verify(logs, never()).countByStaffUserId(any());
    }

    @Test
    void summaryCanBeLimitedToOneEventAndPurposeTogether() {
        UUID purposeId = UUID.randomUUID();
        when(logs.countByStaffUserIdAndEventIdAndScanPurposeId(staffId, eventId, purposeId)).thenReturn(3L);
        when(logs.countByStaffUserIdAndEventIdAndScanPurposeIdAndTransactionResult(staffId, eventId, purposeId, TransactionResult.APPROVED)).thenReturn(2L);
        when(logs.countByStaffUserIdAndEventIdAndScanPurposeIdAndTransactionResult(staffId, eventId, purposeId, TransactionResult.REJECTED)).thenReturn(1L);

        assertThat(service.summarizeForStaff(staffId, eventId, purposeId)).isEqualTo(new StaffTransactionSummary(3, 2, 1));
        verify(logs, never()).countByStaffUserIdAndEventId(any(), any());
    }

    @Test
    void todaySummaryCountsDistinctApprovedEntryAndAttendancePeopleSinceManilaMidnight() {
        when(logs.countByStaffUserIdAndScannedAtGreaterThanEqual(eq(staffId), any(Instant.class))).thenReturn(12L);
        when(logs.countDistinctAttendeesSince(eq(staffId), any(Instant.class), eq(TransactionResult.APPROVED), any())).thenReturn(5L);

        StaffTodaySummary summary = service.summarizeTodayForStaff(staffId);

        assertThat(summary).isEqualTo(new StaffTodaySummary(12, 5));
        Instant expectedStart = LocalDate.now(ZoneId.of("Asia/Manila")).atStartOfDay(ZoneId.of("Asia/Manila")).toInstant();
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<TransactionType>> types = ArgumentCaptor.forClass(Collection.class);
        verify(logs).countDistinctAttendeesSince(eq(staffId), since.capture(), eq(TransactionResult.APPROVED), types.capture());
        assertThat(since.getValue()).isBetween(expectedStart.minusSeconds(86_400), expectedStart.plusSeconds(86_400));
        assertThat(Set.copyOf(types.getValue())).containsExactlyInAnyOrder(TransactionType.ENTRY, TransactionType.ATTENDANCE);
        verify(logs).countByStaffUserIdAndScannedAtGreaterThanEqual(staffId, since.getValue());
    }

    // ----- verify honesty -----

    private void givenShortIdLookup(boolean qrActive, RegistrationStatus status) {
        when(events.requireEvent(eventId)).thenReturn(new EventSnapshot(eventId, "Event", "Loc", EventStatus.ACTIVE,
                Instant.now(), Instant.now(), Instant.now(), Instant.now(), 10, 1, true, UUID.randomUUID()));
        when(purposes.requireActive(purposeId)).thenReturn(
                new ScanPurposeSnapshot(purposeId, eventId, "Entry", ScanPurposeCode.ENTRY, true, false, null));
        when(directory.findById(staffId)).thenReturn(Optional.of(
                new AttendeeSnapshot(staffId, "s@example.com", "Staff", null, AccountRole.ADMIN, AccountStatus.ACTIVE)));
        when(registrations.findByEventIdAndRegistrationNumber(eventId, 12)).thenReturn(Optional.of(new RegistrationSnapshot(
                registrationId, eventId, attendeeId, "a@example.com", "Jane", status, qrId, Instant.now(), null, null, null, 0, 12)));
        when(qrPort.findById(qrId)).thenReturn(Optional.of(new QrCredentialSnapshot(qrId, eventId, attendeeId, registrationId,
                "qr-value", qrActive, QrDisplayStatus.PENDING, QrDeliveryStatus.PENDING, false)));
    }

    private TransactionRequest shortIdRequest() {
        return new TransactionRequest(eventId, purposeId, null, "#12", staffId, null, null);
    }

    @Test
    void verifyByShortIdReportsAnInactiveCredentialHonestly() {
        givenShortIdLookup(false, RegistrationStatus.REGISTERED);

        ScanVerificationResponse response = service.verify(shortIdRequest());

        assertThat(response.qrActive()).isFalse();
        assertThat(response.eligible()).isFalse();
        assertThat(response.registrationStatus()).isEqualTo(RegistrationStatus.REGISTERED);
        assertThat(response.message()).contains("inactive").doesNotContain("verified");
    }

    @Test
    void verifyByShortIdWithAnActiveCredentialIsEligible() {
        givenShortIdLookup(true, RegistrationStatus.ENTERED);

        ScanVerificationResponse response = service.verify(shortIdRequest());

        assertThat(response.qrActive()).isTrue();
        assertThat(response.eligible()).isTrue();
        assertThat(response.registrationStatus()).isEqualTo(RegistrationStatus.ENTERED);
        assertThat(response.message()).isEqualTo("Attendee ID #12 verified");
    }

    @Test
    void verifyByQrValueIsEligibleWithAnActiveCredential() {
        givenShortIdLookup(true, RegistrationStatus.REGISTERED);
        Optional<QrCredentialSnapshot> qr = qrPort.findById(qrId);
        Optional<RegistrationSnapshot> registration = registrations.findByEventIdAndRegistrationNumber(eventId, 12);
        when(qrPort.findByQrValue("qr-value")).thenReturn(qr);
        when(registrations.findByQrCredentialId(qrId)).thenReturn(registration);

        ScanVerificationResponse response = service.verify(new TransactionRequest(eventId, purposeId, "qr-value", null, staffId, null, null));

        assertThat(response.qrActive()).isTrue();
        assertThat(response.eligible()).isTrue();
        assertThat(response.message()).isEqualTo("QR credential verified");
    }
}
