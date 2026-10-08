package com.thedavelopers.eventqr.features.staff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.features.scanning.service.ScanPurposeService;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Authorization coverage for StaffController: requireActiveAssignment and
 * requireScanPermission across all roles (entry scan used as representative endpoint).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffScanAuthorizationTest {

    @Mock
    private EventService eventService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private RewardService rewardService;
    @Mock
    private EventStaffAssignmentRepository eventStaffAssignmentRepository;
    @Mock
    private ScanPurposeService scanPurposeService;
    @Mock
    private JwtService jwtService;

    private MockMvc mockMvc;

    private final UUID eventId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new StaffController(eventService, registrationService, transactionService,
                                rewardService, eventStaffAssignmentRepository, scanPurposeService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        org.mockito.Mockito.lenient().when(scanPurposeService.requireActive(any())).thenReturn(
                new com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort.ScanPurposeSnapshot(UUID.randomUUID(), eventId,
                        "Entry", com.thedavelopers.eventqr.shared.constants.ScanPurposeCode.ENTRY, true, false, null));
        org.mockito.Mockito.lenient().when(transactionService.record(any())).thenReturn(
                new com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse(UUID.randomUUID(), eventId, null,
                        UUID.randomUUID(), null, UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), null,
                        com.thedavelopers.eventqr.shared.constants.TransactionType.ENTRY,
                        com.thedavelopers.eventqr.shared.constants.TransactionResult.APPROVED, 0, null, java.time.Instant.now()));
    }

    // --- helpers -----------------------------------------------------------

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private void eventStatus(EventStatus status, UUID organizerUserId) {
        given(eventService.findOne(eventId)).willReturn(new EventResponse(eventId, "Event", null, null,
                "Location", null, null, null, null, 100, 0, status, false,
                organizerUserId, null, null, null));
    }

    private void activeAssignment(boolean canScan) {
        EventStaffAssignment assignment = org.mockito.Mockito.mock(EventStaffAssignment.class);
        given(assignment.isCanScan()).willReturn(canScan);
        given(eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(Optional.of(assignment));
    }

    private void noAssignment() {
        given(eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(Optional.empty());
    }

    private String scanBody() {
        return "{\"eventId\":\"" + eventId + "\",\"scanPurposeId\":\"" + UUID.randomUUID() + "\","
                + "\"qrValue\":\"qr-1\",\"shortId\":null,\"staffUserId\":null,\"notes\":null}";
    }

    private void postEntry(int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/staff/events/{eventId}/scan/entry", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanBody())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().is(expectedStatus));
    }

    // --- scan permission by role -------------------------------------------

    @Test
    void scanEntry_superAdmin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.SUPER_ADMIN);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        postEntry(200);
    }

    @Test
    void scanEntry_adminNonOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        postEntry(200);
    }

    @Test
    void scanEntry_organizerOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        eventStatus(EventStatus.ACTIVE, callerId);
        postEntry(200);
    }

    @Test
    void scanEntry_organizerNonOwnerUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        noAssignment();
        postEntry(403);
    }

    @Test
    void scanEntry_organizerNonOwnerButAssigned_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        activeAssignment(true);
        postEntry(200);
    }

    @Test
    void scanEntry_staffAssignedWithScanPermission_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        activeAssignment(true);
        postEntry(200);
    }

    @Test
    void scanEntry_staffAssignedWithoutScanPermission_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        activeAssignment(false);
        postEntry(403);
    }

    @Test
    void scanEntry_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        noAssignment();
        postEntry(403);
    }

    @Test
    void scanEntry_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        postEntry(403);
    }

    @Test
    void scanEntry_eventEnded_isForbiddenEvenForAdmin() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);
        eventStatus(EventStatus.ENDED, UUID.randomUUID());
        postEntry(403);
    }

    @Test
    void scanEntry_scanPurposeBelongsToOtherEvent_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        activeAssignment(true);
        UUID otherEventId = UUID.randomUUID();
        given(scanPurposeService.requireActive(any())).willReturn(
                new com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort.ScanPurposeSnapshot(UUID.randomUUID(), otherEventId,
                        "Entry", com.thedavelopers.eventqr.shared.constants.ScanPurposeCode.ENTRY, true, false, null));

        postEntry(403);
    }

    @Test
    void scanEntry_scanPurposeCodeMismatchedRoute_isBadRequest() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        eventStatus(EventStatus.ACTIVE, UUID.randomUUID());
        activeAssignment(true);
        given(scanPurposeService.requireActive(any())).willReturn(
                new com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort.ScanPurposeSnapshot(UUID.randomUUID(), eventId,
                        "Exit", com.thedavelopers.eventqr.shared.constants.ScanPurposeCode.EXIT, true, false, null));

        postEntry(400);
    }

    // --- assignment-guarded read endpoint -----------------------------------

    @Test
    void eventDetails_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);

        mockMvc.perform(get("/api/v1/staff/events/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void eventDetails_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        noAssignment();

        mockMvc.perform(get("/api/v1/staff/events/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void eventDetails_organizerOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        eventStatus(EventStatus.APPROVED, callerId);

        mockMvc.perform(get("/api/v1/staff/events/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }
}
