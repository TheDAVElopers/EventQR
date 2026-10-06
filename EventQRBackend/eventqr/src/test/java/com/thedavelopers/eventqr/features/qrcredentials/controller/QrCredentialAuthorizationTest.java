package com.thedavelopers.eventqr.features.qrcredentials.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.qrcredentials.service.QrCredentialService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Authorization coverage for QrCredentialController: generic registration path
 * (role/self/ownership/assignment) and attendees/me own-registration ceiling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QrCredentialAuthorizationTest {

    @Mock
    private QrCredentialService qrCredentialService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private JwtService jwtService;
    @Mock
    private EventService eventService;
    @Mock
    private EventStaffAssignmentRepository eventStaffAssignmentRepository;

    private MockMvc mockMvc;

    private final UUID registrationId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID credentialId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new QrCredentialController(qrCredentialService, registrationService, jwtService,
                                eventService, eventStaffAssignmentRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(qrCredentialService.findByRegistrationId(any()))
                .willReturn(Optional.of(snapshot()));
        given(qrCredentialService.findById(any())).willReturn(Optional.of(snapshot()));
    }

    // --- helpers -----------------------------------------------------------

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private QrCredentialSnapshot snapshot() {
        return new QrCredentialSnapshot(credentialId, eventId, attendeeId, registrationId,
                "qr-value", true, null, null, false);
    }

    private void registrationOwnedBy(UUID attendeeUserId) {
        given(registrationService.findOne(registrationId)).willReturn(
                new RegistrationResponse(registrationId, eventId, attendeeUserId, "a@example.com",
                        "Attendee", null, credentialId, null, "Event", "Location",
                        null, null, null, null, null, null, null, 1, null));
    }

    private void eventOwnedBy(UUID organizerUserId) {
        given(eventService.findOne(eventId)).willReturn(new EventResponse(eventId, "Event", null, null,
                "Location", null, null, null, null, 100, 0, EventStatus.APPROVED, false,
                organizerUserId, null, null, null));
    }

    // --- GET /qr-credentials/registration/{registrationId} ------------------

    @Test
    void findByRegistration_admin_isAllowedWithoutRegistrationLookup() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(get("/api/v1/qr-credentials/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(registrationService, never()).findOne(any());
    }

    @Test
    void findByRegistration_attendeeOwn_isAllowed() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);
        registrationOwnedBy(attendeeId);

        mockMvc.perform(get("/api/v1/qr-credentials/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void findByRegistration_attendeeForeign_isForbidden() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);
        registrationOwnedBy(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/qr-credentials/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(qrCredentialService, never()).findByRegistrationId(any());
    }

    @Test
    void findByRegistration_organizerOwnRegistration_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        registrationOwnedBy(callerId);

        mockMvc.perform(get("/api/v1/qr-credentials/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(eventService, never()).findOne(any());
    }

    @Test
    void findByRegistration_organizerForeignButOwnsEvent_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        registrationOwnedBy(attendeeId);
        eventOwnedBy(callerId);

        mockMvc.perform(get("/api/v1/qr-credentials/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void findByRegistration_organizerForeignNotOwner_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        registrationOwnedBy(attendeeId);
        eventOwnedBy(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/qr-credentials/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findByRegistration_staffAssigned_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        registrationOwnedBy(attendeeId);
        given(eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(true);

        mockMvc.perform(get("/api/v1/qr-credentials/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void findByRegistration_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        registrationOwnedBy(attendeeId);
        given(eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(false);

        mockMvc.perform(get("/api/v1/qr-credentials/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    // --- GET /qr-credentials/attendees/me/registration/{registrationId} ----

    @Test
    void attendeesMe_ownRegistration_isAllowed() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);
        registrationOwnedBy(attendeeId);

        mockMvc.perform(get("/api/v1/qr-credentials/attendees/me/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void attendeesMe_foreignRegistration_isForbiddenForAttendee() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);
        registrationOwnedBy(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/qr-credentials/attendees/me/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void attendeesMe_foreignRegistration_isForbiddenEvenForAdmin() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);
        registrationOwnedBy(attendeeId);

        mockMvc.perform(get("/api/v1/qr-credentials/attendees/me/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(qrCredentialService, never()).findByRegistrationId(any());
    }

    @Test
    void attendeesMe_foreignRegistration_isForbiddenEvenForOrganizer() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        registrationOwnedBy(attendeeId);
        eventOwnedBy(callerId);

        mockMvc.perform(get("/api/v1/qr-credentials/attendees/me/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }
}
