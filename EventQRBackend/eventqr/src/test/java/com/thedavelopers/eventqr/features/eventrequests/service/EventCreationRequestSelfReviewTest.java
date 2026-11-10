package com.thedavelopers.eventqr.features.eventrequests.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.thedavelopers.eventqr.features.auditlogs.service.AuditLogService;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.eventrequests.model.entity.EventCreationRequest;
import com.thedavelopers.eventqr.features.eventrequests.repository.EventCreationRequestRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.shared.constants.EventRequestStatus;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;

/**
 * B2: an admin must not approve or reject a request they submitted themselves, even after becoming an admin.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventCreationRequestSelfReviewTest {

    @Mock private EventCreationRequestRepository eventRequestRepository;
    @Mock private EventRepository eventRepository;
    @Mock private AttendeeDirectoryPort attendeeDirectoryPort;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;
    @InjectMocks private EventCreationRequestService service;

    private EventCreationRequest pendingRequest(UUID requesterUserId) {
        EventCreationRequest request = new EventCreationRequest();
        request.setId(UUID.randomUUID());
        request.setRequesterUserId(requesterUserId);
        request.setEventName("Expo");
        request.setStatus(EventRequestStatus.PENDING);
        request.setStartDateTime(Instant.parse("2030-01-01T01:00:00Z"));
        request.setEndDateTime(Instant.parse("2030-01-01T05:00:00Z"));
        when(eventRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void approvalByTheRequesterIsForbidden() {
        EventCreationRequest request = pendingRequest(UUID.randomUUID());
        UUID requester = request.getRequesterUserId();

        assertThatThrownBy(() -> service.approve(request.getId(), requester, "Admin", null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You cannot approve or reject your own event request");
        verify(eventRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectionByTheRequesterIsForbidden() {
        EventCreationRequest request = pendingRequest(UUID.randomUUID());
        UUID requester = request.getRequesterUserId();

        assertThatThrownBy(() -> service.reject(request.getId(), requester, "Admin", null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You cannot approve or reject your own event request");
        verify(eventRequestRepository, never()).save(any());
    }
}
