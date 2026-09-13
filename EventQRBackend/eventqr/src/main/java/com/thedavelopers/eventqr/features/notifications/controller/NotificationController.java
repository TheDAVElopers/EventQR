package com.thedavelopers.eventqr.features.notifications.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.NotificationStatus;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtService jwtService;
    public NotificationController(NotificationService notificationService, JwtService jwtService) {
        this.notificationService = notificationService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> mine(HttpServletRequest request,
                                                                        @RequestParam(required = false) NotificationStatus status) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        if (status != null) {
            return ResponseEntity.ok(ApiResponse.success(notificationService.findByRecipientAndStatus(userId, status)));
        }
        return ResponseEntity.ok(ApiResponse.success(notificationService.findByRecipient(userId)));
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationResponse>> findOne(HttpServletRequest request,
                                                                     @PathVariable UUID notificationId) {
        requireNotificationAccess(request, notificationId);
        return ResponseEntity.ok(ApiResponse.success(notificationService.findOne(notificationId)));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(HttpServletRequest request,
                                                                      @PathVariable UUID notificationId) {
        requireNotificationAccess(request, notificationId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", notificationService.markRead(notificationId)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(HttpServletRequest request) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> delete(HttpServletRequest request, @PathVariable UUID notificationId) {
        requireNotificationAccess(request, notificationId);
        notificationService.delete(notificationId);
        return ResponseEntity.ok(ApiResponse.success("Notification deleted", null));
    }

    @GetMapping("/recipient/{recipientUserId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> findByRecipient(HttpServletRequest request,
                                                                                   @PathVariable UUID recipientUserId) {
        requireRecipientOrAdmin(request, recipientUserId);
        return ResponseEntity.ok(ApiResponse.success(notificationService.findByRecipient(recipientUserId)));
    }

    private void requireSenderRole(HttpServletRequest request) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ATTENDEE) {
            throw new ForbiddenException("Attendees cannot create notifications");
        }
    }

    private void requireNotificationAccess(HttpServletRequest request, UUID notificationId) {
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        NotificationResponse notification = notificationService.findOne(notificationId);
        if (notification.recipientUserId().equals(callerId)) {
            return;
        }
        throw new ForbiddenException("Access denied to notification");
    }

    private void requireRecipientOrAdmin(HttpServletRequest request, UUID recipientUserId) {
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        if (recipientUserId.equals(callerId)) {
            return;
        }
        throw new ForbiddenException("Access denied to notifications");
    }

}