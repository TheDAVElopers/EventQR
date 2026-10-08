package com.thedavelopers.eventqr.features.admin.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.auditlogs.service.AuditLogService;
import com.thedavelopers.eventqr.features.users.model.dto.ProfileUpdateRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.model.dto.UserRoleRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserStatusRequest;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.features.eventrequests.model.dto.EventRequestDecisionRequest;
import com.thedavelopers.eventqr.features.eventrequests.model.dto.EventRequestResponse;
import com.thedavelopers.eventqr.features.eventrequests.service.EventCreationRequestService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.utils.LikePatterns;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserService userService;
    private final JwtService jwtService;
    private final EventCreationRequestService eventCreationRequestService;
    private final AuditLogService auditLogService;

    public AdminController(UserService userService, JwtService jwtService,
                           EventCreationRequestService eventCreationRequestService,
                           AuditLogService auditLogService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.eventCreationRequestService = eventCreationRequestService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(HttpServletRequest request,
                                                                     @RequestParam(required = false) AccountRole role,
                                                                     @RequestParam(required = false) String q,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        requireAdmin(request);
        AccountRole callerRole = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserResponse> users;
        String pattern = LikePatterns.contains(q);
        if (callerRole == AccountRole.SUPER_ADMIN) {
            if (pattern != null) {
                users = role != null ? userService.searchByRole(role, pattern, pageable) : userService.searchAllUsers(pattern, pageable);
            } else {
                users = role != null ? userService.findByRole(role, pageable) : userService.findAllUsers(pageable);
            }
        } else if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            users = Page.empty(pageable);
        } else if (role != null) {
            users = pattern != null ? userService.searchByRole(role, pattern, pageable) : userService.findByRole(role, pageable);
        } else if (pattern != null) {
            users = userService.searchByRoleNotIn(List.of(AccountRole.ADMIN, AccountRole.SUPER_ADMIN), pattern, pageable);
        } else {
            users = userService.findByRoleNotIn(List.of(AccountRole.ADMIN, AccountRole.SUPER_ADMIN), pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> findUser(HttpServletRequest request, @PathVariable UUID userId) {
        requireAdmin(request);
        forbidManagingAdminTargets(request, userId);
        return ResponseEntity.ok(ApiResponse.success(userService.findOne(userId)));
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(HttpServletRequest request,
                                                               @PathVariable UUID userId,
                                                               @Valid @RequestBody ProfileUpdateRequest body) {
        requireAdmin(request);
        forbidManagingAdminTargets(request, userId);
        UserResponse updated = userService.updateProfile(userId, body.fullName(), body.phoneNumber());
        logAdminAction(request, "ACCOUNT_UPDATED", updated.fullName(), null, updated.userId());
        return ResponseEntity.ok(ApiResponse.success("User updated", updated));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(HttpServletRequest request,
                                                                @PathVariable UUID userId,
                                                                @Valid @RequestBody UserStatusRequest body) {
        requireAdmin(request);
        forbidManagingAdminTargets(request, userId);
        UserResponse updated = userService.updateStatus(userId, body.status());
        logAdminAction(request, "ACCOUNT_STATUS_UPDATED", updated.fullName(), null, updated.userId());
        return ResponseEntity.ok(ApiResponse.success("Status updated", updated));
    }

    @PatchMapping("/users/{userId}/roles")
    public ResponseEntity<ApiResponse<UserResponse>> updateRole(HttpServletRequest request,
                                                              @PathVariable UUID userId,
                                                              @Valid @RequestBody UserRoleRequest body) {
        requireAdmin(request);
        AccountRole callerRole = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        UserResponse updated = userService.changeRoleResponse(currentAdminId(request), callerRole, userId, body.role());
        logAdminAction(request, "ACCOUNT_ROLE_UPDATED", updated.fullName(), null, updated.userId());
        return ResponseEntity.ok(ApiResponse.success("Role updated", updated));
    }

    @PatchMapping("/users/{userId}/disable")
    public ResponseEntity<ApiResponse<UserResponse>> disableUser(HttpServletRequest request,
                                                                 @PathVariable UUID userId) {
        requireAdmin(request);
        forbidManagingAdminTargets(request, userId);
        UUID adminUserId = currentAdminId(request);
        if (adminUserId.equals(userId)) {
            throw new BadRequestException("Cannot disable your own account");
        }
        UserResponse updated = userService.updateStatus(userId, AccountStatus.INACTIVE);
        logAdminAction(request, "ACCOUNT_DISABLED", updated.fullName(), null, userId);
        return ResponseEntity.ok(ApiResponse.success("Account disabled", updated));
    }

    @PatchMapping("/users/{userId}/enable")
    public ResponseEntity<ApiResponse<UserResponse>> enableUser(HttpServletRequest request,
                                                                @PathVariable UUID userId) {
        requireAdmin(request);
        forbidManagingAdminTargets(request, userId);
        UUID adminUserId = currentAdminId(request);
        if (adminUserId.equals(userId)) {
            throw new BadRequestException("Cannot enable your own account");
        }
        UserResponse updated = userService.updateStatus(userId, AccountStatus.ACTIVE);
        logAdminAction(request, "ACCOUNT_ENABLED", updated.fullName(), null, userId);
        return ResponseEntity.ok(ApiResponse.success("Account enabled", updated));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(HttpServletRequest request, @PathVariable UUID userId) {
        requireAdmin(request);
        forbidManagingAdminTargets(request, userId);
        UUID adminUserId = currentAdminId(request);
        if (adminUserId.equals(userId)) {
            throw new BadRequestException("Cannot delete your own account");
        }
        UserResponse target = userService.findOne(userId);
        if (target.status() == AccountStatus.ACTIVE) {
            throw new BadRequestException("Account must be disabled before deletion");
        }
        if (userService.hasDependentRecords(userId)) {
            throw new BadRequestException("Account has registration or transaction history, cannot be deleted");
        }
        logAdminAction(request, "ACCOUNT_DELETED", target.fullName(), null, userId);
        userService.hardDelete(userId);
        return ResponseEntity.ok(ApiResponse.success("Account permanently deleted", null));
    }

    @GetMapping("/event-requests")
    public ResponseEntity<ApiResponse<List<EventRequestResponse>>> listEventRequests(HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success(eventCreationRequestService.findAllForAdmin()));
    }

    @GetMapping("/event-requests/{requestId}")
    public ResponseEntity<ApiResponse<EventRequestResponse>> findEventRequest(HttpServletRequest request, @PathVariable UUID requestId) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success(eventCreationRequestService.findOneForAdmin(requestId)));
    }

    @PatchMapping("/event-requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<EventRequestResponse>> approveEventRequest(HttpServletRequest request,
                                                                                @PathVariable UUID requestId,
                                                                                @Valid @RequestBody(required = false) EventRequestDecisionRequest body) {
        requireAdmin(request);
        UUID adminUserId = currentAdminId(request);
        String remarks = body == null ? null : body.adminRemarks();
        return ResponseEntity.ok(ApiResponse.success("Event request approved",
                eventCreationRequestService.approve(requestId, adminUserId, currentAdminName(adminUserId), remarks)));
    }

    @PatchMapping("/event-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<EventRequestResponse>> rejectEventRequest(HttpServletRequest request,
                                                                               @PathVariable UUID requestId,
                                                                               @Valid @RequestBody(required = false) EventRequestDecisionRequest body) {
        requireAdmin(request);
        UUID adminUserId = currentAdminId(request);
        String remarks = body == null ? null : body.adminRemarks();
        return ResponseEntity.ok(ApiResponse.success("Event request rejected",
                eventCreationRequestService.reject(requestId, adminUserId, currentAdminName(adminUserId), remarks)));
    }

    @PatchMapping("/event-requests/{requestId}/upgrade-organizer")
    public ResponseEntity<ApiResponse<EventRequestResponse>> upgradeOrganizer(HttpServletRequest request, @PathVariable UUID requestId) {
        requireAdmin(request);
        UUID adminUserId = currentAdminId(request);
        return ResponseEntity.ok(ApiResponse.success("Requester upgraded to organizer",
                eventCreationRequestService.upgradeOrganizer(requestId, adminUserId, currentAdminName(adminUserId))));
    }

    private void requireAdmin(HttpServletRequest request) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role != AccountRole.ADMIN && role != AccountRole.SUPER_ADMIN) {
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Admin access required");
        }
    }

    private void forbidManagingAdminTargets(HttpServletRequest request, UUID targetUserId) {
        AccountRole callerRole = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (callerRole == AccountRole.SUPER_ADMIN) {
            return;
        }
        UserResponse target = userService.findOne(targetUserId);
        if (target.role() == AccountRole.ADMIN || target.role() == AccountRole.SUPER_ADMIN) {
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Admins cannot manage admin accounts");
        }
    }

    private UUID currentAdminId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }

    private String currentAdminName(UUID adminUserId) {
        return userService.findOne(adminUserId).fullName();
    }

    private void logAdminAction(HttpServletRequest request, String action, String details, UUID eventId, UUID targetUserId) {
        UUID adminUserId = currentAdminId(request);
        auditLogService.log(action, details, adminUserId, currentAdminName(adminUserId), eventId, targetUserId);
    }

}
