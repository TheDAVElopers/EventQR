package com.thedavelopers.eventqr.features.registrations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationSubmissionResponse;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.exceptions.TooManyRequestsException;
import com.thedavelopers.eventqr.shared.security.RegistrationRateLimiter;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort.AttendeeSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort.EventSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {

    @Mock private EventRegistrationRepository registrationRepository;
    @Mock private AttendeeDirectoryPort attendeeDirectoryPort;
    @Mock private NotificationService notificationService;
    @Mock private EventStaffAssignmentRepository staffAssignmentRepository;
    @Mock private EventLookupPort eventLookupPort;
    @Mock private QrCredentialPort qrCredentialPort;
    @Mock private EventService eventService;
    @Mock private QREmailService qrEmailService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private RegistrationRateLimiter registrationRateLimiter;

    private RegistrationService service;

    private static final String IP = "203.0.113.9";
    private final UUID eventId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();
    private final UUID qrId = UUID.randomUUID();
    private final RegistrationRequest request =
            new RegistrationRequest(eventId, "jane@example.com", "Jane Doe", "+639171234567");

    @BeforeEach
    void setUp() {
        service = new RegistrationService(registrationRepository, attendeeDirectoryPort, notificationService,
                staffAssignmentRepository, eventLookupPort, qrCredentialPort, eventService, qrEmailService,
                applicationEventPublisher, registrationRateLimiter,
                mock(com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository.class));
        when(registrationRateLimiter.allow(any(), any())).thenReturn(true);
        // Injected by the container in production; flush/clear are no-ops for these unit tests.
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
    }

    private EventSnapshot event(EventStatus status, Instant opensAt, Instant closesAt, int capacity, int attending) {
        return new EventSnapshot(eventId, "Tech Conf", "Hall A", status, opensAt, closesAt,
                Instant.now().plusSeconds(86_400), Instant.now().plusSeconds(90_000), capacity, attending, false,
                organizerId);
    }

    private EventSnapshot openEvent() {
        return event(EventStatus.APPROVED, Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600), 100, 10);
    }

    private AttendeeSnapshot attendee(UUID userId) {
        return new AttendeeSnapshot(userId, "jane@example.com", "Jane Doe", "+639171234567", AccountRole.ATTENDEE,
                AccountStatus.ACTIVE);
    }

    private QrCredentialSnapshot qr() {
        return new QrCredentialSnapshot(qrId, eventId, attendeeId, UUID.randomUUID(), "qr-value", true,
                QrDisplayStatus.PENDING, QrDeliveryStatus.PENDING, false);
    }

    private EventRegistration registration(UUID id, UUID owner, RegistrationStatus status) {
        EventRegistration r = new EventRegistration();
        r.setId(id);
        r.setEventId(eventId);
        r.setAttendeeUserId(owner);
        r.setAttendeeEmail("jane@example.com");
        r.setAttendeeName("Jane Doe");
        r.setStatus(status);
        r.setRegisteredAt(Instant.now());
        return r;
    }

    private void givenSuccessfulRegistrationDependencies() {
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(openEvent()));
        when(attendeeDirectoryPort.findOrCreateAttendee(any(), any(), any(), any())).thenReturn(attendee(attendeeId));
        when(attendeeDirectoryPort.findById(attendeeId)).thenReturn(Optional.of(attendee(attendeeId)));
        when(registrationRepository.saveAndFlush(any(EventRegistration.class))).thenAnswer(invocation -> {
            EventRegistration saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(registrationRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.of(registration(invocation.getArgument(0), attendeeId, RegistrationStatus.REGISTERED)));
        when(qrCredentialPort.issueOrReturnExisting(any(), any(), any(), any())).thenReturn(qr());
    }

    // ----- register: the rules -----

    @Test
    void registeringForAnOpenEventCreatesTheRegistrationIssuesAQrAndQueuesTheEmail() {
        givenSuccessfulRegistrationDependencies();

        RegistrationSubmissionResponse response = service.register(request);

        assertThat(response.registration().status()).isEqualTo(RegistrationStatus.REGISTERED);
        assertThat(response.registration().attendeeEmail()).isEqualTo("jane@example.com");
        verify(eventService).incrementCurrentAttendeeCount(eventId);
        verify(qrCredentialPort).issueOrReturnExisting(eq(eventId), eq(attendeeId), any(), any());
        verify(applicationEventPublisher).publishEvent(any(Object.class));
        verify(notificationService).createRegistrationConfirmationNotification(eventId, attendeeId, "Tech Conf");
        verify(notificationService).createNewRegistrationNotification(eq(eventId), eq(organizerId), any(), any());
    }

    @Test
    void anUnknownEventIsNotFound() {
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void onlyApprovedOrActiveEventsAcceptRegistrations() {
        for (EventStatus status : List.of(EventStatus.DRAFT, EventStatus.PENDING_REVIEW, EventStatus.REJECTED, EventStatus.CANCELLED,
                EventStatus.ENDED)) {
            when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(
                    event(status, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 100, 0)));

            assertThatThrownBy(() -> service.register(request))
                    .as("status %s", status)
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not open for registration");
        }
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void registrationBeforeTheWindowOpensIsRefused() {
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(
                event(EventStatus.APPROVED, Instant.now().plusSeconds(3_600), Instant.now().plusSeconds(7_200), 100, 0)));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("closed");
    }

    @Test
    void registrationAfterTheWindowClosesIsRefused() {
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(
                event(EventStatus.ACTIVE, Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(60), 100, 0)));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("closed");
    }

    @Test
    void aFullEventRefusesNewRegistrations() {
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(
                event(EventStatus.APPROVED, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 50, 50)));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ConflictException.class).hasMessageContaining("capacity");
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void anEventWithCapacityZeroIsUnlimited() {
        givenSuccessfulRegistrationDependencies();
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(
                event(EventStatus.APPROVED, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 0, 9_999)));

        assertThat(service.register(request).registration().status()).isEqualTo(RegistrationStatus.REGISTERED);
    }

    @Test
    void registeringTwiceWithTheSameEmailIsAConflictEvenIfTheCaseDiffers() {
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(openEvent()));
        when(registrationRepository.findByEventIdAndAttendeeEmailIgnoreCase(eventId, "jane@example.com"))
                .thenReturn(Optional.of(registration(UUID.randomUUID(), attendeeId, RegistrationStatus.REGISTERED)));

        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ConflictException.class);
        verify(attendeeDirectoryPort, never()).findOrCreateAttendee(any(), any(), any(), any());
    }

    @Test
    void theSameUserCannotRegisterTwiceEvenUnderADifferentEmail() {
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(openEvent()));
        when(attendeeDirectoryPort.findOrCreateAttendee(any(), any(), any(), any())).thenReturn(attendee(attendeeId));
        when(registrationRepository.findFirstByEventIdAndAttendeeUserId(eventId, attendeeId))
                .thenReturn(Optional.of(registration(UUID.randomUUID(), attendeeId, RegistrationStatus.REGISTERED)));

        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ConflictException.class);
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void anOrganizerCannotRegisterForTheirOwnEvent() {
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(openEvent()));
        when(attendeeDirectoryPort.findOrCreateAttendee(any(), any(), any(), any())).thenReturn(attendee(organizerId));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("own event");
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void theOrganizerIsToldWhenTheEventFillsUp() {
        givenSuccessfulRegistrationDependencies();
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(
                event(EventStatus.APPROVED, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 10, 9)),
                Optional.of(event(EventStatus.APPROVED, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 10, 10)));

        service.register(request);

        verify(notificationService).createCapacityFullNotification(eventId, organizerId, "Tech Conf", 10, 10);
    }

    @Test
    void theOrganizerGetsAWarningAtEightyPercent() {
        givenSuccessfulRegistrationDependencies();
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(
                event(EventStatus.APPROVED, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 10, 7)),
                Optional.of(event(EventStatus.APPROVED, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 10, 8)));

        service.register(request);

        verify(notificationService).createCapacityWarningNotification(eventId, organizerId, "Tech Conf", 8, 10);
        verify(notificationService, never()).createCapacityFullNotification(any(), any(), any(), anyInt(), anyInt());
    }

    // ----- reactivating a cancelled registration -----

    private EventRegistration givenCancelledRegistration() {
        givenSuccessfulRegistrationDependencies();
        EventRegistration cancelled = registration(UUID.randomUUID(), attendeeId, RegistrationStatus.CANCELLED);
        cancelled.setEnteredAt(Instant.now().minusSeconds(500));
        cancelled.setExitedAt(Instant.now().minusSeconds(400));
        when(registrationRepository.findByEventIdAndAttendeeEmailIgnoreCase(eventId, "jane@example.com"))
                .thenReturn(Optional.of(cancelled));
        when(registrationRepository.updateStatusIfCurrent(cancelled.getId(), "CANCELLED", "REGISTERED")).thenAnswer(inv -> {
            cancelled.setStatus(RegistrationStatus.REGISTERED);
            return 1;
        });
        when(registrationRepository.findById(cancelled.getId())).thenReturn(Optional.of(cancelled));
        when(qrCredentialPort.reissueCredential(any(), any(), any(), any())).thenReturn(qr());
        return cancelled;
    }

    @Test
    void aCancelledRegistrationIsReactivatedWithAFreshQrAndASingleSeat() {
        EventRegistration cancelled = givenCancelledRegistration();

        RegistrationSubmissionResponse response = service.register(request);

        assertThat(response.registration().registrationId()).isEqualTo(cancelled.getId());
        assertThat(cancelled.getStatus()).isEqualTo(RegistrationStatus.REGISTERED);
        assertThat(cancelled.getEnteredAt()).isNull();
        assertThat(cancelled.getExitedAt()).isNull();
        verify(eventService).incrementCurrentAttendeeCount(eventId);
        verify(eventService, never()).decrementCurrentAttendeeCount(any());
        verify(registrationRepository, never()).saveAndFlush(argThatNew());
        verify(qrCredentialPort).reissueCredential(eq(eventId), eq(attendeeId), eq(cancelled.getId()), any());
        verify(qrCredentialPort, never()).issueOrReturnExisting(any(), any(), any(), any());
        verify(notificationService).createRegistrationConfirmationNotification(eventId, attendeeId, "Tech Conf");
        verify(applicationEventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void reactivationAlsoMatchesTheCancelledRowByUserWhenTheEmailDiffers() {
        EventRegistration cancelled = givenCancelledRegistration();
        when(registrationRepository.findByEventIdAndAttendeeEmailIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(registrationRepository.findFirstByEventIdAndAttendeeUserId(eventId, attendeeId)).thenReturn(Optional.of(cancelled));

        service.register(request);

        assertThat(cancelled.getStatus()).isEqualTo(RegistrationStatus.REGISTERED);
        verify(eventService).incrementCurrentAttendeeCount(eventId);
    }

    @Test
    void reactivationOnAFullEventLeavesTheRegistrationCancelled() {
        EventRegistration cancelled = givenCancelledRegistration();
        org.mockito.Mockito.doThrow(new ConflictException("Event is at capacity"))
                .when(eventService).incrementCurrentAttendeeCount(eventId);

        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ConflictException.class);

        assertThat(cancelled.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        verify(registrationRepository, never()).updateStatusIfCurrent(any(), any(), any());
        verify(qrCredentialPort, never()).reissueCredential(any(), any(), any(), any());
        verify(notificationService, never()).createRegistrationConfirmationNotification(any(), any(), any());
    }

    @Test
    void losingTheReactivationRaceReturnsTheSeatAndConflicts() {
        EventRegistration cancelled = givenCancelledRegistration();
        org.mockito.Mockito.doReturn(0).when(registrationRepository)
                .updateStatusIfCurrent(cancelled.getId(), "CANCELLED", "REGISTERED");

        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ConflictException.class);

        verify(eventService).incrementCurrentAttendeeCount(eventId);
        verify(eventService).decrementCurrentAttendeeCount(eventId);
        verify(qrCredentialPort, never()).reissueCredential(any(), any(), any(), any());
    }

    @Test
    void reactivationFollowsTheRegistrationWindowAndEventStatusRules() {
        givenCancelledRegistration();
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(
                event(EventStatus.APPROVED, Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(60), 100, 0)));
        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ForbiddenException.class);

        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(
                event(EventStatus.ENDED, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 100, 0)));
        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ForbiddenException.class);

        verify(eventService, never()).incrementCurrentAttendeeCount(any());
    }

    @Test
    void anOrganizerWithACancelledRowStillCannotRegisterForTheirOwnEvent() {
        givenCancelledRegistration();
        when(attendeeDirectoryPort.findOrCreateAttendee(any(), any(), any(), any())).thenReturn(attendee(organizerId));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("own event");
        verify(eventService, never()).incrementCurrentAttendeeCount(any());
    }

    private static EventRegistration argThatNew() {
        return org.mockito.ArgumentMatchers.argThat(r -> r != null && r.getId() == null);
    }

    // ----- lookup and ownership -----

    @Test
    void findOneForAttendeeReturnsOnlyTheCallersOwnRegistration() {
        UUID id = UUID.randomUUID();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(registration(id, attendeeId, RegistrationStatus.REGISTERED)));
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(openEvent()));

        RegistrationResponse own = service.findOneForAttendee(id, attendeeId);

        assertThat(own.registrationId()).isEqualTo(id);
        assertThatThrownBy(() -> service.findOneForAttendee(id, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void findOneOfAnUnknownRegistrationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(registrationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOne(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- cancel -----

    @Test
    void cancellingFreesTheSeatExactlyOnce() {
        UUID id = UUID.randomUUID();
        EventRegistration registration = registration(id, attendeeId, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(id)).thenReturn(Optional.of(registration));
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(openEvent()));
        when(registrationRepository.updateStatusIfCurrent(id, "REGISTERED", "CANCELLED")).thenAnswer(inv -> {
            registration.setStatus(RegistrationStatus.CANCELLED);
            return 1;
        });

        service.cancel(id, attendeeId);
        service.cancel(id, attendeeId);

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        verify(eventService).decrementCurrentAttendeeCount(eventId);
    }

    @Test
    void cancellingACheckedInRegistrationIsRefused() {
        UUID id = UUID.randomUUID();
        EventRegistration registration = registration(id, attendeeId, RegistrationStatus.ENTERED);
        when(registrationRepository.findById(id)).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.cancel(id, attendeeId)).isInstanceOf(ConflictException.class);
        verify(eventService, never()).decrementCurrentAttendeeCount(any());
    }

    @Test
    void cancellingDeactivatesTheQrCredential() {
        UUID id = UUID.randomUUID();
        EventRegistration registration = registration(id, attendeeId, RegistrationStatus.REGISTERED);
        registration.setQrCredentialId(qrId);
        when(registrationRepository.findById(id)).thenReturn(Optional.of(registration));
        when(qrCredentialPort.findById(qrId)).thenReturn(Optional.of(qr()));
        when(eventLookupPort.findById(eventId)).thenReturn(Optional.of(openEvent()));

        service.cancel(id, attendeeId);

        verify(qrCredentialPort).deactivate(qrId);
        verify(qrCredentialPort, never()).markEmailQueued(any());
    }

    @Test
    void youCannotCancelSomeoneElsesRegistration() {
        UUID id = UUID.randomUUID();
        when(registrationRepository.findById(id)).thenReturn(Optional.of(registration(id, attendeeId, RegistrationStatus.REGISTERED)));

        assertThatThrownBy(() -> service.cancel(id, UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
        verify(eventService, never()).decrementCurrentAttendeeCount(any());
    }

    // ----- attendance marking -----

    @Test
    void enteringExitingAndAttendingUpdateTheRegistration() {
        UUID id = UUID.randomUUID();
        EventRegistration registration = registration(id, attendeeId, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(id)).thenReturn(Optional.of(registration));

        service.markEntered(id);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.ENTERED);
        assertThat(registration.getEnteredAt()).isNotNull();

        service.markExited(id);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.EXITED);
        assertThat(registration.getExitedAt()).isNotNull();

        service.markAttended(id);
        assertThat(registration.getAttendedAt()).isNotNull();
    }

    @Test
    void pointsAccumulateAndATolerateAMissingBalance() {
        UUID id = UUID.randomUUID();
        EventRegistration registration = registration(id, attendeeId, RegistrationStatus.REGISTERED);
        registration.setPointsEarned(null);
        when(registrationRepository.findById(id)).thenReturn(Optional.of(registration));

        service.addPoints(id, 10);
        service.addPoints(id, 5);

        assertThat(registration.getPointsEarned()).isEqualTo(15);
    }

    @Test
    void markingAnUnknownRegistrationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(registrationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markEntered(id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.addPoints(id, 1)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- registerAs: the registration email must belong to the caller -----

    @Test
    void anAttendeeCanRegisterTheirOwnEmailCaseInsensitively() {
        givenSuccessfulRegistrationDependencies();
        RegistrationRequest own = new RegistrationRequest(eventId, "  JANE@Example.com ", "Jane Doe", "+639171234567");

        assertThat(service.registerAs(own, attendeeId, AccountRole.ATTENDEE, IP).registration().status())
                .isEqualTo(RegistrationStatus.REGISTERED);
    }

    @Test
    void anAttendeeCannotRegisterSomeoneElsesEmailAndNoProfileIsCreated() {
        when(attendeeDirectoryPort.findById(attendeeId)).thenReturn(Optional.of(attendee(attendeeId)));
        RegistrationRequest foreign = new RegistrationRequest(eventId, "victim@example.com", "Victim", null);

        assertThatThrownBy(() -> service.registerAs(foreign, attendeeId, AccountRole.ATTENDEE, IP))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only register using your own account email");
        verify(attendeeDirectoryPort, never()).findOrCreateAttendee(any(), any(), any(), any());
        verify(eventLookupPort, never()).findById(any());
        verify(registrationRepository, never()).existsByEventIdAndAttendeeEmailIgnoreCase(any(), any());
    }

    @Test
    void theRefusalIsIdenticalWhetherOrNotTheForeignEmailIsAlreadyRegisteredOrKnown() {
        when(attendeeDirectoryPort.findById(attendeeId)).thenReturn(Optional.of(attendee(attendeeId)));
        RegistrationRequest foreign = new RegistrationRequest(eventId, "victim@example.com", "Victim", null);

        // Case 1: the foreign email has a profile and is already registered for the event.
        when(registrationRepository.existsByEventIdAndAttendeeEmailIgnoreCase(any(), any())).thenReturn(true);
        when(registrationRepository.existsByEventIdAndAttendeeUserId(any(), any())).thenReturn(true);
        when(attendeeDirectoryPort.findByEmail("victim@example.com")).thenReturn(Optional.of(attendee(UUID.randomUUID())));
        Throwable registered = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.registerAs(foreign, attendeeId, AccountRole.ATTENDEE, IP));

        // Case 2: the foreign email is entirely unknown and unregistered.
        when(registrationRepository.existsByEventIdAndAttendeeEmailIgnoreCase(any(), any())).thenReturn(false);
        when(registrationRepository.existsByEventIdAndAttendeeUserId(any(), any())).thenReturn(false);
        when(attendeeDirectoryPort.findByEmail("victim@example.com")).thenReturn(Optional.empty());
        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.registerAs(foreign, attendeeId, AccountRole.ATTENDEE, IP));

        assertThat(registered).isInstanceOf(ForbiddenException.class);
        assertThat(unknown).isInstanceOf(registered.getClass()).hasMessage(registered.getMessage());
        // The lookups that could tell the two cases apart are never reached.
        verify(registrationRepository, never()).existsByEventIdAndAttendeeEmailIgnoreCase(any(), any());
        verify(registrationRepository, never()).existsByEventIdAndAttendeeUserId(any(), any());
        verify(attendeeDirectoryPort, never()).findByEmail(any());
        verify(attendeeDirectoryPort, never()).findOrCreateAttendee(any(), any(), any(), any());
    }

    @Test
    void aForeignEmailIsRefusedBeforeTheRateLimiterSoNoBucketIsConsumed() {
        when(attendeeDirectoryPort.findById(attendeeId)).thenReturn(Optional.of(attendee(attendeeId)));
        RegistrationRequest foreign = new RegistrationRequest(eventId, "victim@example.com", "Victim", null);

        for (int i = 0; i < 50; i++) {
            assertThatThrownBy(() -> service.registerAs(foreign, attendeeId, AccountRole.ATTENDEE, IP))
                    .isInstanceOf(ForbiddenException.class);
        }

        verify(registrationRateLimiter, never()).allow(any(), any());
    }

    @Test
    void theRateLimiterIsKeyedOnTheClientIpAndTheCallerNeverTheBodyEmail() {
        givenSuccessfulRegistrationDependencies();

        service.registerAs(request, attendeeId, AccountRole.ATTENDEE, IP);

        verify(registrationRateLimiter).allow(IP, attendeeId);
    }

    @Test
    void anAdminRegisteringOnBehalfIsLimitedAgainstTheAdminNotTheTargetEmail() {
        givenSuccessfulRegistrationDependencies();
        UUID adminId = UUID.randomUUID();
        RegistrationRequest onBehalf = new RegistrationRequest(eventId, "other@example.com", "Other", null);

        service.registerAs(onBehalf, adminId, AccountRole.ADMIN, IP);

        verify(registrationRateLimiter).allow(IP, adminId);
    }

    @Test
    void whenTheLimiterRejectsTheRequestIs429AndNothingIsLookedUpOrCreated() {
        when(attendeeDirectoryPort.findById(attendeeId)).thenReturn(Optional.of(attendee(attendeeId)));
        when(registrationRateLimiter.allow(IP, attendeeId)).thenReturn(false);

        assertThatThrownBy(() -> service.registerAs(request, attendeeId, AccountRole.ATTENDEE, IP))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many registration requests. Please try again later.");

        verify(eventLookupPort, never()).findById(any());
        verify(attendeeDirectoryPort, never()).findOrCreateAttendee(any(), any(), any(), any());
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void aThrottledCallerDoesNotStopAnotherUserFromRegisteringNormally() {
        givenSuccessfulRegistrationDependencies();
        UUID throttled = UUID.randomUUID();
        when(registrationRateLimiter.allow(IP, throttled)).thenReturn(false);
        when(attendeeDirectoryPort.findById(throttled)).thenReturn(Optional.of(attendee(throttled)));

        assertThatThrownBy(() -> service.registerAs(request, throttled, AccountRole.ATTENDEE, IP))
                .isInstanceOf(TooManyRequestsException.class);

        assertThat(service.registerAs(request, attendeeId, AccountRole.ATTENDEE, "198.51.100.7").registration().status())
                .isEqualTo(RegistrationStatus.REGISTERED);
    }

    @Test
    void aCallerWithoutAProfileIsRefused() {
        when(attendeeDirectoryPort.findById(attendeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerAs(request, attendeeId, AccountRole.ATTENDEE, IP))
                .isInstanceOf(ForbiddenException.class);
        verify(attendeeDirectoryPort, never()).findOrCreateAttendee(any(), any(), any(), any());
    }

    @Test
    void anAdminMayRegisterAnotherEmailOnBehalf() {
        givenSuccessfulRegistrationDependencies();
        RegistrationRequest onBehalf = new RegistrationRequest(eventId, "other@example.com", "Other", null);

        assertThat(service.registerAs(onBehalf, UUID.randomUUID(), AccountRole.ADMIN, IP).registration().status())
                .isEqualTo(RegistrationStatus.REGISTERED);
        verify(attendeeDirectoryPort).findOrCreateAttendee(eq("other@example.com"), any(), any(), any());
    }

    @Test
    void lockForUpdateRefreshesTheCachedRowUnderTheLockAndReturnsTheLockedState() {
        EntityManager entityManager = mock(EntityManager.class);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        UUID registrationId = UUID.randomUUID();
        EventRegistration cached = new EventRegistration();
        cached.setId(registrationId);
        cached.setEventId(eventId);
        cached.setAttendeeUserId(attendeeId);
        cached.setStatus(RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(registrationId)).thenReturn(Optional.of(cached));
        // A concurrent cancel committed before we got the lock: the locking refresh re-reads the row.
        org.mockito.Mockito.doAnswer(inv -> {
            cached.setStatus(RegistrationStatus.CANCELLED);
            return null;
        }).when(entityManager).refresh(cached, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        var locked = service.lockForUpdate(registrationId);

        assertThat(locked.status()).isEqualTo(RegistrationStatus.CANCELLED);
        var order = org.mockito.Mockito.inOrder(registrationRepository, entityManager);
        order.verify(registrationRepository).boundLockWaits();
        order.verify(entityManager).refresh(cached, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
}
