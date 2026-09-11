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
        requireEventAccess(request, body.eventId());
        return ResponseEntity.ok(ApiResponse.success("Reward saved", rewardService.saveReward(body)));
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<RewardRedemptionResponse>> redeem(HttpServletRequest request,
                                                                        @Valid @RequestBody RewardRedemptionRequest body) {
        requireEventAccess(request, body.eventId());
        return ResponseEntity.ok(ApiResponse.success("Reward redeemed", rewardService.redeem(body)));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<List<RewardResponse>>> findRewards(HttpServletRequest request,
                                                                         @PathVariable UUID eventId) {
        requireEventReadAccess(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRewards(eventId)));
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
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRedemptions(eventId)));
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

    private void requireEventReadAccess(HttpServletRequest request, UUID eventId) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        if (role == AccountRole.ATTENDEE) {
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
