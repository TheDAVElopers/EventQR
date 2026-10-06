package com.thedavelopers.eventqr.features.organizer.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerEventResponse;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;

/**
 * Authorization coverage for OrganizerService.requireOrganizerEvent via event():
 * admin/super bypass by rank (not ownership), organizer must own, staff denied,
 * approved/active/ended status gate applies to everyone including admins.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizerServiceAuthorizationTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventRegistrationRepository registrationRepository;
    @Mock
    private TransactionLogRepository transactionLogRepository;
    @Mock
    private ScanPurposeRepository scanPurposeRepository;
    @Mock
    private TransactionRuleRepository transactionRuleRepository;
    @Mock
    private RewardRedemptionRepository rewardRedemptionRepository;
    @Mock
    private PointTransactionRepository pointTransactionRepository;
    @Mock
    private EventStaffAssignmentRepository staffAssignmentRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private IdTemplateRepository idTemplateRepository;
    @Mock
    private NotificationService notificationService;

    private OrganizerService organizerService;

    private final UUID eventId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        organizerService = new OrganizerService(eventRepository, registrationRepository,
                transactionLogRepository, scanPurposeRepository, transactionRuleRepository,
                rewardRedemptionRepository, pointTransactionRepository, staffAssignmentRepository,
                userProfileRepository, idTemplateRepository, notificationService);
    }

    // --- helpers -----------------------------------------------------------

    private void stubLookup(UUID organizerUserId, EventStatus status) {
        Event event = new Event();
        event.setId(eventId);
        event.setTitle("Event");
        event.setOrganizerUserId(organizerUserId);
        event.setStatus(status);
        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));
        given(userProfileRepository.findById(callerId)).willReturn(Optional.of(mock(UserProfile.class)));
    }

    private void stubProjectionDependencies() {
        given(registrationRepository.findByEventId(any())).willReturn(List.of());
        given(transactionLogRepository.findByEventId(any())).willReturn(List.of());
        given(rewardRedemptionRepository.findByEventId(any())).willReturn(List.of());
        given(idTemplateRepository.findFirstByEventIdAndActiveTrue(any())).willReturn(Optional.empty());
        given(staffAssignmentRepository.findByEventId(any())).willReturn(List.of());
        given(scanPurposeRepository.findByEventId(any())).willReturn(List.of());
    }

    // --- admin/super bypass by rank ----------------------------------------

    @Test
    void event_adminNonOwner_isAllowed() {
        stubLookup(ownerId, EventStatus.APPROVED);
        stubProjectionDependencies();

        OrganizerEventResponse response = organizerService.event(callerId, eventId, AccountRole.ADMIN);

        assertNotNull(response);
    }

    @Test
    void event_superAdminNonOwner_isAllowed() {
        stubLookup(ownerId, EventStatus.APPROVED);
        stubProjectionDependencies();

        OrganizerEventResponse response = organizerService.event(callerId, eventId, AccountRole.SUPER_ADMIN);

        assertNotNull(response);
    }

    // --- organizer ownership ------------------------------------------------

    @Test
    void event_organizerOwner_isAllowed() {
        stubLookup(callerId, EventStatus.APPROVED);
        stubProjectionDependencies();

        OrganizerEventResponse response = organizerService.event(callerId, eventId, AccountRole.ORGANIZER);

        assertNotNull(response);
    }

    @Test
    void event_organizerNonOwner_isForbidden() {
        stubLookup(ownerId, EventStatus.APPROVED);

        assertThrows(ForbiddenException.class, () ->
                organizerService.event(callerId, eventId, AccountRole.ORGANIZER));
    }

    // --- staff denied -------------------------------------------------------

    @Test
    void event_staff_isForbidden() {
        stubLookup(ownerId, EventStatus.APPROVED);

        assertThrows(ForbiddenException.class, () ->
                organizerService.event(callerId, eventId, AccountRole.STAFF));
    }

    @Test
    void event_attendee_isForbidden() {
        stubLookup(ownerId, EventStatus.APPROVED);

        assertThrows(ForbiddenException.class, () ->
                organizerService.event(callerId, eventId, AccountRole.ATTENDEE));
    }

    // --- status gate applies to every role ----------------------------------

    @Test
    void event_adminOnPendingReviewEvent_isForbidden() {
        stubLookup(ownerId, EventStatus.PENDING_REVIEW);

        assertThrows(ForbiddenException.class, () ->
                organizerService.event(callerId, eventId, AccountRole.ADMIN));
    }

    @Test
    void event_adminOnRejectedEvent_isForbidden() {
        stubLookup(ownerId, EventStatus.REJECTED);

        assertThrows(ForbiddenException.class, () ->
                organizerService.event(callerId, eventId, AccountRole.ADMIN));
    }

    @Test
    void event_organizerOwnerOnPendingReviewEvent_isForbidden() {
        stubLookup(callerId, EventStatus.PENDING_REVIEW);

        assertThrows(ForbiddenException.class, () ->
                organizerService.event(callerId, eventId, AccountRole.ORGANIZER));
    }

    @Test
    void event_unknownEvent_isNotFound() {
        given(eventRepository.findById(eventId)).willReturn(Optional.empty());

        assertThrows(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class, () ->
                organizerService.event(callerId, eventId, AccountRole.ADMIN));
    }
}
