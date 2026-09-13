package com.thedavelopers.eventqr.features.rewards.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;
import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.ScanVerificationResponse;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;

@Service
@Transactional
public class RewardRedemptionScanService {

    private final TransactionService transactionService;
    private final AttendeePointBalanceRepository attendeePointBalanceRepository;
    private final RewardRepository rewardRepository;

    public RewardRedemptionScanService(TransactionService transactionService,
                                       AttendeePointBalanceRepository attendeePointBalanceRepository,
                                       RewardRepository rewardRepository) {
        this.transactionService = transactionService;
        this.attendeePointBalanceRepository = attendeePointBalanceRepository;
        this.rewardRepository = rewardRepository;
    }

    public RewardRedemptionScanResponse scan(RewardRedemptionScanRequest request) {
        TransactionRequest transactionRequest = new TransactionRequest(
                request.eventId(), request.scanPurposeId(), request.qrValue(), request.shortId(),
                request.staffUserId(), null);

        ScanVerificationResponse verification = transactionService.verify(transactionRequest);

        // record() only resolves by raw QR value (not short/attendee ID), so use the QR value
        // that verify() already resolved for a manually-entered attendee ID.
        TransactionRequest recordRequest = new TransactionRequest(
                request.eventId(), request.scanPurposeId(), verification.qrValue(), null,
                request.staffUserId(), null);
        TransactionResponse scanLog = transactionService.record(recordRequest);

        boolean rejected = scanLog.transactionResult() == TransactionResult.REJECTED;
        String rejectionReason = rejected ? scanLog.reason() : null;

        int pointsBalance = balanceFor(request.eventId(), verification.attendeeUserId()).getPointsBalance();

        List<RewardResponse> eligibleRewards = rewardRepository.findByEventId(request.eventId()).stream()
                .filter(reward -> reward.getStatus() == RewardStatus.ACTIVE)
                .filter(reward -> reward.getPointsRequired() <= pointsBalance)
                .filter(reward -> reward.getStockQuantity() == null || reward.getStockQuantity() > 0)
                .map(this::toRewardResponse)
                .toList();

        return new RewardRedemptionScanResponse(
                request.eventId(),
                verification.attendeeUserId(),
                verification.registrationId(),
                verification.qrCredentialId(),
                verification.attendeeName(),
                verification.attendeeEmail(),
                verification.registrationStatus(),
                pointsBalance,
                request.scanPurposeId(),
                scanLog.transactionId(),
                eligibleRewards,
                rejected,
                rejectionReason);
    }

    private AttendeePointBalance balanceFor(UUID eventId, UUID attendeeUserId) {
        return attendeePointBalanceRepository.findByEventIdAndAttendeeUserId(eventId, attendeeUserId)
                .orElseGet(() -> {
                    AttendeePointBalance balance = new AttendeePointBalance();
                    balance.setEventId(eventId);
                    balance.setAttendeeUserId(attendeeUserId);
                    balance.setPointsBalance(0);
                    return balance;
                });
    }

    private RewardResponse toRewardResponse(Reward reward) {
        return new RewardResponse(reward.getId(), reward.getEventId(), reward.getName(), reward.getDescription(), reward.getPointsRequired(),
                reward.getStatus(), reward.getStockQuantity(), reward.isAllowDuplicateClaims());
    }
}
