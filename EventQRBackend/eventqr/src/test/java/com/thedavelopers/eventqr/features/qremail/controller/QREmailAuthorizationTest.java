package com.thedavelopers.eventqr.features.qremail.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort.RegistrationSnapshot;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Authorization coverage for QREmailController: self / ownership / assignment
 * checks before QR email dispatch.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QREmailAuthorizationTest {

    @Mock
    private QREmailService qrEmailService;
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
                        new QREmailController(qrEmailService, registrationService, jwtService,
                                eventService, eventStaffAssignmentRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- helpers -----------------------------------------------------------

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private void registrationOwnedBy(UUID attendeeUserId) {
        given(registrationService.requireById(registrationId)).willReturn(
                new RegistrationSnapshot(registrationId, eventId, attendeeUserId, "a@example.com",
                        "Attendee", null, credentialId, null, null, null, null, null, 1));
    }

    private void eventOwnedBy(UUID organizerUserId) {
        given(eventService.findOne(eventId)).willReturn(new EventResponse(eventId, "Event", null, null,
                "Location", null, null, null, null, 100, 0, EventStatus.APPROVED, false,
                organizerUserId, null, null, null));
    }

    private void send(int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/qr-email/registration/{id}", registrationId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().is(expectedStatus));
    }

    // --- self access --------------------------------------------------------

    @Test
    void send_attendeeOwnRegistration_isAllowed() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);
        registrationOwnedBy(attendeeId);
        send(200);
    }

    @Test
    void send_attendeeForeignRegistration_isForbidden() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);
        registrationOwnedBy(UUID.randomUUID());
        send(403);
        verify(qrEmailService, never()).sendForRegistration(any());
    }

    // --- admin / organizer / staff ------------------------------------------

    @Test
    void send_admin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);
        registrationOwnedBy(attendeeId);
        send(200);
        verify(eventService, never()).findOne(any());
    }

    @Test
    void send_organizerOwnRegistration_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        registrationOwnedBy(callerId);
        send(200);
    }

    @Test
    void send_organizerForeignButOwnsEvent_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        registrationOwnedBy(attendeeId);
        eventOwnedBy(callerId);
        send(200);
    }

    @Test
    void send_organizerForeignNotOwner_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        registrationOwnedBy(attendeeId);
        eventOwnedBy(UUID.randomUUID());
        send(403);
        verify(qrEmailService, never()).sendForRegistration(any());
    }

    @Test
    void send_staffAssigned_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        registrationOwnedBy(attendeeId);
        given(eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(true);
        send(200);
    }

    @Test
    void send_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        registrationOwnedBy(attendeeId);
        given(eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(false);
        send(403);
        verify(qrEmailService, never()).sendForRegistration(any());
    }

    @Test
    void send_superAdmin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.SUPER_ADMIN);
        registrationOwnedBy(attendeeId);
        send(200);
    }
}
