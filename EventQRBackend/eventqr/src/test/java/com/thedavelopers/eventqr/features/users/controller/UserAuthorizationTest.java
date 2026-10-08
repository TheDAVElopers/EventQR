package com.thedavelopers.eventqr.features.users.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.uploads.service.FileStorageService;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Authorization ceilings for UserController: create / list / changeRole.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAuthorizationTest {

    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;
    @Mock
    private FileStorageService fileStorageService;

    private MockMvc mockMvc;

    private final UUID callerId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UserController(userService, jwtService, fileStorageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(userService.findByRoleNotIn(any(), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 20)));
        given(userService.findByRole(any(AccountRole.class), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 20)));
        given(userService.findAllUsers(any(Pageable.class))).willReturn(Page.empty(PageRequest.of(0, 20)));
    }

    // --- helpers -----------------------------------------------------------

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private String createBody(AccountRole role) {
        return "{\"email\":\"user@example.com\",\"fullName\":\"User\",\"phoneNumber\":null,"
                + "\"password\":\"Password1!\",\"role\":\"" + role.name() + "\"}";
    }

    private void postCreate(AccountRole callerRole, AccountRole roleToCreate, int expectedStatus)
            throws Exception {
        actingAs(callerId, callerRole);
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(roleToCreate))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().is(expectedStatus));
    }

    // --- POST /api/v1/users ------------------------------------------------

    @Test
    void create_attendee_isForbidden() throws Exception {
        postCreate(AccountRole.ATTENDEE, AccountRole.ATTENDEE, 403);
        verify(userService, never()).create(any());
    }

    @Test
    void create_organizer_isForbidden() throws Exception {
        postCreate(AccountRole.ORGANIZER, AccountRole.ATTENDEE, 403);
        verify(userService, never()).create(any());
    }

    @Test
    void create_staff_isForbidden() throws Exception {
        postCreate(AccountRole.STAFF, AccountRole.STAFF, 403);
        verify(userService, never()).create(any());
    }

    @Test
    void create_adminCreatingAttendee_isAllowed() throws Exception {
        postCreate(AccountRole.ADMIN, AccountRole.ATTENDEE, 200);
    }

    @Test
    void create_adminCreatingAdmin_isForbidden() throws Exception {
        postCreate(AccountRole.ADMIN, AccountRole.ADMIN, 403);
        verify(userService, never()).create(any());
    }

    @Test
    void create_adminCreatingSuperAdmin_isForbidden() throws Exception {
        postCreate(AccountRole.ADMIN, AccountRole.SUPER_ADMIN, 403);
        verify(userService, never()).create(any());
    }

    @Test
    void create_superAdminCreatingAdmin_isAllowed() throws Exception {
        postCreate(AccountRole.SUPER_ADMIN, AccountRole.ADMIN, 200);
    }

    @Test
    void create_superAdminCreatingSuperAdmin_isAllowed() throws Exception {
        postCreate(AccountRole.SUPER_ADMIN, AccountRole.SUPER_ADMIN, 200);
    }

    // --- GET /api/v1/users -------------------------------------------------

    @Test
    void list_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).findByRoleNotIn(any(), any());
    }

    @Test
    void list_organizer_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_admin_returnsNonAdminAccounts() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(userService).findByRoleNotIn(any(), any(Pageable.class));
        verify(userService, never()).findAllUsers(any());
    }

    @Test
    void list_adminRequestingAdminRole_getsEmptyPageWithoutQuery() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(get("/api/v1/users")
                        .param("role", "ADMIN")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(userService, never()).findByRole(eq(AccountRole.ADMIN), any());
        verify(userService, never()).findByRoleNotIn(any(), any());
    }

    @Test
    void list_superAdminMaySeeAdminAccounts() throws Exception {
        actingAs(callerId, AccountRole.SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/users")
                        .param("role", "ADMIN")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(userService).findByRole(eq(AccountRole.ADMIN), any(Pageable.class));
    }

    // --- PUT /api/v1/users/{userId}/role/{role} ----------------------------

    @Test
    void changeRole_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);

        mockMvc.perform(put("/api/v1/users/{userId}/role/{role}", targetUserId, "STAFF")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).changeRoleResponse(any(), any(), any(), any());
    }

    @Test
    void changeRole_organizer_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);

        mockMvc.perform(put("/api/v1/users/{userId}/role/{role}", targetUserId, "STAFF")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeRole_admin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(put("/api/v1/users/{userId}/role/{role}", targetUserId, "STAFF")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(userService).changeRoleResponse(eq(callerId), eq(AccountRole.ADMIN),
                eq(targetUserId), eq(AccountRole.STAFF));
    }
}
