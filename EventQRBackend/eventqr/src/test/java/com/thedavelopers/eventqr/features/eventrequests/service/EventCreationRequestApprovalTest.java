package com.thedavelopers.eventqr.features.eventrequests.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.thedavelopers.eventqr.features.auditlogs.service.AuditLogService;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.eventrequests.model.entity.EventCreationRequest;
import com.thedavelopers.eventqr.features.eventrequests.repository.EventCreationRequestRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.constants.EventRequestStatus;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort.AttendeeSnapshot;

import jakarta.persistence.EntityManager;

/**
 * Approval is now the single admin action: it creates the event, makes the requester its organizer, and grants the
 * ORGANIZER role. The separate "upgrade to organizer" step no longer exists.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventCreationRequestApprovalTest {

    @Mock private EventCreationRequestRepository eventRequestRepository;
    @Mock private EventRepository eventRepository;
    @Mock private AttendeeDirectoryPort attendeeDirectoryPort;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;
    @Mock private EntityManager entityManager;

    private EventCreationRequestService service;

    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EventCreationRequestService(eventRequestRepository, eventRepository, attendeeDirectoryPort,
                auditLogService, notificationService);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    private EventCreationRequest pendingRequest() {
        EventCreationRequest request = new EventCreationRequest();
        request.setId(UUID.randomUUID());
        request.setRequesterUserId(UUID.randomUUID());
        request.setRequesterName("Rhea Requester");
        request.setEventName("Expo");
        request.setEventDescription("A day of talks");
        request.setEventCategory("Technology");
        request.setVenue("Main Hall");
        request.setCapacity(100);
        request.setRequestedFeatures(List.of());
        request.setStartDateTime(Instant.parse("2030-01-01T01:00:00Z"));
        request.setEndDateTime(Instant.parse("2030-01-01T05:00:00Z"));
        request.setStatus(EventRequestStatus.PENDING);
        when(eventRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(eventRequestRepository.saveAndFlush(any(EventCreationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            event.setId(UUID.randomUUID());
            return event;
        });
        return request;
    }

    private void givenRequesterRole(EventCreationRequest request, AccountRole role) {
        when(attendeeDirectoryPort.findById(request.getRequesterUserId()))
                .thenReturn(Optional.of(new AttendeeSnapshot(request.getRequesterUserId(), "rhea@example.com",
                        request.getRequesterName(), "09171234567", role, AccountStatus.ACTIVE)));
    }

    @Test
    void approvingTheRequestMakesTheRequesterItsOrganizerAndAnOrganizerAccount() {
        EventCreationRequest request = pendingRequest();
        givenRequesterRole(request, AccountRole.ATTENDEE);

        service.approve(request.getId(), adminId, "Admin", null);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).saveAndFlush(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrganizerUserId()).isEqualTo(request.getRequesterUserId());
        verify(attendeeDirectoryPort).changeRole(request.getRequesterUserId(), AccountRole.ORGANIZER);
        verify(auditLogService).log(eq("ACCOUNT_ROLE_UPDATED"), anyString(), eq(adminId), eq("Admin"),
                any(), eq(request.getRequesterUserId()));
    }

    @Test
    void approvingDoesNotDowngradeAnAdminRequester() {
        EventCreationRequest request = pendingRequest();
        givenRequesterRole(request, AccountRole.ADMIN);

        service.approve(request.getId(), adminId, "Admin", null);

        verify(attendeeDirectoryPort, never()).changeRole(any(), any());
        verify(auditLogService, never()).log(eq("ACCOUNT_ROLE_UPDATED"), anyString(), eq(adminId), anyString(),
                any(), any());
    }

    @Test
    void approvalNotificationPointsAtTheCreatedEvent() {
        EventCreationRequest request = pendingRequest();
        givenRequesterRole(request, AccountRole.ATTENDEE);

        service.approve(request.getId(), adminId, "Admin", null);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).saveAndFlush(eventCaptor.capture());
        verify(notificationService).createEventApprovedNotification(
                eventCaptor.getValue().getId(), request.getRequesterUserId(), request.getEventName());
        verify(notificationService, never()).createEventRejectedNotification(any(), any(), any(), any());
    }

    @Test
    void approvingAFutureEventLeavesItApprovedRatherThanActive() {
        EventCreationRequest request = pendingRequest();
        givenRequesterRole(request, AccountRole.ATTENDEE);

        service.approve(request.getId(), adminId, "Admin", null);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).saveAndFlush(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(EventStatus.APPROVED);
        // A future event is activated by the status sweep at its start time, not at approval time.
        verify(notificationService, never()).createEventStartingSoonNotifications(any());
    }
}
