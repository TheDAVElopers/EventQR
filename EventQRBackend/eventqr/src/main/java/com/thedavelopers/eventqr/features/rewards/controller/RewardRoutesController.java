package com.thedavelopers.eventqr.features.rewards.controller;

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

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1")
public class RewardRoutesController {

    private final RewardService rewardService;
    private final JwtService jwtService;
    private final EventService eventService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;

    public RewardRoutesController(RewardService rewardService, JwtService jwtService,
                                  EventService eventService, EventStaffAssignmentRepository eventStaffAssignmentRepository) {
        this.rewardService = rewardService;
        this.jwtService = jwtService;
        this.eventService = eventService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
    }

    @GetMapping("/events/{eventId}/rewards")
    public ResponseEntity<ApiResponse<List<RewardResponse>>> eventRewards(HttpServletRequest request,
                                                                          @PathVariable UUID eventId) {
        requireOwnerOrStaff(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRewards(eventId)));
    }

    @GetMapping("/events/{eventId}/rewards/{rewardId}")
    public ResponseEntity<ApiResponse<RewardResponse>> eventReward(HttpServletRequest request,
                                                                   @PathVariable UUID eventId,
                                                                   @PathVariable UUID rewardId) {
        requireOwnerOrStaff(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(rewardService.findReward(eventId, rewardId)));
    }

    @GetMapping("/organizer/events/{eventId}/rewards")
    public ResponseEntity<ApiResponse<List<RewardResponse>>> organizerRewards(HttpServletRequest request,
                                                                              @PathVariable UUID eventId) {
        requireOwnerOrStaff(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRewards(eventId)));
    }

    @PostMapping("/organizer/events/{eventId}/rewards")
    public ResponseEntity<ApiResponse<RewardResponse>> createReward(HttpServletRequest request,
                                                                    @PathVariable UUID eventId,
                                                                    @Valid @RequestBody RewardRequest body) {
        requireOwnerOrStaff(request, eventId);
        RewardRequest normalized = new RewardRequest(eventId, body.name(), body.pointsRequired(), body.stockQuantity(), body.allowDuplicateClaims());
        return ResponseEntity.ok(ApiResponse.success("Reward created", rewardService.saveReward(normalized)));
    }

    @PatchMapping("/organizer/events/{eventId}/rewards/{rewardId}")
    public ResponseEntity<ApiResponse<RewardResponse>> updateReward(HttpServletRequest request,
                                                                    @PathVariable UUID eventId,
                                                                    @PathVariable UUID rewardId,
                                                                    @Valid @RequestBody RewardRequest body) {
        requireOwnerOrStaff(request, eventId);
        RewardRequest normalized = new RewardRequest(eventId, body.name(), body.pointsRequired(), body.stockQuantity(), body.allowDuplicateClaims());
        return ResponseEntity.ok(ApiResponse.success("Reward updated", rewardService.updateReward(eventId, rewardId, normalized)));
    }

    @DeleteMapping("/organizer/events/{eventId}/rewards/{rewardId}")
    public ResponseEntity<ApiResponse<Void>> deleteReward(HttpServletRequest request,
                                                          @PathVariable UUID eventId,
                                                          @PathVariable UUID rewardId) {
        requireOwnerOrStaff(request, eventId);
        rewardService.deleteReward(eventId, rewardId);
        return ResponseEntity.ok(ApiResponse.success("Reward deleted", null));
    }

    @GetMapping("/organizer/events/{eventId}/claimed-rewards")
    public ResponseEntity<ApiResponse<List<RewardRedemptionResponse>>> organizerClaimedRewards(HttpServletRequest request,
                                                                                               @PathVariable UUID eventId) {
        requireOwnerOrStaff(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRedemptions(eventId)));
    }

    @GetMapping("/attendees/me/events/{eventId}/claimed-rewards")
    public ResponseEntity<ApiResponse<List<RewardRedemptionResponse>>> attendeeClaimedRewards(HttpServletRequest request,
                                                                                              @PathVariable UUID eventId) {
        UUID userId = currentUserId(request);
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRedemptions(eventId, userId)));
    }

    @GetMapping("/attendees/me/events/{eventId}/rewards")
    public ResponseEntity<ApiResponse<List<RewardResponse>>> attendeeRewards(HttpServletRequest request,
                                                                              @PathVariable UUID eventId) {
        UUID userId = currentUserId(request);
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRewards(eventId)));
    }

    @GetMapping("/attendees/me/events/{eventId}/points")
    public ResponseEntity<ApiResponse<PointBalanceResponse>> attendeePoints(HttpServletRequest request,
                                                                         @PathVariable UUID eventId) {
        UUID userId = currentUserId(request);
        return ResponseEntity.ok(ApiResponse.success(rewardService.getBalance(eventId, userId)));
    }

    @GetMapping("/attendees/me/events/{eventId}/point-transactions")
    public ResponseEntity<ApiResponse<List<PointTransaction>>> attendeePointTransactions(HttpServletRequest request,
                                                                                            @PathVariable UUID eventId) {
        UUID userId = currentUserId(request);
        return ResponseEntity.ok(ApiResponse.success(rewardService.findPointTransactions(eventId, userId)));
    }

    @GetMapping("/organizer/events/{eventId}/point-transactions")
    public ResponseEntity<ApiResponse<List<PointTransaction>>> organizerPointTransactions(HttpServletRequest request,
                                                                                          @PathVariable UUID eventId) {
        requireOwnerOrStaff(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(rewardService.findPointTransactions(eventId)));
    }

    private UUID currentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }

    private void requireNonAttendee(HttpServletRequest request) {
        if (jwtService.extractRoleFromBearer(request.getHeader("Authorization")) == AccountRole.ATTENDEE) {
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Organizer or admin access required");
        }
    }

    private void requireOwnerOrStaff(HttpServletRequest request, UUID eventId) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        UUID callerId = currentUserId(request);
        if (role == AccountRole.ORGANIZER) {
            if (eventService.findOne(eventId).organizerUserId().equals(callerId)) {
                return;
            }
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Event ownership required");
        }
        if (role == AccountRole.STAFF) {
            if (eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)) {
                return;
            }
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Staff user is not actively assigned to this event");
        }
        throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Organizer or staff access required");
    }
}
