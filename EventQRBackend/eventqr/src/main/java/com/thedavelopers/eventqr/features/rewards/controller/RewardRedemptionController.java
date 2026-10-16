package com.thedavelopers.eventqr.features.rewards.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionGrantRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResultResponse;
import com.thedavelopers.eventqr.features.rewards.service.RewardRedemptionService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/rewards")
public class RewardRedemptionController {

    private final RewardRedemptionService rewardRedemptionService;
    private final JwtService jwtService;
    private final EventService eventService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;

    public RewardRedemptionController(RewardRedemptionService rewardRedemptionService,
                                      JwtService jwtService,
                                      EventService eventService,
                                      EventStaffAssignmentRepository eventStaffAssignmentRepository) {
        this.rewardRedemptionService = rewardRedemptionService;
        this.jwtService = jwtService;
        this.eventService = eventService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
    }

    @PostMapping("/redeem-staff")
    public ResponseEntity<ApiResponse<RewardRedemptionResultResponse>> redeem(HttpServletRequest request,
                                                                              @Valid @RequestBody RewardRedemptionGrantRequest body) {
        UUID callerId = requireRedemptionAccess(request, body.eventId());
        // The staff identity is always the authenticated caller; any body staffUserId is ignored.
        RewardRedemptionResultResponse result = rewardRedemptionService.redeem(body.withStaffUserId(callerId));
        return ResponseEntity.ok(ApiResponse.success(
                result.approved() ? "Reward redeemed" : "Reward redemption rejected", result));
    }

    /** Returns the caller id; see {@link RewardRedemptionAccess#requireScanAccess}. */
    private UUID requireRedemptionAccess(HttpServletRequest request, UUID eventId) {
        return RewardRedemptionAccess.requireScanAccess(request, eventId, jwtService, eventService,
                eventStaffAssignmentRepository);
    }
}
