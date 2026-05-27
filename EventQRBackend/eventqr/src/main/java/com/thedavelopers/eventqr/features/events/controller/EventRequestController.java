package com.thedavelopers.eventqr.features.events.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse;
import com.thedavelopers.eventqr.features.events.service.EventCreationRequestService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/event-requests")
public class EventRequestController {

    private final EventCreationRequestService eventCreationRequestService;
    private final JwtService jwtService;

    public EventRequestController(EventCreationRequestService eventCreationRequestService, JwtService jwtService) {
        this.eventCreationRequestService = eventCreationRequestService;
        this.jwtService = jwtService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventRequestResponse>> create(HttpServletRequest servletRequest,
                                                                    @Valid @RequestBody EventCreationRequestDto request) {
        UUID userId = jwtService.extractUserIdFromBearer(servletRequest.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success("Event request submitted",
                eventCreationRequestService.create(userId, request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<EventRequestResponse>>> mine(HttpServletRequest request) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success(eventCreationRequestService.findMine(userId)));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<EventRequestResponse>> findOne(HttpServletRequest request,
                                                                     @PathVariable UUID requestId) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        var role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success(eventCreationRequestService.findOne(userId, role, requestId)));
    }
}
