package com.thedavelopers.eventqr.features.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
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
 * Business rules of the admin account-management endpoints. Who may call them at all is covered
 * by AdminAuthorizationTest; these tests cover what an admin may do to whom.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminControllerTest {

    private static final String ADMIN = "Bearer admin";
    private static final String SUPER = "Bearer super";

    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private EventCreationRequestService eventCreationRequestService;
    @Mock private AuditLogService auditLogService;

    private MockMvc mvc;
    private final UUID adminId = UUID.randomUUID();
    private final UUID superId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminController(userService, jwtService,
                        eventCreationRequestService, auditLogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(jwtService.extractUserIdFromBearer(ADMIN)).thenReturn(adminId);
        when(jwtService.extractRoleFromBearer(ADMIN)).thenReturn(AccountRole.ADMIN);
        when(jwtService.extractUserIdFromBearer(SUPER)).thenReturn(superId);
        when(jwtService.extractRoleFromBearer(SUPER)).thenReturn(AccountRole.SUPER_ADMIN);
        when(userService.findOne(adminId)).thenReturn(user(adminId, AccountRole.ADMIN, AccountStatus.ACTIVE));
        when(userService.findOne(superId)).thenReturn(user(superId, AccountRole.SUPER_ADMIN, AccountStatus.ACTIVE));
    }

    private UserResponse user(UUID id, AccountRole role, AccountStatus status) {
        return new UserResponse(id, id + "@example.com", "User " + id.toString().substring(0, 4), null, role, status);
    }

    private void givenTarget(AccountRole role, AccountStatus status) {
        UserResponse target = user(targetId, role, status);
        when(userService.findOne(targetId)).thenReturn(target);
        when(userService.updateStatus(eq(targetId), any())).thenReturn(target);
        when(userService.updateProfile(eq(targetId), any(), any())).thenReturn(target);
    }

    private static String json(String body) {
        return body.replace('\'', '"');
    }

    // ----- listing -----

    @Test
    void anAdminDoesNotSeeAdminAccountsInTheDefaultList() throws Exception {
        when(userService.findByRoleNotIn(any(), any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc.perform(get("/api/v1/admin/users").header("Authorization", ADMIN)).andExpect(status().isOk());

        verify(userService).findByRoleNotIn(eq(List.of(AccountRole.ADMIN, AccountRole.SUPER_ADMIN)), any(Pageable.class));
        verify(userService, never()).findAllUsers(any());
    }

    @Test
    void anAdminAskingForAdminsGetsAnEmptyPageNotTheAccounts() throws Exception {
        mvc.perform(get("/api/v1/admin/users").param("role", "ADMIN").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        verify(userService, never()).findByRole(any(), any());
    }

    @Test
    void aSuperAdminSeesEveryone() throws Exception {
        when(userService.findAllUsers(any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(user(adminId, AccountRole.ADMIN, AccountStatus.ACTIVE)), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/admin/users").header("Authorization", SUPER)).andExpect(status().isOk());

        verify(userService).findAllUsers(any(Pageable.class));
    }

    // ----- admins cannot manage other admins; a super admin can -----

    @Test
    void anAdminCannotViewOrChangeAnotherAdmin() throws Exception {
        givenTarget(AccountRole.ADMIN, AccountStatus.ACTIVE);

        mvc.perform(get("/api/v1/admin/users/{id}", targetId).header("Authorization", ADMIN))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/users/{id}/status", targetId).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'status':'INACTIVE'}")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/users/{id}/disable", targetId).header("Authorization", ADMIN))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/users/{id}", targetId).header("Authorization", ADMIN))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateStatus(any(), any());
        verify(userService, never()).hardDelete(any());
    }

    @Test
    void aSuperAdminCanDisableAnAdmin() throws Exception {
        givenTarget(AccountRole.ADMIN, AccountStatus.ACTIVE);

        mvc.perform(patch("/api/v1/admin/users/{id}/disable", targetId).header("Authorization", SUPER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account disabled"));

        verify(userService).updateStatus(targetId, AccountStatus.INACTIVE);
    }

    @Test
    void anAdminCanDisableAndReEnableAnOrdinaryUser() throws Exception {
        givenTarget(AccountRole.ATTENDEE, AccountStatus.ACTIVE);

        mvc.perform(patch("/api/v1/admin/users/{id}/disable", targetId).header("Authorization", ADMIN))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/users/{id}/enable", targetId).header("Authorization", ADMIN))
                .andExpect(status().isOk());

        verify(userService).updateStatus(targetId, AccountStatus.INACTIVE);
        verify(userService).updateStatus(targetId, AccountStatus.ACTIVE);
    }

    // ----- self-protection -----

    @Test
    void anAdminCannotDisableEnableOrDeleteTheirOwnAccount() throws Exception {
        when(userService.findOne(adminId)).thenReturn(user(adminId, AccountRole.ATTENDEE, AccountStatus.INACTIVE));

        mvc.perform(patch("/api/v1/admin/users/{id}/disable", adminId).header("Authorization", ADMIN))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/users/{id}/enable", adminId).header("Authorization", ADMIN))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/users/{id}", adminId).header("Authorization", ADMIN))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateStatus(any(), any());
        verify(userService, never()).hardDelete(any());
    }

    // ----- deletion safeguards -----

    @Test
    void anActiveAccountMustBeDisabledBeforeItCanBeDeleted() throws Exception {
        givenTarget(AccountRole.ATTENDEE, AccountStatus.ACTIVE);

        mvc.perform(delete("/api/v1/admin/users/{id}", targetId).header("Authorization", ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account must be disabled before deletion"));

        verify(userService, never()).hardDelete(any());
    }

    @Test
    void anAccountWithHistoryCannotBeDeleted() throws Exception {
        givenTarget(AccountRole.ATTENDEE, AccountStatus.INACTIVE);
        when(userService.hasDependentRecords(targetId)).thenReturn(true);

        mvc.perform(delete("/api/v1/admin/users/{id}", targetId).header("Authorization", ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account has registration or transaction history, cannot be deleted"));

        verify(userService, never()).hardDelete(any());
    }

    @Test
    void aDisabledAccountWithNoHistoryIsDeletedAndTheDeletionIsAudited() throws Exception {
        givenTarget(AccountRole.ATTENDEE, AccountStatus.INACTIVE);
        when(userService.hasDependentRecords(targetId)).thenReturn(false);

        mvc.perform(delete("/api/v1/admin/users/{id}", targetId).header("Authorization", ADMIN))
                .andExpect(status().isOk());

        verify(userService).hardDelete(targetId);
        verify(auditLogService).log(eq("ACCOUNT_DELETED"), anyString(), eq(adminId), anyString(), any(), eq(targetId));
    }

    // ----- audit trail -----

    @Test
    void everyAccountChangeIsRecordedInTheAuditLog() throws Exception {
        givenTarget(AccountRole.ATTENDEE, AccountStatus.ACTIVE);

        mvc.perform(patch("/api/v1/admin/users/{id}/status", targetId).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'status':'SUSPENDED'}")))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/users/{id}", targetId).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'fullName':'New Name'}")))
                .andExpect(status().isOk());

        verify(auditLogService).log(eq("ACCOUNT_STATUS_UPDATED"), anyString(), eq(adminId), anyString(), any(), eq(targetId));
        verify(auditLogService).log(eq("ACCOUNT_UPDATED"), anyString(), eq(adminId), anyString(), any(), eq(targetId));
    }

    @Test
    void aStatusChangeWithoutAStatusIsRejected() throws Exception {
        givenTarget(AccountRole.ATTENDEE, AccountStatus.ACTIVE);

        mvc.perform(patch("/api/v1/admin/users/{id}/status", targetId).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateStatus(any(), any());
    }

    // ----- role changes delegate the ceiling rules to the service -----

    @Test
    void roleChangesPassTheCallersIdAndRoleSoTheServiceCanApplyTheCeiling() throws Exception {
        when(userService.changeRoleResponse(adminId, AccountRole.ADMIN, targetId, AccountRole.STAFF))
                .thenReturn(user(targetId, AccountRole.STAFF, AccountStatus.ACTIVE));

        mvc.perform(patch("/api/v1/admin/users/{id}/roles", targetId).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'role':'STAFF'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STAFF"));
    }

    @Test
    void theServicesRefusalToCreateAnAdminSurfacesAs403() throws Exception {
        when(userService.changeRoleResponse(adminId, AccountRole.ADMIN, targetId, AccountRole.ADMIN))
                .thenThrow(new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Only super admins can assign admin roles"));

        mvc.perform(patch("/api/v1/admin/users/{id}/roles", targetId).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'role':'ADMIN'}")))
                .andExpect(status().isForbidden());
    }

    // ----- event requests -----

    @Test
    void approvingAnEventRequestRecordsWhoApprovedItAndTheirRemarks() throws Exception {
        UUID requestId = UUID.randomUUID();

        mvc.perform(patch("/api/v1/admin/event-requests/{id}/approve", requestId).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'adminRemarks':'Looks good'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event request approved"));

        verify(eventCreationRequestService).approve(eq(requestId), eq(adminId), anyString(), eq("Looks good"));
    }

    @Test
    void rejectingWithoutABodyIsAllowed() throws Exception {
        UUID requestId = UUID.randomUUID();

        mvc.perform(patch("/api/v1/admin/event-requests/{id}/reject", requestId).header("Authorization", ADMIN))
                .andExpect(status().isOk());

        verify(eventCreationRequestService).reject(eq(requestId), eq(adminId), anyString(), eq(null));
    }
}
