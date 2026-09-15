package com.thedavelopers.eventqr.features.registrations.service;

import com.thedavelopers.eventqr.features.events.dto.EventLookupPort;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.shared.constants.EventStatus;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdPrintJob;
import com.thedavelopers.eventqr.features.registrations.dto.RegistrationSubmissionResponse;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.features.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.features.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.events.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class for RegistrationService.
 */
@WebMvcTest(RegistrationService.class)
@Import({EventService.class, NotificationService.class, QREmailService.class})
class RegistrationServiceTest {

    @Autowired
    private RegistrationService registrationService;

    @MockBean
    private EventRegistrationRepository registrationRepository;

    @MockBean
    private AttendeeDirectoryPort attendeeDirectoryPort;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private EventStaffAssignmentRepository staffAssignmentRepository;

    @MockBean
    private EventLookupPort eventLookupPort;

    @MockBean
    private QrCredentialPort qrCredentialPort;

    @MockBean
    private EventService eventService;

    @MockBean
    private QREmailService qrEmailService;

    @MockBean
    private ApplicationEventPublisher applicationEventPublisher;

    private UUID eventId;
    private UUID attendeeId;
    private UUID registrationId;
    private Event testEvent;
    private UserProfile testUserProfile;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        attendeeId = UUID.randomUUID();
        registrationId = UUID.randomUUID();
        
        testEvent = new Event();
        testEvent.setId(eventId);
        testEvent.setTitle("Tech Conference 2026");
        testEvent.setDescription("Annual tech conference");
        testEvent.setCategory("Technology");
        testEvent.setLocation("Convention Center");
        testEvent.setEventLogoUrl("http://example.com/logo.png");
        testEvent.setRegistrationOpenAt(Instant.now().minusSeconds(86400));
        testEvent.setRegistrationCloseAt(Instant.now().plusSeconds(86400 * 30));
        testEvent.setEventStartAt(Instant.now().plusSeconds(86400 * 60));
        testEvent.setEventEndAt(Instant.now().plusSeconds(86400 * 90));
        testEvent.setCapacity(100);
        testEvent.setRewardsEnabled(true);
        testEvent.setStatus(EventStatus.APPROVED);
        testEvent.setOrganizerUserId(UUID.randomUUID());
        testEvent.setCurrentAttendeeCount(0);
        
        testUserProfile = new UserProfile();
        testUserProfile.setId(attendeeId);
        testUserProfile.setEmail("attendee@example.com");
        testUserProfile.setFullName("Test Attendee");
        testUserProfile.setPhoneNumber("+1234567890");
        testUserProfile.setPasswordHash("$2a$10$hashed.password");
        testUserProfile.setRole(AccountRole.ATTENDEE);
        testUserProfile.setStatus(com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE);
    }

    @Test
    void testRegister_Success() throws Exception {
        // Arrange
        RegistrationRequest request = new RegistrationRequest(
                eventId,
                "attendee@example.com",
                "Test Attendee",
                "+1234567890"
        );
        
        String normalizedEmail = "attendee@example.com";
        AttendeeDirectoryPort.AttendeeSnapshot attendeeSnapshot = new AttendeeDirectoryPort.AttendeeSnapshot(
                attendeeId, normalizedEmail, "Test Attendee", "+1234567890", AccountRole.ATTENDEE, 
                com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE);
        
        EventLookupPort.EventSnapshot eventSnapshot = new EventLookupPort.EventSnapshot(
                eventId, "Tech Conference 2026", "Convention Center", EventStatus.APPROVED,
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60), Instant.now().plusSeconds(86400 * 90),
                100, 0, true, UUID.randomUUID());
        
        EventRegistration registration = new EventRegistration();
        registration.setId(registrationId);
        registration.setEventId(eventId);
        registration.setAttendeeUserId(attendeeId);
        registration.setAttendeeEmail(normalizedEmail);
        registration.setAttendeeName("Test Attendee");
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setRegisteredAt(Instant.now());
        
        given(eventLookupPort.findById(eventId)).willReturn(Optional.of(eventSnapshot));
        given(registrationRepository.existsByEventIdAndAttendeeEmailIgnoreCase(eventId, normalizedEmail)).willReturn(false);
        given(attendeeDirectoryPort.findOrCreateAttendee(normalizedEmail, "Test Attendee", "+1234567890", AccountRole.ATTENDEE))
                .willReturn(attendeeSnapshot);
        given(registrationRepository.existsByEventIdAndAttendeeUserId(eventId, attendeeId)).willReturn(false);
        given(registrationRepository.saveAndFlush(any(EventRegistration.class))).willReturn(registration);
        given(registrationRepository.findById(registrationId)).willReturn(Optional.of(registration));
        given(eventService.incrementCurrentAttendeeCount(eventId)).willReturn(undefined);
        given(qrCredentialPort.issueCredential(any(UUID.class), any(UUID.class), any(UUID.class), anyString()))
                .willReturn(new QrCredentialPort.QrCredentialSnapshot(
                        UUID.randomUUID(), eventId, attendeeId, registrationId,
                        "qr-value-123", true, QrDisplayStatus.NOT_SHOWN, QrDeliveryStatus.PENDING, false));

        // Act
        RegistrationSubmissionResponse result = registrationService.register(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.registrationId()).isEqualTo(registrationId);
        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.attendeeName()).isEqualTo("Test Attendee");
        assertThat(result.qrValue()).isEqualTo("qr-value-123");
        then(eventLookupPort).should().findById(eventId);
        then(registrationRepository).should().existsByEventIdAndAttendeeEmailIgnoreCase(eventId, normalizedEmail);
        then(attendeeDirectoryPort).should().findOrCreateAttendee(normalizedEmail, "Test Attendee", "+1234567890", AccountRole.ATTENDEE);
        then(registrationRepository).should().existsByEventIdAndAttendeeUserId(eventId, attendeeId);
        then(registrationRepository).should().saveAndFlush(any(EventRegistration.class));
        then(eventService).should().incrementCurrentAttendeeCount(eventId);
        then(qrEmailService).should().sendForRegistration(registrationId);
    }

    @Test
    void testRegister_EventNotFound() throws Exception {
        // Arrange
        RegistrationRequest request = new RegistrationRequest(
                eventId,
                "attendee@example.com",
                "Test Attendee",
                "+1234567890"
        );
        
        given(eventLookupPort.findById(eventId)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class)
                .hasMessageContaining("Event not found");
    }

    @Test
    void testRegister_EventNotOpenForRegistration() throws Exception {
        // Arrange
        RegistrationRequest request = new RegistrationRequest(
                eventId,
                "attendee@example.com",
                "Test Attendee",
                "+1234567890"
        );
        
        String normalizedEmail = "attendee@example.com";
        EventLookupPort.EventSnapshot eventSnapshot = new EventLookupPort.EventSnapshot(
                eventId, "Tech Conference 2026", "Convention Center", EventStatus.DRAFT,
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60), Instant.now().plusSeconds(86400 * 90),
                100, 0, true, UUID.randomUUID());
        
        given(eventLookupPort.findById(eventId)).willReturn(Optional.of(eventSnapshot));

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ForbiddenException.class)
                .hasMessageContaining("Event is not open for registration");
    }

    @Test
    void testRegister_RegistrationClosed() throws Exception {
        // Arrange
        RegistrationRequest request = new RegistrationRequest(
                eventId,
                "attendee@example.com",
                "Test Attendee",
                "+1234567890"
        );
        
        String normalizedEmail = "attendee@example.com";
        EventLookupPort.EventSnapshot eventSnapshot = new EventLookupPort.EventSnapshot(
                eventId, "Tech Conference 2026", "Convention Center", EventStatus.APPROVED,
                Instant.now().plusSeconds(86400), Instant.now().plusSeconds(86400 * 2),
                Instant.now().plusSeconds(86400 * 3), Instant.now().plusSeconds(86400 * 4),
                100, 0, true, UUID.randomUUID());
        
        given(eventLookupPort.findById(eventId)).willReturn(Optional.of(eventSnapshot));

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ForbiddenException.class)
                .hasMessageContaining("Registration is closed");
    }

    @Test
    void testRegister_EventAtCapacity() throws Exception {
        // Arrange
        RegistrationRequest request = new RegistrationRequest(
                eventId,
                "attendee@example.com",
                "Test Attendee",
                "+1234567890"
        );
        
        String normalizedEmail = "attendee@example.com";
        EventLookupPort.EventSnapshot eventSnapshot = new EventLookupPort.EventSnapshot(
                eventId, "Tech Conference 2026", "Convention Center", EventStatus.APPROVED,
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60), Instant.now().plusSeconds(86400 * 90),
                100, 100, true, UUID.randomUUID()); // At capacity
        
        given(eventLookupPort.findById(eventId)).willReturn(Optional.of(eventSnapshot));

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ConflictException.class)
                .hasMessageContaining("Event is at capacity");
    }

    @Test
    void testRegister_DuplicateRegistrationByEmail() throws Exception {
        // Arrange
        RegistrationRequest request = new RegistrationRequest(
                eventId,
                "attendee@example.com",
                "Test Attendee",
                "+1234567890"
        );
        
        String normalizedEmail = "attendee@example.com";
        EventLookupPort.EventSnapshot eventSnapshot = new EventLookupPort.EventSnapshot(
                eventId, "Tech Conference 2026", "Convention Center", EventStatus.APPROVED,
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60), Instant.now().plusSeconds(86400 * 90),
                100, 0, true, UUID.randomUUID());
        
        AttendeeDirectoryPort.AttendeeSnapshot attendeeSnapshot = new AttendeeDirectoryPort.AttendeeSnapshot(
                attendeeId, normalizedEmail, "Test Attendee", "+1234567890", AccountRole.ATTENDEE, 
                com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE);
        
        given(eventLookupPort.findById(eventId)).willReturn(Optional.of(eventSnapshot));
        given(registrationRepository.existsByEventIdAndAttendeeEmailIgnoreCase(eventId, normalizedEmail)).willReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ConflictException.class)
                .hasMessageContaining("Duplicate registration for this event and attendee");
    }

    @Test
    void testRegister_OrganizerCannotRegisterOwnEvent() throws Exception {
        // Arrange
        RegistrationRequest request = new RegistrationRequest(
                eventId,
                "organizer@example.com",
                "Test Organizer",
                "+1234567890"
        );
        
        String normalizedEmail = "organizer@example.com";
        UUID organizerId = UUID.randomUUID();
        EventLookupPort.EventSnapshot eventSnapshot = new EventLookupPort.EventSnapshot(
                eventId, "Tech Conference 2026", "Convention Center", EventStatus.APPROVED,
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60), Instant.now().plusSeconds(86400 * 90),
                100, 0, true, organizerId); // Organizer is the event owner
        
        AttendeeDirectoryPort.AttendeeSnapshot attendeeSnapshot = new AttendeeDirectoryPort.AttendeeSnapshot(
                organizerId, normalizedEmail, "Test Organizer", "+1234567890", AccountRole.ATTENDEE, 
                com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE);
        
        given(eventLookupPort.findById(eventId)).willReturn(Optional.of(eventSnapshot));
        given(registrationRepository.existsByEventIdAndAttendeeEmailIgnoreCase(eventId, normalizedEmail)).willReturn(false);
        given(attendeeDirectoryPort.findOrCreateAttendee(normalizedEmail, "Test Organizer", "+1234567890", AccountRole.ATTENDEE))
                .willReturn(attendeeSnapshot);

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ForbiddenException.class)
                .hasMessageContaining("Organizers cannot register for their own event");
    }

    @Test
    void testFindOne_Success() throws Exception {
        // Arrange
        EventRegistration registration = new EventRegistration();
        registration.setId(registrationId);
        registration.setEventId(eventId);
        registration.setAttendeeUserId(attendeeId);
        registration.setAttendeeEmail("attendee@example.com");
        registration.setAttendeeName("Test Attendee");
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setRegisteredAt(Instant.now());
        
        given(registrationRepository.findById(registrationId)).willReturn(Optional.of(registration));
        given(eventService.findOne(eventId)).willReturn(new com.thedavelopers.eventqr.features.events.dto.EventResponse(
                eventId, "Tech Conference 2026", "Description", "Technology", "Location",
                "http://example.com/logo.png", Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400 * 30), Instant.now().plusSeconds(86400 * 60),
                Instant.now().plusSeconds(86400 * 90), 100, true, EventStatus.APPROVED, UUID.randomUUID()));
        given(qrCredentialPort.findByRegistrationId(registrationId)).willReturn(Optional.of(
                new QrCredentialPort.QrCredentialSnapshot(
                        UUID.randomUUID(), eventId, attendeeId, registrationId,
                        "qr-value-123", true, QrDisplayStatus.NOT_SHOWN, QrDeliveryStatus.PENDING, false)));

        // Act
        com.thedavelopers.eventqr.features.registrations.dto.RegistrationResponse result = 
                registrationService.findOne(registrationId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.registrationId()).isEqualTo(registrationId);
        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.attendeeName()).isEqualTo("Test Attendee");
        assertThat(result.status()).isEqualTo(RegistrationStatus.REGISTERED);
        then(registrationRepository).should().findById(registrationId);
        then(eventService).should().findOne(eventId);
        then(qrCredentialPort).should().findByRegistrationId(registrationId);
    }

    @Test
    void testFindOne_RegistrationNotFound() throws Exception {
        // Arrange
        given(registrationRepository.findById(registrationId)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> registrationService.findOne(registrationId))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class)
                .hasMessageContaining("Registration not found");
    }

    @Test
    void testFindRegistrationsByEventId_Success() throws Exception {
        // Arrange
        EventRegistration registration1 = new EventRegistration();
        registration1.setId(UUID.randomUUID());
        registration1.setEventId(eventId);
        registration1.setAttendeeUserId(attendeeId);
        registration1.setAttendeeEmail("attendee1@example.com");
        registration1.setAttendeeName("Attendee One");
        registration1.setStatus(RegistrationStatus.REGISTERED);
        registration1.setRegisteredAt(Instant.now());
        
        EventRegistration registration2 = new EventRegistration();
        registration2.setId(UUID.randomUUID());
        registration2.setEventId(eventId);
        registration2.setAttendeeUserId(UUID.randomUUID());
        registration2.setAttendeeEmail("attendee2@example.com");
        registration2.setAttendeeName("Attendee Two");
        registration2.setStatus(RegistrationStatus.REGISTERED);
        registration2.setRegisteredAt(Instant.now().minusSeconds(3600));
        
        given(registrationRepository.findByEventId(eventId)).willReturn(List.of(registration1, registration2));
        given(eventService.findOne(eventId)).willReturn(new com.thedavelopers.eventqr.features.events.dto.EventResponse(
                eventId, "Tech Conference 2026", "Description", "Technology", "Location",
                "http://example.com/logo.png", Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400 * 30), Instant.now().plusSeconds(86400 * 60),
                Instant.now().plusSeconds(86400 * 90), 100, true, EventStatus.APPROVED, UUID.randomUUID()));

        // Act
        List<com.thedavelopers.eventqr.features.registrations.dto.RegistrationResponse> result = 
                registrationService.findByEventId(eventId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).attendeeName()).isEqualTo("Attendee One");
        assertThat(result.get(1).attendeeName()).isEqualTo("Attendee Two");
        then(registrationRepository).should().findByEventId(eventId);
        then(eventService).should().findOne(eventId);
    }

    @Test
    void testCheckInAttendee_Success() throws Exception {
        // Arrange
        EventRegistration registration = new EventRegistration();
        registration.setId(registrationId);
        registration.setEventId(eventId);
        registration.setAttendeeUserId(attendeeId);
        registration.setAttendeeEmail("attendee@example.com");
        registration.setAttendeeName("Test Attendee");
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setRegisteredAt(Instant.now());
        
        given(registrationRepository.findById(registrationId)).willReturn(Optional.of(registration));
        given(registrationRepository.save(any(EventRegistration.class))).willAnswer(invocation -> invocation.getArgument(0));

        // Act
        registrationService.checkInAttendee(registrationId);

        // Assert
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CHECKED_IN);
        assertThat(registration.getEnteredAt()).isNotNull();
        then(registrationRepository).should().findById(registrationId);
        then(registrationRepository).should().save(any(EventRegistration.class));
    }

    @Test
    void testCheckOutAttendee_Success() throws Exception {
        // Arrange
        EventRegistration registration = new EventRegistration();
        registration.setId(registrationId);
        registration.setEventId(eventId);
        registration.setAttendeeUserId(attendeeId);
        registration.setAttendeeEmail("attendee@example.com");
        registration.setAttendeeName("Test Attendee");
        registration.setStatus(RegistrationStatus.CHECKED_IN);
        registration.setRegisteredAt(Instant.now());
        registration.setEnteredAt(Instant.now().minusSeconds(3600));
        
        given(registrationRepository.findById(registrationId)).willReturn(Optional.of(registration));
        given(registrationRepository.save(any(EventRegistration.class))).willAnswer(invocation -> invocation.getArgument(0));

        // Act
        registrationService.checkOutAttendee(registrationId);

        // Assert
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CHECKED_OUT);
        assertThat(registration.getExitedAt()).isNotNull();
        then(registrationRepository).should().findById(registrationId);
        then(registrationRepository).should().save(any(EventRegistration.class));
    }

    @Test
    void testMarkAttended_Success() throws Exception {
        // Arrange
        EventRegistration registration = new EventRegistration();
        registration.setId(registrationId);
        registration.setEventId(eventId);
        registration.setAttendeeUserId(attendeeId);
        registration.setAttendeeEmail("attendee@example.com");
        registration.setAttendeeName("Test Attendee");
        registration.setStatus(RegistrationStatus.CHECKED_OUT);
        registration.setRegisteredAt(Instant.now());
        registration.setEnteredAt(Instant.now().minusSeconds(7200));
        registration.setExitedAt(Instant.now().minusSeconds(3600));
        
        given(registrationRepository.findById(registrationId)).willReturn(Optional.of(registration));
        given(registrationRepository.save(any(EventRegistration.class))).willAnswer(invocation -> invocation.getArgument(0));

        // Act
        registrationService.markAttended(registrationId);

        // Assert
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.ATTENDED);
        assertThat(registration.getAttendedAt()).isNotNull();
        then(registrationRepository).should().findById(registrationId);
        then(registrationRepository).should().save(any(EventRegistration.class));
    }
}