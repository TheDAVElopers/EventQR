package com.thedavelopers.eventqr.features.rewards.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.ScanVerificationResponse;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort;

@Service
@Transactional
public class RewardRedemptionScanService {

    private final TransactionService transactionService;
    private final AttendeePointBalanceRepository attendeePointBalanceRepository;
    private final RewardRepository rewardRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final ScanPurposePort scanPurposePort;

    public RewardRedemptionScanService(TransactionService transactionService,
                                       AttendeePointBalanceRepository attendeePointBalanceRepository,
                                       RewardRepository rewardRepository,
                                       RewardRedemptionRepository rewardRedemptionRepository,
                                       ScanPurposePort scanPurposePort) {
        this.scanPurposePort = scanPurposePort;
        this.transactionService = transactionService;
        this.attendeePointBalanceRepository = attendeePointBalanceRepository;
        this.rewardRepository = rewardRepository;
        this.rewardRedemptionRepository = rewardRedemptionRepository;
    }

    public RewardRedemptionScanResponse scan(RewardRedemptionScanRequest request) {
        // Only redemption purposes may be recorded here: any other purpose (ENTRY, ATTENDANCE, ...) would award
        // points or change registration state while bypassing its own route guard. Checked before verify() so a
        // wrong purpose fails fast; verify() still checks that the purpose belongs to the event and is active.
        ScanPurposeCode purposeCode = scanPurposePort.requireActive(request.scanPurposeId()).code();
        if (purposeCode != ScanPurposeCode.REWARD_REDEMPTION_SCAN && purposeCode != ScanPurposeCode.REWARD_REDEMPTION) {
            throw new BadRequestException("Scan purpose " + purposeCode
                    + " cannot be recorded on the reward redemption scan route");
        }
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

        Map<UUID, Long> claimed = new HashMap<>();
        for (var row : rewardRedemptionRepository.countByEventIdAndStatusGroupedByReward(request.eventId(), RedemptionStatus.REDEEMED)) {
            claimed.put(row.getRewardId(), row.getTotal() == null ? 0L : row.getTotal());
        }
        List<RewardResponse> eligibleRewards = rewardRepository.findByEventId(request.eventId()).stream()
                .filter(reward -> reward.getStatus() == RewardStatus.ACTIVE)
                .filter(reward -> reward.getPointsRequired() <= pointsBalance)
                .filter(reward -> reward.getStockQuantity() == null || reward.getStockQuantity() > 0)
                .map(reward -> RewardResponse.of(reward, claimed.getOrDefault(reward.getId(), 0L)))
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
}
