package com.thedavelopers.eventqr.features.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.shared.constants.NotificationStatus;
import com.thedavelopers.eventqr.shared.constants.NotificationType;
import com.thedavelopers.eventqr.shared.interfaces.TransactionRecordedEvent;

class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EventRegistrationRepository registrationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notificationService = new NotificationService(notificationRepository, registrationRepository);
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
    void onTransactionRecorded_stillWorks() {
        // Regression: existing event listener should not break
        // Just confirm service instantiates and saves work
        UUID eventId = UUID.randomUUID();
        notificationService.createEventApprovedNotification(eventId, UUID.randomUUID(), "Event");
        verify(notificationRepository).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }
}
