package com.thedavelopers.eventqr.features.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.auditlogs.service.AuditLogService;
import com.thedavelopers.eventqr.features.eventrequests.service.EventCreationRequestService;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Admin endpoint ceilings: non-admin roles must be rejected before any service call.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthorizationTest {

    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;
    @Mock
    private EventCreationRequestService eventCreationRequestService;
    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;

    private final UUID callerId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminController(userService, jwtService, eventCreationRequestService, auditLogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(userService.findByRoleNotIn(any(), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 20)));
        given(userService.findByRole(any(AccountRole.class), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 20)));
        given(userService.findAllUsers(any(Pageable.class))).willReturn(Page.empty(PageRequest.of(0, 20)));
    }

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    @Test
    void listUsers_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).findAllUsers(any());
        verify(userService, never()).findByRoleNotIn(any(), any());
    }

    @Test
    void listUsers_organizer_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_staff_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_admin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(userService).findByRoleNotIn(any(), any(Pageable.class));
    }

    @Test
    void findUser_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);

        mockMvc.perform(get("/api/v1/admin/users/{userId}", targetUserId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).findOne(any());
    }

    @Test
    void findUser_admin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);
        given(userService.findOne(targetUserId)).willReturn(new UserResponse(targetUserId,
                "target@example.com", "Target", null, AccountRole.ATTENDEE, AccountStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/admin/users/{userId}", targetUserId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(userService, org.mockito.Mockito.atLeastOnce()).findOne(targetUserId);
    }
}
