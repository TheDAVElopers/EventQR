package com.thedavelopers.eventqr.features.admin.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.users.model.dto.AdminCreateRequest;
import com.thedavelopers.eventqr.features.users.model.dto.ProfileUpdateRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.model.dto.UserRoleRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserStatusRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestDecisionRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse;
import com.thedavelopers.eventqr.features.events.service.EventCreationRequestService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserService userService;
    private final JwtService jwtService;
    private final EventCreationRequestService eventCreationRequestService;

    public AdminController(UserService userService, JwtService jwtService,
                           EventCreationRequestService eventCreationRequestService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.eventCreationRequestService = eventCreationRequestService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers(HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success(userService.findAllUsers()));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> findUser(HttpServletRequest request, @PathVariable UUID userId) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success(userService.findOne(userId)));
    }

    @PostMapping("/users/admins")
    public ResponseEntity<ApiResponse<UserResponse>> createAdmin(HttpServletRequest request,
                                                                 @Valid @RequestBody AdminCreateRequest body) {
        requireAdmin(request);
        UserRequest createRequest = new UserRequest(body.email(), body.fullName(), body.phoneNumber(), body.password(), AccountRole.ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Admin account created", userService.create(createRequest)));
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(HttpServletRequest request,
                                                               @PathVariable UUID userId,
                                                               @Valid @RequestBody ProfileUpdateRequest body) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("User updated", userService.updateProfile(userId, body.fullName(), body.phoneNumber())));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(HttpServletRequest request,
                                                                @PathVariable UUID userId,
                                                                @Valid @RequestBody UserStatusRequest body) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Status updated", userService.updateStatus(userId, body.status())));
    }

    @PatchMapping("/users/{userId}/roles")
    public ResponseEntity<ApiResponse<UserResponse>> updateRole(HttpServletRequest request,
                                                              @PathVariable UUID userId,
                                                              @Valid @RequestBody UserRoleRequest body) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Role updated", userService.changeRoleResponse(userId, body.role())));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(HttpServletRequest request, @PathVariable UUID userId) {
        requireAdmin(request);
        userService.softDelete(userId);
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
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
                                                                                @RequestBody(required = false) EventRequestDecisionRequest body) {
        requireAdmin(request);
        UUID adminUserId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        String remarks = body == null ? null : body.adminRemarks();
        return ResponseEntity.ok(ApiResponse.success("Event request approved",
                eventCreationRequestService.approve(requestId, adminUserId, remarks)));
    }

    @PatchMapping("/event-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<EventRequestResponse>> rejectEventRequest(HttpServletRequest request,
                                                                               @PathVariable UUID requestId,
                                                                               @RequestBody(required = false) EventRequestDecisionRequest body) {
        requireAdmin(request);
        UUID adminUserId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        String remarks = body == null ? null : body.adminRemarks();
        return ResponseEntity.ok(ApiResponse.success("Event request rejected",
                eventCreationRequestService.reject(requestId, adminUserId, remarks)));
    }

    @PatchMapping("/event-requests/{requestId}/upgrade-organizer")
    public ResponseEntity<ApiResponse<EventRequestResponse>> upgradeOrganizer(HttpServletRequest request, @PathVariable UUID requestId) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Requester upgraded to organizer",
                eventCreationRequestService.upgradeOrganizer(requestId)));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (jwtService.extractRoleFromBearer(request.getHeader("Authorization")) != AccountRole.ADMIN) {
            throw new com.thedavelopers.eventqr.shared.exception.ForbiddenException("Admin access required");
        }
    }

}
