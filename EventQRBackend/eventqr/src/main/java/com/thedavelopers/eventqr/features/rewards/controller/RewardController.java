package com.thedavelopers.eventqr.features.rewards.controller;

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

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final RewardService rewardService;
    private final EventService eventService;
    private final JwtService jwtService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;
    private final EventRegistrationRepository eventRegistrationRepository;

    public RewardController(RewardService rewardService, EventService eventService,
                            JwtService jwtService, EventStaffAssignmentRepository eventStaffAssignmentRepository,
                            EventRegistrationRepository eventRegistrationRepository) {
        this.rewardService = rewardService;
        this.eventService = eventService;
        this.jwtService = jwtService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RewardResponse>> saveReward(HttpServletRequest request,
                                                                  @Valid @RequestBody RewardRequest body) {
        requireRewardWriteAccess(request, body.eventId());
        return ResponseEntity.ok(ApiResponse.success("Reward saved", rewardService.saveReward(body)));
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<RewardRedemptionResponse>> redeem(HttpServletRequest request,
                                                                        @Valid @RequestBody RewardRedemptionRequest body) {
        requireRedeemAccess(request, body);
        return ResponseEntity.ok(ApiResponse.success("Reward redeemed", rewardService.redeem(body)));
    }

    // Intentionally unguarded: attendee-floor browse semantics for the reward catalog.
    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<List<RewardResponse>>> findRewards(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRewardsForAttendee(eventId)));
    }

    @GetMapping("/balance/{eventId}/{attendeeUserId}")
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getBalance(HttpServletRequest request,
                                                                        @PathVariable UUID eventId,
                                                                        @PathVariable UUID attendeeUserId) {
        requireBalanceAccess(request, eventId, attendeeUserId);
        return ResponseEntity.ok(ApiResponse.success(rewardService.getBalance(eventId, attendeeUserId)));
    }

    @GetMapping("/redemptions/{eventId}")
    public ResponseEntity<ApiResponse<List<RewardRedemptionResponse>>> findRedemptions(HttpServletRequest request,
                                                                                       @PathVariable UUID eventId) {
        requireEventAccess(request, eventId);
        if (jwtService.extractRoleFromBearer(request.getHeader("Authorization")) == AccountRole.ATTENDEE) {
            UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
            return ResponseEntity.ok(ApiResponse.success(rewardService.findRedemptions(eventId, callerId)));
        }
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRedemptions(eventId)));
    }

    private void requireRewardWriteAccess(HttpServletRequest request, UUID eventId) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ORGANIZER) {
            if (eventService.findOne(eventId).organizerUserId().equals(callerId)) {
                return;
            }
            throw new ForbiddenException("Event ownership required");
        }
        if (role == AccountRole.STAFF) {
            EventStaffAssignment assignment = eventStaffAssignmentRepository
                    .findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)
                    .orElseThrow(() -> new ForbiddenException("Staff user is not actively assigned to this event"));
            if (assignment.isCanManageRewards()) {
                return;
            }
            throw new ForbiddenException("Staff user is not allowed to manage rewards for this event");
        }
        throw new ForbiddenException("Organizer or admin access required to create rewards");
    }

    private void requireRedeemAccess(HttpServletRequest request, RewardRedemptionRequest body) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        // Authorization only. Whether the attendee holds an active (REGISTERED / ENTERED / EXITED) registration is
        // checked by RewardService.redeem for every caller (admins included), so the answer is always the same 404.
        if (role == AccountRole.ORGANIZER || role == AccountRole.STAFF) {
            requireScanlessRedeemManager(role, callerId, body.eventId());
            return;
        }
        if (role == AccountRole.ATTENDEE) {
            if (callerId.equals(body.attendeeUserId())) {
                return;
            }
            throw new ForbiddenException("Attendee can only redeem own rewards");
        }
        throw new ForbiddenException("Access denied to reward redemption");
    }

    /**
     * Scan-less redemption on someone's behalf, same rule as StaffController.requireRewardManager: the owning
     * organizer passes; any other organizer or staff member needs an active assignment with can_manage_rewards.
     */
    private void requireScanlessRedeemManager(AccountRole role, UUID callerId, UUID eventId) {
        if (role == AccountRole.ORGANIZER && eventService.findOne(eventId).organizerUserId().equals(callerId)) {
            return;
        }
        EventStaffAssignment assignment = eventStaffAssignmentRepository
                .findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)
                .orElseThrow(() -> new ForbiddenException(role == AccountRole.ORGANIZER
                        ? "Organizer can only redeem for attendees of owned events"
                        : "Staff user is not actively assigned to this event"));
        if (!assignment.isCanManageRewards()) {
            throw new ForbiddenException("Staff user is not allowed to manage rewards for this event");
        }
    }

    private void requireEventAccess(HttpServletRequest request, UUID eventId) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ATTENDEE) {
            if (eventRegistrationRepository.existsByEventIdAndAttendeeUserId(eventId, callerId)) {
                return;
            }
            throw new ForbiddenException("Attendee is not registered for this event");
        }
        if (role == AccountRole.ORGANIZER) {
            if (eventService.findOne(eventId).organizerUserId().equals(callerId)) {
                return;
            }
            throw new ForbiddenException("Event ownership required");
        }
        if (role == AccountRole.STAFF) {
            if (eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)) {
                return;
            }
            throw new ForbiddenException("Staff user is not actively assigned to this event");
        }
        throw new ForbiddenException("Organizer or staff access required");
    }

    private void requireBalanceAccess(HttpServletRequest request, UUID eventId, UUID attendeeUserId) {
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        if (callerId.equals(attendeeUserId)) {
            return;
        }
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        if (role == AccountRole.ORGANIZER) {
            if (eventService.findOne(eventId).organizerUserId().equals(callerId)) {
                return;
            }
            throw new ForbiddenException("Event ownership required");
        }
        if (role == AccountRole.STAFF) {
            if (eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)) {
                return;
            }
        }
        throw new ForbiddenException("Access denied to reward balance");
    }
}
