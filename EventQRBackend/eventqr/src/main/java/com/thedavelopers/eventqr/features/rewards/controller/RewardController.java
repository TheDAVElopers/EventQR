package com.thedavelopers.eventqr.features.rewards.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.PointRuleRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.PointRule;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<PointRuleRequest>> savePointRule(@Valid @RequestBody PointRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Point rule saved", rewardService.savePointRule(request)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RewardResponse>> saveReward(@Valid @RequestBody RewardRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reward saved", rewardService.saveReward(request)));
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<RewardRedemptionResponse>> redeem(@Valid @RequestBody RewardRedemptionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reward redeemed", rewardService.redeem(request)));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<List<RewardResponse>>> findRewards(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRewards(eventId)));
    }

    @GetMapping("/balance/{eventId}/{attendeeUserId}")
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getBalance(@PathVariable UUID eventId,
                                                                        @PathVariable UUID attendeeUserId) {
        return ResponseEntity.ok(ApiResponse.success(rewardService.getBalance(eventId, attendeeUserId)));
    }

    @GetMapping("/redemptions/{eventId}")
    public ResponseEntity<ApiResponse<List<RewardRedemptionResponse>>> findRedemptions(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(rewardService.findRedemptions(eventId)));
    }

    @GetMapping("/rules/{eventId}")
    public ResponseEntity<ApiResponse<List<PointRule>>> findRules(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(rewardService.listPointRules(eventId)));
    }
}