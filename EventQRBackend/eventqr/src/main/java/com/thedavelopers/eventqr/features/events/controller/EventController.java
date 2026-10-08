package com.thedavelopers.eventqr.features.events.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse;
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse;
import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationSubmissionResponse;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.ClientIp;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final JwtService jwtService;

    public EventController(EventService eventService, RegistrationService registrationService, JwtService jwtService) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.jwtService = jwtService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> create(HttpServletRequest request, @Valid @RequestBody EventRequest event) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Event submitted", eventService.create(currentUserId(request), event)));
    }

    @PutMapping("/{eventId}/review")
    public ResponseEntity<ApiResponse<EventResponse>> review(HttpServletRequest request,
                                                             @PathVariable UUID eventId,
                                                             @Valid @RequestBody EventApprovalRequest approvalRequest) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Event reviewed", eventService.review(eventId, approvalRequest)));
    }

    @PutMapping("/{eventId}/activate")
    public ResponseEntity<ApiResponse<EventResponse>> activate(HttpServletRequest request, @PathVariable UUID eventId) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Event activated", eventService.activate(eventId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EventResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(eventService.findAllEvents(PageRequest.of(page, size))));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<AttendeeEventResponse>> findOne(HttpServletRequest request, @PathVariable UUID eventId) {
        UUID userId = currentUserId(request);
        return ResponseEntity.ok(ApiResponse.success(eventService.findAttendeeEvent(eventId, userId)));
    }

    @GetMapping("/{eventId}/availability")
    public ResponseEntity<ApiResponse<EventAvailabilityResponse>> availability(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.availability(eventId)));
    }

    @PostMapping("/{eventId}/registrations")
    public ResponseEntity<ApiResponse<RegistrationSubmissionResponse>> register(HttpServletRequest httpRequest,
                                                                                @PathVariable UUID eventId,
                                                                                @Valid @RequestBody RegistrationRequest request) {
        RegistrationRequest normalized = new RegistrationRequest(eventId, request.email(), request.fullName(), request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.success("Registration completed", registrationService.registerAs(normalized,
                currentUserId(httpRequest), jwtService.extractRoleFromBearer(httpRequest.getHeader("Authorization")),
                ClientIp.from(httpRequest))));
    }

    @GetMapping("/attendee-visible")
    public ResponseEntity<ApiResponse<Page<AttendeeEventResponse>>> listAttendeeVisible(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = currentUserId(request);
        return ResponseEntity.ok(ApiResponse.success(eventService.findAttendeeVisibleEvents(userId, attendeePage(page, size))));
    }

    @GetMapping("/attendee-browse")
    public ResponseEntity<ApiResponse<Page<AttendeeEventResponse>>> listAttendeeBrowse(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = currentUserId(request);
        return ResponseEntity.ok(ApiResponse.success(eventService.findAttendeeBrowseEvents(userId, attendeePage(page, size))));
    }

    private static final int MAX_PAGE_SIZE = 100;

    private static PageRequest attendeePage(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize,
                org.springframework.data.domain.Sort.by("eventStartAt").ascending().and(org.springframework.data.domain.Sort.by("id").ascending()));
    }

    private void requireOrganizer(HttpServletRequest request) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ORGANIZER || role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Organizer access required");
    }

    private UUID currentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }

    private void requireAdmin(HttpServletRequest request) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role != AccountRole.ADMIN && role != AccountRole.SUPER_ADMIN) {
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Admin access required");
        }
    }
}
