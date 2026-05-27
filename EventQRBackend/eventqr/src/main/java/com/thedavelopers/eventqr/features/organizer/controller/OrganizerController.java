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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.*;
import com.thedavelopers.eventqr.features.organizer.model.dto.RewardSettingsRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest;
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

    @GetMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<OrganizerEventResponse>> event(HttpServletRequest request,
                                                                     @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.event(currentUserId(request), eventId)));
    }

    @PatchMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(HttpServletRequest request,
                                                               @PathVariable UUID eventId,
                                                               @Valid @RequestBody EventRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Event updated", organizerService.updateEvent(currentUserId(request), eventId, body)));
    }

    @PatchMapping("/events/{eventId}/status")
    public ResponseEntity<ApiResponse<EventResponse>> updateEventStatus(HttpServletRequest request,
                                                                       @PathVariable UUID eventId,
                                                                       @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success("Event status updated",
                organizerService.updateStatus(currentUserId(request), eventId, com.thedavelopers.eventqr.shared.constants.EventStatus.valueOf(status))));
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

    @GetMapping("/events/{eventId}/attendees/search")
    public ResponseEntity<ApiResponse<List<OrganizerAttendeeResponse>>> searchAttendees(HttpServletRequest request,
                                                                                       @PathVariable UUID eventId,
                                                                                       @RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.searchAttendees(currentUserId(request), eventId, query)));
    }

    @GetMapping("/events/{eventId}/attendees/{attendeeId}")
    public ResponseEntity<ApiResponse<OrganizerAttendeeResponse>> attendee(HttpServletRequest request,
                                                                           @PathVariable UUID eventId,
                                                                           @PathVariable UUID attendeeId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.attendee(currentUserId(request), eventId, attendeeId)));
    }

    @PatchMapping("/events/{eventId}/attendees/{attendeeId}/status")
    public ResponseEntity<ApiResponse<OrganizerAttendeeResponse>> updateAttendeeStatus(HttpServletRequest request,
                                                                                       @PathVariable UUID eventId,
                                                                                       @PathVariable UUID attendeeId,
                                                                                       @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success("Attendee status updated",
                organizerService.updateAttendeeStatus(currentUserId(request), eventId, attendeeId, status)));
    }

    @GetMapping("/events/{eventId}/transactions")
    public ResponseEntity<ApiResponse<List<OrganizerTransactionResponse>>> transactions(HttpServletRequest request,
                                                                                       @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.transactions(currentUserId(request), eventId)));
    }

    @GetMapping("/events/{eventId}/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse>> transaction(HttpServletRequest request,
                                                                                                                              @PathVariable UUID eventId,
                                                                                                                              @PathVariable UUID transactionId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.transaction(currentUserId(request), eventId, transactionId)));
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

        @DeleteMapping("/events/{eventId}/scan-purposes/{purposeId}")
        public ResponseEntity<ApiResponse<Void>> deleteScanPurpose(HttpServletRequest request,
                                       @PathVariable UUID eventId,
                                       @PathVariable UUID purposeId) {
        organizerService.deleteScanPurpose(currentUserId(request), eventId, purposeId);
        return ResponseEntity.ok(ApiResponse.success("Scan purpose deleted", null));
        }

        @PatchMapping("/events/{eventId}/scan-purposes/{purposeId}/enable")
        public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> enableScanPurpose(HttpServletRequest request,
                                                   @PathVariable UUID eventId,
                                                   @PathVariable UUID purposeId) {
        return ResponseEntity.ok(ApiResponse.success("Scan purpose enabled",
            organizerService.enableScanPurpose(currentUserId(request), eventId, purposeId, true)));
        }

        @PatchMapping("/events/{eventId}/scan-purposes/{purposeId}/disable")
        public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> disableScanPurpose(HttpServletRequest request,
                                                @PathVariable UUID eventId,
                                                @PathVariable UUID purposeId) {
        return ResponseEntity.ok(ApiResponse.success("Scan purpose disabled",
            organizerService.enableScanPurpose(currentUserId(request), eventId, purposeId, false)));
        }

            @PatchMapping("/events/{eventId}/scan-purposes/{purposeId}/tracking-only")
            public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> trackingOnly(HttpServletRequest request,
                                                  @PathVariable UUID eventId,
                                                  @PathVariable UUID purposeId,
                                                  @RequestParam boolean trackingOnly) {
            return ResponseEntity.ok(ApiResponse.success("Scan purpose updated",
                organizerService.toggleTrackingOnly(currentUserId(request), eventId, purposeId, trackingOnly)));
            }

        @GetMapping("/events/{eventId}/transaction-rules")
        public ResponseEntity<ApiResponse<List<com.thedavelopers.eventqr.features.transactions.model.entity.TransactionRule>>> transactionRules(HttpServletRequest request,
                                                                        @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.listTransactionRules(currentUserId(request), eventId)));
        }

        @PutMapping("/events/{eventId}/transaction-rules")
        public ResponseEntity<ApiResponse<com.thedavelopers.eventqr.features.transactions.model.entity.TransactionRule>> updateTransactionRule(HttpServletRequest request,
                                                                          @PathVariable UUID eventId,
                                                                          @Valid @RequestBody TransactionRuleRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Transaction rule saved",
            organizerService.saveTransactionRule(currentUserId(request), eventId, body)));
        }

        @GetMapping("/events/{eventId}/reward-settings")
        public ResponseEntity<ApiResponse<Boolean>> rewardSettings(HttpServletRequest request,
                                       @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(organizerService.event(currentUserId(request), eventId).rewardsStatus().equals("Enabled")));
        }

        @PatchMapping("/events/{eventId}/reward-settings")
    public ResponseEntity<ApiResponse<EventResponse>> updateRewardSettings(HttpServletRequest request,
                                           @PathVariable UUID eventId,
                                           @Valid @RequestBody RewardSettingsRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Reward settings updated",
            organizerService.updateRewardSettings(currentUserId(request), eventId, body)));
    }

    private UUID currentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }
}
