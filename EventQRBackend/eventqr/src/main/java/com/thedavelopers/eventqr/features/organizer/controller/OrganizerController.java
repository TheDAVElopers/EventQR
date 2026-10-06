package com.thedavelopers.eventqr.features.organizer.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.*;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerAttendeeResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerDashboardResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerEventResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerScanPurposeRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerScanPurposeResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerStaffResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerTransactionResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerTransactionRuleResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.StaffAssignmentRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.StaffAssignmentUpdateRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.UserSearchResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.RewardSettingsRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest;
import com.thedavelopers.eventqr.features.organizer.service.OrganizerService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

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
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.listEvents(currentUserId(request))));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<OrganizerDashboardResponse>> dashboard(HttpServletRequest request) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.dashboard(currentUserId(request))));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<OrganizerEventResponse>> event(HttpServletRequest request,
                                                                     @PathVariable UUID eventId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.event(currentUserId(request), eventId, currentRole(request))));
    }

    @PatchMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(HttpServletRequest request,
                                                                  @PathVariable UUID eventId,
                                                                  @Valid @RequestBody EventRequest body) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Event updated", organizerService.updateEvent(currentUserId(request), eventId, currentRole(request), body)));
    }

    @PatchMapping("/events/{eventId}/status")
    public ResponseEntity<ApiResponse<EventResponse>> updateEventStatus(HttpServletRequest request,
                                                                       @PathVariable UUID eventId,
                                                                       @RequestParam String status) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Event status updated",
                organizerService.updateStatus(currentUserId(request), eventId, currentRole(request), com.thedavelopers.eventqr.shared.constants.EventStatus.valueOf(status))));
    }

    @GetMapping("/events/{eventId}/dashboard")
    public ResponseEntity<ApiResponse<OrganizerDashboardResponse>> eventDashboard(HttpServletRequest request,
                                                                                 @PathVariable UUID eventId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.dashboard(currentUserId(request), eventId, currentRole(request))));
    }

    @GetMapping("/events/{eventId}/attendees")
    public ResponseEntity<ApiResponse<List<OrganizerAttendeeResponse>>> attendees(HttpServletRequest request,
                                                                                 @PathVariable UUID eventId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.attendees(currentUserId(request), eventId, currentRole(request))));
    }

    @GetMapping("/events/{eventId}/attendees/search")
    public ResponseEntity<ApiResponse<List<OrganizerAttendeeResponse>>> searchAttendees(HttpServletRequest request,
                                                                                       @PathVariable UUID eventId,
                                                                                       @RequestParam String query) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.searchAttendees(currentUserId(request), eventId, currentRole(request), query)));
    }

    @GetMapping("/events/{eventId}/attendees/{attendeeId}")
    public ResponseEntity<ApiResponse<OrganizerAttendeeResponse>> attendee(HttpServletRequest request,
                                                                          @PathVariable UUID eventId,
                                                                          @PathVariable UUID attendeeId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.attendee(currentUserId(request), eventId, currentRole(request), attendeeId)));
    }

    @PatchMapping("/events/{eventId}/attendees/{attendeeId}/status")
    public ResponseEntity<ApiResponse<OrganizerAttendeeResponse>> updateAttendeeStatus(HttpServletRequest request,
                                                                                      @PathVariable UUID eventId,
                                                                                      @PathVariable UUID attendeeId,
                                                                                      @RequestParam String status) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Attendee status updated",
                organizerService.updateAttendeeStatus(currentUserId(request), eventId, currentRole(request), attendeeId, status)));
    }

    @GetMapping("/events/{eventId}/transactions")
    public ResponseEntity<ApiResponse<List<OrganizerTransactionResponse>>> transactions(HttpServletRequest request,
                                                                                       @PathVariable UUID eventId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.transactions(currentUserId(request), eventId, currentRole(request))));
    }

    @GetMapping("/events/{eventId}/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse>> transaction(HttpServletRequest request,
                                                                                                                              @PathVariable UUID eventId,
                                                                                                                              @PathVariable UUID transactionId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.transaction(currentUserId(request), eventId, currentRole(request), transactionId)));
    }

    @GetMapping("/events/{eventId}/staff")
    public ResponseEntity<ApiResponse<List<OrganizerStaffResponse>>> staff(HttpServletRequest request,
                                                                           @PathVariable UUID eventId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.staff(currentUserId(request), eventId, currentRole(request))));
    }

    @GetMapping("/events/{eventId}/staff/search")
    public ResponseEntity<ApiResponse<List<UserSearchResponse>>> searchEventStaffUsers(HttpServletRequest request,
                                                                                      @PathVariable UUID eventId,
                                                                                      @RequestParam String query) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.searchUsers(currentUserId(request), eventId, currentRole(request), query)));
    }

    @PostMapping("/events/{eventId}/staff")
    public ResponseEntity<ApiResponse<OrganizerStaffResponse>> addStaff(HttpServletRequest request,
                                                                        @PathVariable UUID eventId,
                                                                        @Valid @RequestBody StaffAssignmentRequest body) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Staff assigned", organizerService.addStaff(currentUserId(request), eventId, currentRole(request), body)));
    }

    @PatchMapping("/events/{eventId}/staff/{assignmentId}")
    public ResponseEntity<ApiResponse<OrganizerStaffResponse>> updateStaff(HttpServletRequest request,
                                                                           @PathVariable UUID eventId,
                                                                           @PathVariable UUID assignmentId,
                                                                           @Valid @RequestBody StaffAssignmentUpdateRequest body) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Staff assignment updated",
                organizerService.updateStaff(currentUserId(request), eventId, currentRole(request), assignmentId, body)));
    }

    @DeleteMapping("/events/{eventId}/staff/{assignmentId}")
    public ResponseEntity<ApiResponse<Void>> removeStaff(HttpServletRequest request,
                                                         @PathVariable UUID eventId,
                                                         @PathVariable UUID assignmentId) {
        requireOrganizer(request);
        organizerService.removeStaff(currentUserId(request), eventId, currentRole(request), assignmentId);
        return ResponseEntity.ok(ApiResponse.success("Staff assignment removed", null));
    }

    @GetMapping("/users/search")
    public ResponseEntity<ApiResponse<List<UserSearchResponse>>> searchUsers(HttpServletRequest request,
                                                                             @RequestParam String query) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.searchUsers(currentUserId(request), query)));
    }

    @GetMapping("/events/{eventId}/scan-purposes")
    public ResponseEntity<ApiResponse<List<OrganizerScanPurposeResponse>>> scanPurposes(HttpServletRequest request,
                                                                                        @PathVariable UUID eventId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.scanPurposes(currentUserId(request), eventId, currentRole(request))));
    }

    @PostMapping("/events/{eventId}/scan-purposes")
    public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> createScanPurpose(HttpServletRequest request,
                                                                                      @PathVariable UUID eventId,
                                                                                      @Valid @RequestBody OrganizerScanPurposeRequest body) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Scan purpose saved",
                organizerService.saveScanPurpose(currentUserId(request), eventId, currentRole(request), body)));
    }

    @PatchMapping("/events/{eventId}/scan-purposes/{purposeId}")
    public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> updateScanPurpose(HttpServletRequest request,
                                                                                      @PathVariable UUID eventId,
                                                                                      @PathVariable UUID purposeId,
                                                                                      @Valid @RequestBody OrganizerScanPurposeRequest body) {
        requireOrganizer(request);
        OrganizerScanPurposeRequest merged = new OrganizerScanPurposeRequest(purposeId, body.title(), body.code(),
                body.enabled(), body.trackingOnly(), body.pointsEnabled(), body.pointsValue(), body.allowDuplicate(),
                body.duplicateRuleSummary(), body.requiredSelectionLabel(), body.description());
        return ResponseEntity.ok(ApiResponse.success("Scan purpose saved",
                organizerService.saveScanPurpose(currentUserId(request), eventId, currentRole(request), merged)));
    }

        @DeleteMapping("/events/{eventId}/scan-purposes/{purposeId}")
        public ResponseEntity<ApiResponse<Void>> deleteScanPurpose(HttpServletRequest request,
                                       @PathVariable UUID eventId,
                                       @PathVariable UUID purposeId) {
        requireOrganizer(request);
        organizerService.deleteScanPurpose(currentUserId(request), eventId, currentRole(request), purposeId);
        return ResponseEntity.ok(ApiResponse.success("Scan purpose deleted", null));
        }

        @PatchMapping("/events/{eventId}/scan-purposes/{purposeId}/enable")
        public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> enableScanPurpose(HttpServletRequest request,
                                                   @PathVariable UUID eventId,
                                                   @PathVariable UUID purposeId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Scan purpose enabled",
            organizerService.enableScanPurpose(currentUserId(request), eventId, currentRole(request), purposeId, true)));
        }

        @PatchMapping("/events/{eventId}/scan-purposes/{purposeId}/disable")
        public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> disableScanPurpose(HttpServletRequest request,
                                                @PathVariable UUID eventId,
                                                @PathVariable UUID purposeId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Scan purpose disabled",
            organizerService.enableScanPurpose(currentUserId(request), eventId, currentRole(request), purposeId, false)));
        }

            @PatchMapping("/events/{eventId}/scan-purposes/{purposeId}/tracking-only")
            public ResponseEntity<ApiResponse<OrganizerScanPurposeResponse>> trackingOnly(HttpServletRequest request,
                                                  @PathVariable UUID eventId,
                                                  @PathVariable UUID purposeId,
                                                  @RequestParam boolean trackingOnly) {
            requireOrganizer(request);
            return ResponseEntity.ok(ApiResponse.success("Scan purpose updated",
                organizerService.toggleTrackingOnly(currentUserId(request), eventId, currentRole(request), purposeId, trackingOnly)));
            }

        @GetMapping("/events/{eventId}/transaction-rules")
        public ResponseEntity<ApiResponse<List<OrganizerTransactionRuleResponse>>> transactionRules(HttpServletRequest request,
                                                                    @PathVariable UUID eventId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.listTransactionRules(currentUserId(request), eventId, currentRole(request))));
        }

        @PutMapping("/events/{eventId}/transaction-rules")
        public ResponseEntity<ApiResponse<OrganizerTransactionRuleResponse>> updateTransactionRule(HttpServletRequest request,
                                                                          @PathVariable UUID eventId,
                                                                          @Valid @RequestBody TransactionRuleRequest body) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Transaction rule saved",
            organizerService.saveTransactionRule(currentUserId(request), eventId, currentRole(request), body)));
        }

        @PatchMapping("/events/{eventId}/transaction-rules/{ruleId}")
        public ResponseEntity<ApiResponse<OrganizerTransactionRuleResponse>> patchTransactionRule(HttpServletRequest request,
                                                                          @PathVariable UUID eventId,
                                                                          @PathVariable UUID ruleId,
                                                                          @Valid @RequestBody TransactionRuleRequest body) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Transaction rule saved",
            organizerService.saveTransactionRule(currentUserId(request), eventId, currentRole(request), ruleId, body)));
        }

        @GetMapping("/events/{eventId}/reward-settings")
        public ResponseEntity<ApiResponse<Boolean>> rewardSettings(HttpServletRequest request,
                                       @PathVariable UUID eventId) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success(organizerService.event(currentUserId(request), eventId, currentRole(request)).rewardsStatus().equals("Enabled")));
        }

        @PatchMapping("/events/{eventId}/reward-settings")
    public ResponseEntity<ApiResponse<EventResponse>> updateRewardSettings(HttpServletRequest request,
                                           @PathVariable UUID eventId,
                                           @Valid @RequestBody RewardSettingsRequest body) {
        requireOrganizer(request);
        return ResponseEntity.ok(ApiResponse.success("Reward settings updated",
            organizerService.updateRewardSettings(currentUserId(request), eventId, currentRole(request), body)));
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

    private AccountRole currentRole(HttpServletRequest request) {
        return jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
    }
}