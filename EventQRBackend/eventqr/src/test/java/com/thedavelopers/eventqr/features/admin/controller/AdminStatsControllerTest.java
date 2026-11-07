package com.thedavelopers.eventqr.features.admin.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.admin.service.AdminStatsService;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.eventrequests.model.entity.EventCreationRequest;
import com.thedavelopers.eventqr.features.eventrequests.repository.EventCreationRequestRepository;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventRequestStatus;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.security.JwtService;

class AdminStatsControllerTest {

    private static final String AUTH = "Bearer t";
    private static final Instant OLDEST = Instant.parse("2026-11-08T10:15:00Z");

    private UserProfileRepository users;
    private EventRepository events;
    private EventCreationRequestRepository requests;
    private JwtService jwt;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users = mock(UserProfileRepository.class);
        events = mock(EventRepository.class);
        requests = mock(EventCreationRequestRepository.class);
        jwt = mock(JwtService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new AdminStatsController(new AdminStatsService(users, events, requests), jwt))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(users.count()).thenReturn(50L);
        when(users.countByRoleNotIn(List.of(AccountRole.ADMIN, AccountRole.SUPER_ADMIN))).thenReturn(42L);
        when(events.countByStatus(EventStatus.ACTIVE)).thenReturn(7L);
        when(requests.countByStatus(EventRequestStatus.PENDING)).thenReturn(9L);
        when(requests.findTop3ByStatusOrderByCreatedAtAsc(EventRequestStatus.PENDING))
                .thenReturn(List.of(pending("Juan Dela Cruz", "Campus Tech Fest", OLDEST),
                        pending("Maria Santos", "Sports Week", OLDEST.plusSeconds(60)),
                        pending("Ana Cruz", "Arts Night", OLDEST.plusSeconds(120))));
    }

    @Test
    void superAdminCountsEveryAccount() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.SUPER_ADMIN);

        mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAccounts").value(50))
                .andExpect(jsonPath("$.data.activeEvents").value(7));
        verify(users, never()).countByRoleNotIn(any());
    }

    @Test
    void aPlainAdminDoesNotCountAdminAccounts() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ADMIN);

        mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAccounts").value(42));
        verify(users, never()).count();
    }

    @Test
    void onlyActiveEventsAreCountedNotApproved() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ADMIN);

        mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH)).andExpect(status().isOk());

        verify(events).countByStatus(EventStatus.ACTIVE);
        verify(events, never()).countByStatusIn(any());
    }

    @Test
    void pendingCountComesFromCountQueryNotThePreviewList() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ADMIN);

        mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH))
                .andExpect(status().isOk())
                // 9 pending, but only the 3-row preview is loaded: the badge must not equal the preview size.
                .andExpect(jsonPath("$.data.pendingEventRequests").value(9))
                .andExpect(jsonPath("$.data.pendingPreview", hasSize(3)));

        verify(requests).countByStatus(EventRequestStatus.PENDING);
        verify(requests, never()).findAllByOrderByCreatedAtDesc();
        verify(requests, never()).findByRequesterUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void pendingPreviewCarriesIdRequesterNameEventNameAndCreatedAtInOrder() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ADMIN);

        mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingPreview[0].requesterName").value("Juan Dela Cruz"))
                .andExpect(jsonPath("$.data.pendingPreview[0].eventName").value("Campus Tech Fest"))
                .andExpect(jsonPath("$.data.pendingPreview[0].createdAt").value("2026-11-08T10:15:00Z"))
                .andExpect(jsonPath("$.data.pendingPreview[0].id").isString())
                .andExpect(jsonPath("$.data.pendingPreview[2].eventName").value("Arts Night"));

        verify(requests).findTop3ByStatusOrderByCreatedAtAsc(EventRequestStatus.PENDING);
    }

    @Test
    void auditLogCountIsGoneFromThePayload() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.SUPER_ADMIN);

        mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditLogCount").doesNotExist());
    }

    @Test
    void noPendingRequestsYieldsZeroAndEmptyPreview() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ADMIN);
        when(requests.countByStatus(EventRequestStatus.PENDING)).thenReturn(0L);
        when(requests.findTop3ByStatusOrderByCreatedAtAsc(EventRequestStatus.PENDING)).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingEventRequests").value(0))
                .andExpect(jsonPath("$.data.pendingPreview", hasSize(0)));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenThrow(new UnauthorizedException("Invalid or expired session"));

        mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH))
                .andExpect(status().isUnauthorized());
        verify(requests, never()).countByStatus(any());
        verify(requests, never()).findTop3ByStatusOrderByCreatedAtAsc(any());
    }

    @Test
    void nonAdminsAreForbidden() throws Exception {
        for (AccountRole role : new AccountRole[] {AccountRole.STAFF, AccountRole.ORGANIZER, AccountRole.ATTENDEE}) {
            when(jwt.extractRoleFromBearer(AUTH)).thenReturn(role);
            mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH)).andExpect(status().isForbidden());
        }
        verify(requests, never()).countByStatus(any());
    }

    private EventCreationRequest pending(String requesterName, String eventName, Instant createdAt) {
        EventCreationRequest request = new EventCreationRequest();
        request.setId(UUID.randomUUID());
        request.setRequesterName(requesterName);
        request.setEventName(eventName);
        request.setCreatedAt(createdAt);
        request.setStatus(EventRequestStatus.PENDING);
        return request;
    }
}
