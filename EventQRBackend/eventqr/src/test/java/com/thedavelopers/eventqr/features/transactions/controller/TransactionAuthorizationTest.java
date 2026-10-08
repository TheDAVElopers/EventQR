package com.thedavelopers.eventqr.features.transactions.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Authorization coverage for TransactionController record (POST) and
 * event-scoped listing (GET /event/{eventId}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionAuthorizationTest {

    @Mock
    private TransactionService transactionService;
    @Mock
    private EventService eventService;
    @Mock
    private JwtService jwtService;
    @Mock
    private EventStaffAssignmentRepository eventStaffAssignmentRepository;

    private MockMvc mockMvc;

    private final UUID eventId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TransactionController(transactionService, eventService, jwtService,
                                eventStaffAssignmentRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(transactionService.findByEvent(any(UUID.class), any(Pageable.class)))
                .willReturn(Page.empty());
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

    private void callerAssigned(boolean assigned) {
        given(eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(assigned);
    }

    private void eventOwnedBy(UUID organizerUserId) {
        given(eventService.findOne(eventId)).willReturn(new EventResponse(eventId, "Event", null, null,
                "Location", null, null, null, null, 100, 0, EventStatus.APPROVED, false,
                organizerUserId, null, null, null));
    }

    private String body() {
        return "{\"eventId\":\"" + eventId + "\",\"scanPurposeId\":\"" + UUID.randomUUID() + "\","
                + "\"qrValue\":null,\"shortId\":null,\"staffUserId\":null,\"notes\":null}";
    }

    private void postTransaction(int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().is(expectedStatus));
    }

    // --- POST /api/v1/transactions -----------------------------------------

    @Test
    void record_admin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);
        postTransaction(200);
    }

    @Test
    void record_superAdmin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.SUPER_ADMIN);
        postTransaction(200);
    }

    @Test
    void record_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);
        postTransaction(403);
    }

    @Test
    void record_staffAssigned_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssigned(true);
        postTransaction(200);
    }

    @Test
    void record_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssigned(false);
        postTransaction(403);
    }

    @Test
    void record_organizerOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        eventOwnedBy(callerId);
        postTransaction(200);
    }

    @Test
    void record_organizerNonOwner_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        eventOwnedBy(UUID.randomUUID());
        postTransaction(403);
    }

    // --- GET /api/v1/transactions/event/{eventId} --------------------------

    @Test
    void listByEvent_admin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(get("/api/v1/transactions/event/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(transactionService).findByEvent(any(UUID.class), any(Pageable.class));
    }

    @Test
    void listByEvent_organizerOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        eventOwnedBy(callerId);

        mockMvc.perform(get("/api/v1/transactions/event/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void listByEvent_organizerNonOwner_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        eventOwnedBy(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/transactions/event/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(transactionService, never()).findByEvent(any(UUID.class), any(Pageable.class));
    }

    @Test
    void listByEvent_staff_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);

        mockMvc.perform(get("/api/v1/transactions/event/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listByEvent_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);

        mockMvc.perform(get("/api/v1/transactions/event/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void record_ignoresBodyStaffUserId_usesAuthenticatedCaller() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);
        UUID spoofed = UUID.randomUUID();
        String spoofBody = "{\"eventId\":\"" + eventId + "\",\"scanPurposeId\":\"" + UUID.randomUUID() + "\","
                + "\"staffUserId\":\"" + spoofed + "\"}";
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofBody)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        org.mockito.ArgumentCaptor<com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest.class);
        verify(transactionService).record(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().staffUserId()).isEqualTo(callerId);
    }
}
