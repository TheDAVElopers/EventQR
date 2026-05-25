package com.thedavelopers.eventqr.features.notifications.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationRequest;
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> create(@Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Notification created", notificationService.create(request)));
    }

    @GetMapping("/recipient/{recipientUserId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> findByRecipient(@PathVariable UUID recipientUserId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.findByRecipient(recipientUserId)));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> findByEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.findByEvent(eventId)));
    }
}