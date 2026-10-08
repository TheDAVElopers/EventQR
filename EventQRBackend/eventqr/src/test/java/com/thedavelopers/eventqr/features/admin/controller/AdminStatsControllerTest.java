package com.thedavelopers.eventqr.features.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.admin.service.AdminStatsService;
import com.thedavelopers.eventqr.features.auditlogs.repository.AuditLogRepository;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

class AdminStatsControllerTest {

    private static final String AUTH = "Bearer t";

    private UserProfileRepository users;
    private EventRepository events;
    private AuditLogRepository audits;
    private JwtService jwt;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users = mock(UserProfileRepository.class);
        events = mock(EventRepository.class);
        audits = mock(AuditLogRepository.class);
        jwt = mock(JwtService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminStatsController(new AdminStatsService(users, events, audits), jwt))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(users.count()).thenReturn(50L);
        when(users.countByRoleNotIn(List.of(AccountRole.ADMIN, AccountRole.SUPER_ADMIN))).thenReturn(42L);
        when(events.countByStatus(EventStatus.ACTIVE)).thenReturn(7L);
        when(audits.count()).thenReturn(123L);
    }

    @Test
    void superAdminCountsEveryAccount() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.SUPER_ADMIN);

        mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAccounts").value(50))
                .andExpect(jsonPath("$.data.activeEvents").value(7))
                .andExpect(jsonPath("$.data.auditLogCount").value(123));
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
    void nonAdminsAreForbidden() throws Exception {
        for (AccountRole role : new AccountRole[] {AccountRole.STAFF, AccountRole.ORGANIZER, AccountRole.ATTENDEE}) {
            when(jwt.extractRoleFromBearer(AUTH)).thenReturn(role);
            mvc.perform(get("/api/v1/admin/stats").header("Authorization", AUTH)).andExpect(status().isForbidden());
        }
    }
}
