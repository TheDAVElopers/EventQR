package com.thedavelopers.eventqr.features.registrations.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.events.controller.EventController;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.qrcredentials.service.QrCredentialService;
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort.AttendeeSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort.EventSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.security.RegistrationRateLimiter;

import jakarta.persistence.EntityManager;

/**
 * Both registration endpoints against the real RegistrationService and the real limiter (only the ports are
 * mocked): ownership is checked before throttling, the limiter is keyed on caller and IP, and the
 * 403 for a foreign email is the same whether or not that email exists or is registered.
 */
class RegistrationRateLimitWebTest {

    private static final String ALICE = "Bearer alice";
    private static final String BOB = "Bearer bob";
    private static final String ADMIN = "Bearer admin";
    private static final String OWN_EMAIL_MESSAGE = "You can only register using your own account email";

    private final UUID eventId = UUID.randomUUID();
    private final UUID aliceId = UUID.randomUUID();
    private final UUID bobId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private AttendeeDirectoryPort directory;
    private EventRegistrationRepository registrations;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        directory = mock(AttendeeDirectoryPort.class);
        registrations = mock(EventRegistrationRepository.class);
        EventLookupPort events = mock(EventLookupPort.class);
        QrCredentialPort qrPort = mock(QrCredentialPort.class);
        EventService eventService = mock(EventService.class);
        JwtService jwt = mock(JwtService.class);

        RegistrationService registrationService = new RegistrationService(registrations, directory,
                mock(NotificationService.class), mock(EventStaffAssignmentRepository.class), events, qrPort,
                eventService, mock(QREmailService.class), mock(ApplicationEventPublisher.class),
                new RegistrationRateLimiter(),
                mock(com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository.class));
        ReflectionTestUtils.setField(registrationService, "entityManager", mock(EntityManager.class));

        mvc = MockMvcBuilders.standaloneSetup(
                        new RegistrationController(registrationService, mock(QrCredentialService.class),
                                mock(QREmailService.class), jwt, eventService, mock(EventStaffAssignmentRepository.class)),
                        new EventController(eventService, registrationService, jwt))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(jwt.extractUserIdFromBearer(ALICE)).thenReturn(aliceId);
        when(jwt.extractRoleFromBearer(ALICE)).thenReturn(AccountRole.ATTENDEE);
        when(jwt.extractUserIdFromBearer(BOB)).thenReturn(bobId);
        when(jwt.extractRoleFromBearer(BOB)).thenReturn(AccountRole.ATTENDEE);
        when(jwt.extractUserIdFromBearer(ADMIN)).thenReturn(adminId);
        when(jwt.extractRoleFromBearer(ADMIN)).thenReturn(AccountRole.ADMIN);

        when(directory.findById(aliceId)).thenReturn(Optional.of(profile(aliceId, "alice@example.com")));
        when(directory.findById(bobId)).thenReturn(Optional.of(profile(bobId, "bob@example.com")));
        when(directory.findOrCreateAttendee(any(), any(), any(), any())).thenAnswer(inv -> {
            String email = inv.getArgument(0);
            UUID id = email.startsWith("alice") ? aliceId : email.startsWith("bob") ? bobId : UUID.randomUUID();
            return profile(id, email);
        });
        when(events.findById(eventId)).thenReturn(Optional.of(new EventSnapshot(eventId, "Tech Conf", "Hall A",
                EventStatus.APPROVED, Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600),
                Instant.now().plusSeconds(86_400), Instant.now().plusSeconds(90_000), 1_000, 0, false, UUID.randomUUID())));
        when(registrations.saveAndFlush(any(EventRegistration.class))).thenAnswer(inv -> {
            EventRegistration saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(registrations.findById(any(UUID.class))).thenAnswer(inv -> {
            EventRegistration r = new EventRegistration();
            r.setId(inv.getArgument(0));
            r.setEventId(eventId);
            r.setAttendeeUserId(aliceId);
            r.setAttendeeEmail("alice@example.com");
            r.setAttendeeName("Name");
            r.setStatus(RegistrationStatus.REGISTERED);
            r.setRegisteredAt(Instant.now());
            return Optional.of(r);
        });
        when(qrPort.issueOrReturnExisting(any(), any(), any(), any())).thenReturn(new QrCredentialSnapshot(
                UUID.randomUUID(), eventId, aliceId, UUID.randomUUID(), "qr", true, QrDisplayStatus.PENDING,
                QrDeliveryStatus.PENDING, false));
    }

    private AttendeeSnapshot profile(UUID id, String email) {
        return new AttendeeSnapshot(id, email, "Name", null, AccountRole.ATTENDEE, AccountStatus.ACTIVE);
    }

    private String body(String email) {
        return "{\"eventId\":\"" + eventId + "\",\"email\":\"" + email + "\",\"fullName\":\"Name\"}";
    }

    private ResultActions viaRegistrations(String auth, String ip, String email) throws Exception {
        return send(post("/api/v1/registrations"), auth, ip, email);
    }

    private ResultActions viaEvents(String auth, String ip, String email) throws Exception {
        return send(post("/api/v1/events/{id}/registrations", eventId), auth, ip, email);
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String auth, String ip, String email) throws Exception {
        return mvc.perform(builder.header("Authorization", auth).header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content(body(email)));
    }

    private static String normalised(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString()
                .replaceAll("\"timestamp\":[^,]*,", "\"timestamp\":\"-\",");
        return actions.andReturn().getResponse().getStatus() + "|" + body;
    }

    @Test
    void theForeignEmail403BodyIsIdenticalWhetherOrNotThatEmailIsKnownOrRegistered() throws Exception {
        when(directory.findByEmail("registered@example.com")).thenReturn(Optional.of(profile(UUID.randomUUID(), "registered@example.com")));
        when(registrations.existsByEventIdAndAttendeeEmailIgnoreCase(any(), any())).thenReturn(true);

        String registered = normalised(viaRegistrations(ALICE, "203.0.113.1", "registered@example.com"));
        when(registrations.existsByEventIdAndAttendeeEmailIgnoreCase(any(), any())).thenReturn(false);
        when(directory.findByEmail(any())).thenReturn(Optional.empty());
        String unknown = normalised(viaRegistrations(ALICE, "203.0.113.1", "nobody@example.com"));

        assertThat(registered).startsWith("403|").contains(OWN_EMAIL_MESSAGE);
        assertThat(unknown).isEqualTo(registered);

        String viaEventsRegistered = normalised(viaEvents(ALICE, "203.0.113.1", "registered@example.com"));
        String viaEventsUnknown = normalised(viaEvents(ALICE, "203.0.113.1", "nobody@example.com"));
        assertThat(viaEventsUnknown).isEqualTo(viaEventsRegistered);
        assertThat(viaEventsRegistered).startsWith("403|").contains(OWN_EMAIL_MESSAGE);
    }

    @Test
    void foreignEmailAttemptsNeverConsumeTheVictimsBucketOrTheCallersOwn() throws Exception {
        // Mallory (Bob) hammers Alice's email far past every limit: always 403, never 429.
        for (int i = 0; i < 40; i++) {
            viaRegistrations(BOB, "198.51.100.9", "alice@example.com").andExpect(status().isForbidden());
            viaEvents(BOB, "198.51.100.9", "alice@example.com").andExpect(status().isForbidden());
        }

        // Alice registers normally from her own address, and Bob can still register himself from his.
        viaRegistrations(ALICE, "203.0.113.1", "alice@example.com").andExpect(status().isOk());
        viaRegistrations(BOB, "198.51.100.9", "bob@example.com").andExpect(status().isOk());
    }

    @Test
    void theRegistrationsEndpointIsRateLimitedPerCallerWith429() throws Exception {
        for (int i = 0; i < 10; i++) {
            viaRegistrations(ALICE, "203.0.113." + i, "alice@example.com").andExpect(status().isOk());
        }
        viaRegistrations(ALICE, "203.0.113.50", "alice@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many registration requests. Please try again later."));
    }

    @Test
    void theEventsRegistrationEndpointIsRateLimitedToo() throws Exception {
        for (int i = 0; i < 10; i++) {
            viaEvents(ALICE, "203.0.113." + i, "alice@example.com").andExpect(status().isOk());
        }
        viaEvents(ALICE, "203.0.113.50", "alice@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many registration requests. Please try again later."));
    }

    @Test
    void bothEndpointsShareOneBudgetPerCaller() throws Exception {
        for (int i = 0; i < 5; i++) {
            viaRegistrations(ALICE, "203.0.113." + i, "alice@example.com").andExpect(status().isOk());
            viaEvents(ALICE, "203.0.113." + i, "alice@example.com").andExpect(status().isOk());
        }
        viaEvents(ALICE, "203.0.113.60", "alice@example.com").andExpect(status().isTooManyRequests());
        viaRegistrations(ALICE, "203.0.113.61", "alice@example.com").andExpect(status().isTooManyRequests());
    }

    @Test
    void aThrottledCallerDoesNotBlockAnotherUserOnAnotherAddress() throws Exception {
        for (int i = 0; i < 10; i++) {
            viaEvents(ALICE, "203.0.113." + i, "alice@example.com").andExpect(status().isOk());
        }
        viaEvents(ALICE, "203.0.113.50", "alice@example.com").andExpect(status().isTooManyRequests());

        viaEvents(BOB, "198.51.100.9", "bob@example.com").andExpect(status().isOk());
        viaRegistrations(BOB, "198.51.100.9", "bob@example.com").andExpect(status().isOk());
    }

    @Test
    void theBodyEmailIsNotARateLimitKeyAnAdminActingOnBehalfIsLimitedAsTheAdmin() throws Exception {
        // An admin registering one target email repeatedly spends the admin's budget, never the target's.
        for (int i = 0; i < 10; i++) {
            viaRegistrations(ADMIN, "192.0.2." + i, "alice@example.com").andExpect(status().isOk());
        }
        viaRegistrations(ADMIN, "192.0.2.99", "alice@example.com").andExpect(status().isTooManyRequests());

        // Alice is untouched and may still register herself.
        viaRegistrations(ALICE, "203.0.113.1", "alice@example.com").andExpect(status().isOk());
    }
}
