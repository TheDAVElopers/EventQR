package com.thedavelopers.eventqr.features.rewards.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionGrantRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResultResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;
import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.features.rewards.model.entity.RewardRedemption;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;

@Service
@Transactional
public class RewardRedemptionService {

    private static final Logger log = LoggerFactory.getLogger(RewardRedemptionService.class);

private final NotificationService notificationService;

    private final RewardRepository rewardRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final AttendeePointBalanceRepository attendeePointBalanceRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final DuplicateRewardClaimChecker duplicateRewardClaimChecker;
    private final EventRepository eventRepository;

    public RewardRedemptionService(RewardRepository rewardRepository,
                                   RewardRedemptionRepository rewardRedemptionRepository,
                                   AttendeePointBalanceRepository attendeePointBalanceRepository,
                                   TransactionLogRepository transactionLogRepository,
                                   DuplicateRewardClaimChecker duplicateRewardClaimChecker,
                                   EventRepository eventRepository,
                                   NotificationService notificationService) {
        this.notificationService = notificationService;
        this.rewardRepository = rewardRepository;
        this.rewardRedemptionRepository = rewardRedemptionRepository;
        this.attendeePointBalanceRepository = attendeePointBalanceRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.duplicateRewardClaimChecker = duplicateRewardClaimChecker;
        this.eventRepository = eventRepository;
    }

    public RewardRedemptionResultResponse redeem(RewardRedemptionGrantRequest request) {
        TransactionLog scanLog = transactionLogRepository.findById(request.redemptionScanLogId())
                .orElseThrow(() -> new ResourceNotFoundException("Redemption scan log not found"));

        Reward reward = rewardRepository.findById(request.rewardId())
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found"));
        if (!reward.getEventId().equals(request.eventId())) {
            return reject(request, reward, scanLog, "Reward does not belong to the event");
        }
        if (reward.getStatus() != RewardStatus.ACTIVE) {
            return reject(request, reward, scanLog, "Reward is no longer active");
        }
        if (reward.getStockQuantity() != null && reward.getStockQuantity() <= 0) {
            return reject(request, reward, scanLog, "Reward is out of stock");
        }

        AttendeePointBalance balance = balanceFor(request.eventId(), request.attendeeUserId());
        if (balance.getPointsBalance() < reward.getPointsRequired()) {
            return reject(request, reward, scanLog, "Not enough points to redeem this reward");
        }

        String duplicateReason = duplicateRewardClaimChecker.checkDuplicate(reward, request.attendeeUserId());
        if (duplicateReason != null) {
            return reject(request, reward, scanLog, duplicateReason);
        }

        balance.setPointsBalance(balance.getPointsBalance() - reward.getPointsRequired());
        attendeePointBalanceRepository.save(balance);

        if (reward.getStockQuantity() != null) {
            reward.setStockQuantity(reward.getStockQuantity() - 1);
            rewardRepository.save(reward);
            if (reward.getStockQuantity() == 0 && notificationService != null) {
                Event event = eventRepository.findById(request.eventId()).orElse(null);
                if (event != null) {
                    notificationService.createRewardExhaustedNotification(request.eventId(), event.getOrganizerUserId(), event.getTitle(), reward.getName());
                    notificationService.createRewardExhaustedAttendeeNotifications(List.of(event));
                }
            }
        }

        RewardRedemption redemption = new RewardRedemption();
        redemption.setEventId(request.eventId());
        redemption.setAttendeeUserId(request.attendeeUserId());
        redemption.setRewardId(reward.getId());
        redemption.setPointsSpent(reward.getPointsRequired());
        redemption.setStatus(RedemptionStatus.REDEEMED);
        redemption.setRedeemedAt(Instant.now());
        redemption.setRedemptionScanLogId(request.redemptionScanLogId());
        redemption.setStaffUserId(request.staffUserId());
        RewardRedemption saved = rewardRedemptionRepository.save(redemption);

        writeTransactionLog(scanLog, reward, request.staffUserId(), TransactionResult.APPROVED,
                reward.getPointsRequired(), null);

        log.info("Reward redeemed attendeeUserId={} rewardId={} points={} staffUserId={}",
                request.attendeeUserId(), reward.getId(), reward.getPointsRequired(), request.staffUserId());

        Event event = eventRepository.findById(request.eventId()).orElse(null);
        if (event != null && notificationService != null && request.attendeeUserId() != null) {
            notificationService.createRewardRedeemedNotification(request.eventId(), request.attendeeUserId(), event.getTitle(), reward.getName(), reward.getPointsRequired());
        }

        return new RewardRedemptionResultResponse(saved.getId(), reward.getId(), reward.getName(),
                RedemptionStatus.REDEEMED, null, reward.getPointsRequired(), saved.getRedeemedAt(),
                balance.getPointsBalance());
    }

    private RewardRedemptionResultResponse reject(RewardRedemptionGrantRequest request, Reward reward,
                                                  TransactionLog scanLog, String reason) {
        RewardRedemption redemption = new RewardRedemption();
        redemption.setEventId(request.eventId());
        redemption.setAttendeeUserId(request.attendeeUserId());
        redemption.setRewardId(reward.getId());
        redemption.setPointsSpent(reward.getPointsRequired());
        redemption.setStatus(RedemptionStatus.REJECTED);
        redemption.setReason(reason);
        redemption.setRedemptionScanLogId(request.redemptionScanLogId());
        redemption.setStaffUserId(request.staffUserId());
        RewardRedemption saved = rewardRedemptionRepository.save(redemption);

        writeTransactionLog(scanLog, reward, request.staffUserId(), TransactionResult.REJECTED,
                0, reason);

        int balance = balanceFor(request.eventId(), request.attendeeUserId()).getPointsBalance();
        log.info("Reward redemption rejected attendeeUserId={} rewardId={} reason={}",
                request.attendeeUserId(), reward.getId(), reason);

        return new RewardRedemptionResultResponse(saved.getId(), reward.getId(), reward.getName(),
                RedemptionStatus.REJECTED, reason, 0, null, balance);
    }

    private void writeTransactionLog(TransactionLog scanLog, Reward reward, UUID staffUserId,
                                     TransactionResult result, int pointsDelta, String reason) {
        TransactionLog transactionLog = new TransactionLog();
        transactionLog.setEventId(scanLog.getEventId());
        transactionLog.setAttendeeUserId(scanLog.getAttendeeUserId());
        transactionLog.setRegistrationId(scanLog.getRegistrationId());
        transactionLog.setQrCredentialId(scanLog.getQrCredentialId());
        transactionLog.setScanPurposeId(scanLog.getScanPurposeId());
        transactionLog.setStaffUserId(staffUserId);
        transactionLog.setRewardId(reward.getId());
        transactionLog.setTransactionType(TransactionType.REWARD_REDEMPTION);
        transactionLog.setTransactionResult(result);
        transactionLog.setPointsDelta(pointsDelta > 0 ? -pointsDelta : pointsDelta);
        transactionLog.setReason(reason);
        transactionLogRepository.save(transactionLog);
    }

    private AttendeePointBalance balanceFor(UUID eventId, UUID attendeeUserId) {
        return attendeePointBalanceRepository.findByEventIdAndAttendeeUserId(eventId, attendeeUserId)
                .orElseGet(() -> {
                    AttendeePointBalance balance = new AttendeePointBalance();
                    balance.setEventId(eventId);
                    balance.setAttendeeUserId(attendeeUserId);
                    balance.setPointsBalance(0);
                    return attendeePointBalanceRepository.save(balance);
                });
    }
}
