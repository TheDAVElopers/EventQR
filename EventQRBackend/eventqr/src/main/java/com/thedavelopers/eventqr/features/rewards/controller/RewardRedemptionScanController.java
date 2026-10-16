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
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse;
import com.thedavelopers.eventqr.features.rewards.service.RewardRedemptionScanService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/rewards")
public class RewardRedemptionScanController {

    private final RewardRedemptionScanService rewardRedemptionScanService;
    private final JwtService jwtService;
    private final EventService eventService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;

    public RewardRedemptionScanController(RewardRedemptionScanService rewardRedemptionScanService,
                                          JwtService jwtService,
                                          EventService eventService,
                                          EventStaffAssignmentRepository eventStaffAssignmentRepository) {
        this.rewardRedemptionScanService = rewardRedemptionScanService;
        this.jwtService = jwtService;
        this.eventService = eventService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
    }

    @PostMapping("/redemption-scan")
    public ResponseEntity<ApiResponse<RewardRedemptionScanResponse>> scan(HttpServletRequest request,
                                                                          @Valid @RequestBody RewardRedemptionScanRequest body) {
        UUID callerId = requireRedemptionAccess(request, body.eventId());
        // The scan is authorized as and attributed to the authenticated caller; any body staffUserId is ignored.
        return ResponseEntity.ok(ApiResponse.success("Reward redemption scan recorded",
                rewardRedemptionScanService.scan(body.withStaffUserId(callerId))));
    }

    /** Returns the caller id; see {@link RewardRedemptionAccess#requireScanAccess}. */
    private UUID requireRedemptionAccess(HttpServletRequest request, UUID eventId) {
        return RewardRedemptionAccess.requireScanAccess(request, eventId, jwtService, eventService,
                eventStaffAssignmentRepository);
    }
}
