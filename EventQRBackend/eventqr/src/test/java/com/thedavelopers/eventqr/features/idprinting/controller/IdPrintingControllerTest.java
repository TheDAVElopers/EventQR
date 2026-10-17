package com.thedavelopers.eventqr.features.idprinting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdTemplateConfigResponse;
import com.thedavelopers.eventqr.features.idprinting.service.IdPrintingService;
import com.thedavelopers.eventqr.features.idprinting.service.IdTemplateConfigurationService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.uploads.service.FileStorageService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.IdPrintStatus;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Web-layer coverage for the print-confirmation endpoint, the PENDING print responses, and the access rules of
 * GET /api/v1/events/{eventId}/id-template.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdPrintingControllerTest {

    @Mock
    private IdPrintingService idPrintingService;
    @Mock
    private IdTemplateConfigurationService configurationService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private JwtService jwtService;
    @Mock
    private EventService eventService;
    @Mock
    private EventStaffAssignmentRepository assignmentRepository;

    private MockMvc mockMvc;

    private final UUID eventId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();
    private final UUID logId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        IdPrintingAccess access = new IdPrintingAccess(jwtService, eventService, assignmentRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new OrganizerIdTemplateController(idPrintingService, fileStorageService, access),
                        new IdTemplateConfigurationController(configurationService, jwtService, access))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        EventResponse event = mock(EventResponse.class);
        given(event.organizerUserId()).willReturn(organizerId);
        given(eventService.findOne(eventId)).willReturn(event);
        given(assignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, staffId)).willReturn(true);
    }

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private IdPrintResponse response(IdPrintStatus status, boolean success, Instant printedAt, Instant confirmedAt) {
        return new IdPrintResponse(logId, eventId, attendeeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                false, success, "Print confirmed", printedAt, status, confirmedAt);
    }

    // ------------------------------------------------------------ PATCH print-logs/{logId}

    @Test
    void confirm_assignedStaff_printed_returns200WithUpdatedLog() throws Exception {
        actingAs(staffId, AccountRole.STAFF);
        Instant now = Instant.parse("2026-10-17T08:00:00Z");
        given(idPrintingService.confirm(eventId, logId, IdPrintStatus.PRINTED, staffId, false))
                .willReturn(response(IdPrintStatus.PRINTED, true, now, now));

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"PRINTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Print outcome recorded"))
                .andExpect(jsonPath("$.data.printLogId").value(logId.toString()))
                .andExpect(jsonPath("$.data.status").value("PRINTED"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.printedAt").exists())
                .andExpect(jsonPath("$.data.confirmedAt").exists());
    }

    @Test
    void confirm_organizerOwner_isEventManager() throws Exception {
        actingAs(organizerId, AccountRole.ORGANIZER);
        given(idPrintingService.confirm(eventId, logId, IdPrintStatus.CANCELLED, organizerId, true))
                .willReturn(response(IdPrintStatus.CANCELLED, false, null, Instant.now()));

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.success").value(false));
        verify(idPrintingService).confirm(eventId, logId, IdPrintStatus.CANCELLED, organizerId, true);
    }

    @Test
    void confirm_admin_isEventManager() throws Exception {
        UUID adminId = UUID.randomUUID();
        actingAs(adminId, AccountRole.ADMIN);
        given(idPrintingService.confirm(eventId, logId, IdPrintStatus.FAILED, adminId, true))
                .willReturn(response(IdPrintStatus.FAILED, false, null, Instant.now()));

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FAILED\"}"))
                .andExpect(status().isOk());
        verify(idPrintingService).confirm(eventId, logId, IdPrintStatus.FAILED, adminId, true);
    }

    @Test
    void confirm_unassignedStaff_isForbidden() throws Exception {
        actingAs(UUID.randomUUID(), AccountRole.STAFF);

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"PRINTED\"}"))
                .andExpect(status().isForbidden());
        verify(idPrintingService, never()).confirm(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void confirm_otherOrganizer_isForbidden() throws Exception {
        actingAs(UUID.randomUUID(), AccountRole.ORGANIZER);

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"PRINTED\"}"))
                .andExpect(status().isForbidden());
        verify(idPrintingService, never()).confirm(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void confirm_attendee_isForbidden() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"PRINTED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirm_staffConfirmingSomeoneElsesLog_is403() throws Exception {
        actingAs(staffId, AccountRole.STAFF);
        given(idPrintingService.confirm(eventId, logId, IdPrintStatus.PRINTED, staffId, false))
                .willThrow(new ForbiddenException("Only the staff member who started this print can confirm it"));

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"PRINTED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void confirm_alreadyFinal_is409() throws Exception {
        actingAs(staffId, AccountRole.STAFF);
        given(idPrintingService.confirm(eventId, logId, IdPrintStatus.CANCELLED, staffId, false))
                .willThrow(new ConflictException("Print log is already PRINTED"));

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"CANCELLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Print log is already PRINTED"));
    }

    @Test
    void confirm_logNotInEvent_is404() throws Exception {
        actingAs(staffId, AccountRole.STAFF);
        given(idPrintingService.confirm(eventId, logId, IdPrintStatus.PRINTED, staffId, false))
                .willThrow(new ResourceNotFoundException("Print log not found"));

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"PRINTED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirm_missingOrUnknownOutcome_is400() throws Exception {
        actingAs(staffId, AccountRole.STAFF);

        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", eventId, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"DONE\"}"))
                .andExpect(status().isBadRequest());
        verify(idPrintingService, never()).confirm(any(), any(), any(), any(), anyBoolean());
    }

    // ------------------------------------------------------------ print routes

    @Test
    void printId_returnsPendingLog_andPassesCallerAsActor() throws Exception {
        actingAs(staffId, AccountRole.STAFF);
        given(idPrintingService.printForAttendee(eventId, attendeeId, staffId))
                .willReturn(response(IdPrintStatus.PENDING, false, null, null));

        mockMvc.perform(post("/api/v1/staff/events/{eventId}/attendees/{attendeeId}/print-id", eventId, attendeeId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.printLogId").value(logId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.printedAt").doesNotExist());
    }

    @Test
    void printBatch_passesCallerAsActor() throws Exception {
        actingAs(staffId, AccountRole.STAFF);
        given(idPrintingService.printBatch(eq(eventId), eq(List.of(attendeeId)), eq(staffId)))
                .willReturn(List.of(response(IdPrintStatus.PENDING, false, null, null)));

        mockMvc.perform(post("/api/v1/staff/events/{eventId}/print-id-batch", eventId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendeeUserIds\":[\"" + attendeeId + "\"],\"reprint\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void printLogs_includeStatus() throws Exception {
        actingAs(staffId, AccountRole.STAFF);
        given(idPrintingService.findByEvent(eventId))
                .willReturn(List.of(response(IdPrintStatus.CANCELLED, false, null, Instant.now())));

        mockMvc.perform(get("/api/v1/staff/events/{eventId}/print-logs", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("CANCELLED"));
    }

    // ------------------------------------------------------------ GET /events/{id}/id-template

    @Test
    void getIdTemplate_assignedStaff_getsDefaultConfig() throws Exception {
        actingAs(staffId, AccountRole.STAFF);
        given(configurationService.getConfig(eventId))
                .willReturn(new IdTemplateConfigResponse(eventId, List.of(), List.of("QR_CODE", "ATTENDEE_NAME")));

        mockMvc.perform(get("/api/v1/events/{eventId}/id-template", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.data.visibleFields").isEmpty())
                .andExpect(jsonPath("$.data.lockedFields[0]").value("QR_CODE"))
                .andExpect(jsonPath("$.data.lockedFields[1]").value("ATTENDEE_NAME"));
    }

    @Test
    void getIdTemplate_organizerOwner_isAllowed() throws Exception {
        actingAs(organizerId, AccountRole.ORGANIZER);
        given(configurationService.getConfig(eventId))
                .willReturn(new IdTemplateConfigResponse(eventId, List.of("ROLE"), List.of("QR_CODE", "ATTENDEE_NAME")));

        mockMvc.perform(get("/api/v1/events/{eventId}/id-template", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibleFields[0]").value("ROLE"));
    }

    @Test
    void getIdTemplate_admin_isAllowed() throws Exception {
        actingAs(UUID.randomUUID(), AccountRole.SUPER_ADMIN);
        given(configurationService.getConfig(eventId))
                .willReturn(new IdTemplateConfigResponse(eventId, List.of(), List.of("QR_CODE", "ATTENDEE_NAME")));

        mockMvc.perform(get("/api/v1/events/{eventId}/id-template", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void getIdTemplate_attendee_isForbidden() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);

        mockMvc.perform(get("/api/v1/events/{eventId}/id-template", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verify(configurationService, never()).getConfig(any());
    }

    @Test
    void getIdTemplate_unassignedStaff_isForbidden() throws Exception {
        actingAs(UUID.randomUUID(), AccountRole.STAFF);

        mockMvc.perform(get("/api/v1/events/{eventId}/id-template", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verify(configurationService, never()).getConfig(any());
    }

    @Test
    void getIdTemplate_otherOrganizer_isForbidden() throws Exception {
        actingAs(UUID.randomUUID(), AccountRole.ORGANIZER);

        mockMvc.perform(get("/api/v1/events/{eventId}/id-template", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verify(configurationService, never()).getConfig(any());
    }

    // ------------------------------------------------------------ access before existence

    @Test
    void unknownEvent_isForbidden_notNotFound_forOrganizersAndStaff() throws Exception {
        UUID unknownEvent = UUID.randomUUID();
        given(eventService.findOne(unknownEvent)).willThrow(new ResourceNotFoundException("Event not found: " + unknownEvent));

        actingAs(organizerId, AccountRole.ORGANIZER);
        mockMvc.perform(get("/api/v1/events/{eventId}/id-template", unknownEvent)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/staff/events/{eventId}/print-logs/{logId}", unknownEvent, logId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"PRINTED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/organizer/events/{eventId}/id-template", unknownEvent)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        actingAs(staffId, AccountRole.STAFF);
        mockMvc.perform(get("/api/v1/events/{eventId}/id-template", unknownEvent)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(configurationService, never()).getConfig(any());
        verify(idPrintingService, never()).confirm(any(), any(), any(), any(), anyBoolean());
    }
}
