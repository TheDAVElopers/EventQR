package com.thedavelopers.eventqr.features.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.thedavelopers.eventqr.features.notifications.model.entity.Notification;
import com.thedavelopers.eventqr.features.notifications.repository.NotificationRepository;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.shared.constants.NotificationStatus;
import com.thedavelopers.eventqr.shared.constants.NotificationType;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.interfaces.TransactionRecordedEvent;

class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EventRegistrationRepository registrationRepository;

    @Mock
    private EventStaffAssignmentRepository staffAssignmentRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notificationService = new NotificationService(notificationRepository, registrationRepository, staffAssignmentRepository);
    }

    @Test
    void createNewRegistrationNotification_setsCorrectFields() {
        UUID eventId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        notificationService.createNewRegistrationNotification(eventId, org, "Tech Talk", "Alice");
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        Notification n = cap.getValue();
        assertThat(n.getNotificationType()).isEqualTo(NotificationType.REGISTRATION_NEW);
        assertThat(n.getEventId()).isEqualTo(eventId);
        assertThat(n.getRecipientUserId()).isEqualTo(org);
        assertThat(n.getTitle()).contains("Tech Talk");
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void createCapacityWarningNotification_at80Percent() {
        UUID eventId = UUID.randomUUID();
        notificationService.createCapacityWarningNotification(eventId, UUID.randomUUID(), "Concert", 80, 100);
        verify(notificationRepository).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    void createCapacityFullNotification_atExactCapacity() {
        UUID eventId = UUID.randomUUID();
        notificationService.createCapacityFullNotification(eventId, UUID.randomUUID(), "Concert", 100, 100);
        verify(notificationRepository).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    void findByRecipientFiltered_byStatus() {
        UUID user = UUID.randomUUID();
        when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of());
        var result = notificationService.findByRecipientFiltered(user, NotificationStatus.PENDING, null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void findByRecipientFiltered_byEventId_andType() {
        UUID user = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        Notification n = new Notification();
        n.setEventId(event);
        n.setNotificationType(NotificationType.REGISTRATION_NEW);
        n.setStatus(NotificationStatus.PENDING);
        n.setTitle("t");
        n.setMessage("m");
        n.setRecipientUserId(user);
        when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of(n));
        var result = notificationService.findByRecipientFiltered(user, null, event, NotificationType.REGISTRATION_NEW);
        assertThat(result).hasSize(1);
    }

    @Test
    void onTransactionRecorded_rejected_notifiesScanningAndAssignedStaff() {
        UUID eventId = UUID.randomUUID();
        UUID attendee = UUID.randomUUID();
        UUID scanningStaff = UUID.randomUUID();
        UUID assignedStaff = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        EventStaffAssignment assignment = new EventStaffAssignment();
        assignment.setStaffUserId(assignedStaff);
        when(staffAssignmentRepository.findByEventIdAndActiveTrue(eventId)).thenReturn(List.of(assignment));
        notificationService.onTransactionRecorded(new TransactionRecordedEvent(
                txId, eventId, attendee, null, null, null, TransactionType.ENTRY,
                TransactionResult.REJECTED, 0, scanningStaff, "Duplicate entry"));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(n -> {
            assertThat(n.getRecipientUserId()).isEqualTo(scanningStaff);
            assertThat(n.getNotificationType()).isEqualTo(NotificationType.SCAN_REJECTED);
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        });
        assertThat(captor.getAllValues()).anySatisfy(n ->
                assertThat(n.getRecipientUserId()).isEqualTo(assignedStaff));
        assertThat(captor.getAllValues()).anySatisfy(n ->
                assertThat(n.getRecipientUserId()).isEqualTo(attendee));
    }

    @Test
    void onTransactionRecorded_rejected_dedupesScanningStaffWhoIsAssigned() {
        UUID eventId = UUID.randomUUID();
        UUID attendee = UUID.randomUUID();
        UUID scanningStaff = UUID.randomUUID();
        EventStaffAssignment assignment = new EventStaffAssignment();
        assignment.setStaffUserId(scanningStaff);
        when(staffAssignmentRepository.findByEventIdAndActiveTrue(eventId)).thenReturn(List.of(assignment));
        notificationService.onTransactionRecorded(new TransactionRecordedEvent(
                eventId, eventId, attendee, null, null, null, TransactionType.ENTRY,
                TransactionResult.REJECTED, 0, scanningStaff, "Duplicate reward claim"));
        verify(notificationRepository, times(2)).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    void onTransactionRecorded_rejected_nullScanningStaffStillNotifiesAssigned() {
        UUID eventId = UUID.randomUUID();
        UUID attendee = UUID.randomUUID();
        UUID assignedStaff = UUID.randomUUID();
        EventStaffAssignment assignment = new EventStaffAssignment();
        assignment.setStaffUserId(assignedStaff);
        when(staffAssignmentRepository.findByEventIdAndActiveTrue(eventId)).thenReturn(List.of(assignment));
        notificationService.onTransactionRecorded(new TransactionRecordedEvent(
                eventId, eventId, attendee, null, null, null, TransactionType.ENTRY,
                TransactionResult.REJECTED, 0, null, "Wrong event"));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(n ->
                assertThat(n.getRecipientUserId()).isEqualTo(assignedStaff));
    }
}
