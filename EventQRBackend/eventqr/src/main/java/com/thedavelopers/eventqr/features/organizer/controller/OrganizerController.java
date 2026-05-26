package com.thedavelopers.eventqr.features.organizer.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.*;
import com.thedavelopers.eventqr.features.organizer.service.OrganizerService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/organizer")
public class OrganizerController {

    private final OrganizerService organizerService;
    private final JwtService jwtService;

    public OrganizerController(OrganizerService organizerService, JwtService jwtService) {
        this.organizerService = organizerService;
        this.jwtService = jwtService;
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<OrganizerEventResponse>>> events(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.listEvents(currentUserId(request))));
    }

    @GetMapping("/events/{eventId}/dashboard")
    public ResponseEntity<ApiResponse<OrganizerDashboardResponse>> dashboard(HttpServletRequest request,
                                                                             @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.dashboard(currentUserId(request), eventId)));
    }

    @GetMapping("/events/{eventId}/attendees")
    public ResponseEntity<ApiResponse<List<OrganizerAttendeeResponse>>> attendees(HttpServletRequest request,
                                                                                  @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.attendees(currentUserId(request), eventId)));
    }

    @GetMapping("/events/{eventId}/attendees/{attendeeId}")
    public ResponseEntity<ApiResponse<OrganizerAttendeeResponse>> attendee(HttpServletRequest request,
                                                                           @PathVariable UUID eventId,
                                                                           @PathVariable UUID attendeeId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.attendee(currentUserId(request), eventId, attendeeId)));
    }

    @GetMapping("/events/{eventId}/transactions")
    public ResponseEntity<ApiResponse<List<OrganizerTransactionResponse>>> transactions(HttpServletRequest request,
                                                                                       @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.transactions(currentUserId(request), eventId)));
    }

    @GetMapping("/events/{eventId}/reports")
    public ResponseEntity<ApiResponse<OrganizerReportResponse>> reports(HttpServletRequest request,
                                                                        @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.report(currentUserId(request), eventId)));
    }

    @GetMapping("/events/{eventId}/staff")
    public ResponseEntity<ApiResponse<List<OrganizerStaffResponse>>> staff(HttpServletRequest request,
                                                                           @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.staff(currentUserId(request), eventId)));
    }

    @PostMapping("/events/{eventId}/staff")
    public ResponseEntity<ApiResponse<OrganizerStaffResponse>> addStaff(HttpServletRequest request,
                                                                        @PathVariable UUID eventId,
                                                                        @Valid @RequestBody StaffAssignmentRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Staff assigned", organizerService.addStaff(currentUserId(request), eventId, body)));
    }

    @PatchMapping("/events/{eventId}/staff/{assignmentId}")
    public ResponseEntity<ApiResponse<OrganizerStaffResponse>> updateStaff(HttpServletRequest request,
                                                                           @PathVariable UUID eventId,
                                                                           @PathVariable UUID assignmentId,
                                                                           @RequestBody StaffAssignmentUpdateRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Staff assignment updated",
                organizerService.updateStaff(currentUserId(request), eventId, assignmentId, body)));
    }

    @DeleteMapping("/events/{eventId}/staff/{assignmentId}")
    public ResponseEntity<ApiResponse<Void>> removeStaff(HttpServletRequest request,
                                                         @PathVariable UUID eventId,
                                                         @PathVariable UUID assignmentId) {
        organizerService.removeStaff(currentUserId(request), eventId, assignmentId);
        return ResponseEntity.ok(ApiResponse.success("Staff assignment removed", null));
    }

    @GetMapping("/users/search")
    public ResponseEntity<ApiResponse<List<UserSearchResponse>>> searchUsers(HttpServletRequest request,
                                                                             @RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.searchUsers(currentUserId(request), query)));
    }

    @GetMapping("/events/{eventId}/scan-purposes")
    public ResponseEntity<ApiResponse<List<OrganizerScanPurposeResponse>>> scanPurposes(HttpServletRequest request,
                                                                                        @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.scanPurposes(currentUserId(request), eventId)));
    }

    @PostMapping("/events/{eventId}/scan-purposes")
    public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> createScanPurpose(HttpServletRequest request,
                                                                                       @PathVariable UUID eventId,
                                                                                       @Valid @RequestBody OrganizerScanPurposeRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Scan purpose saved",
                organizerService.saveScanPurpose(currentUserId(request), eventId, body)));
    }

    @PatchMapping("/events/{eventId}/scan-purposes/{purposeId}")
    public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> updateScanPurpose(HttpServletRequest request,
                                                                                       @PathVariable UUID eventId,
                                                                                       @PathVariable UUID purposeId,
                                                                                       @Valid @RequestBody OrganizerScanPurposeRequest body) {
        OrganizerScanPurposeRequest merged = new OrganizerScanPurposeRequest(purposeId, body.title(), body.code(),
                body.enabled(), body.trackingOnly(), body.pointsEnabled(), body.pointsValue(), body.allowDuplicate(),
                body.duplicateRuleSummary(), body.requiredSelectionLabel(), body.description());
        return ResponseEntity.ok(ApiResponse.success("Scan purpose saved",
                organizerService.saveScanPurpose(currentUserId(request), eventId, merged)));
    }

    private UUID currentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }
}
