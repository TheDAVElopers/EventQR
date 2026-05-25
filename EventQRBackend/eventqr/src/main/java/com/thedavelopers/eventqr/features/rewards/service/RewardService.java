package com.thedavelopers.eventqr.features.rewards.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.PointRuleRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;
import com.thedavelopers.eventqr.features.rewards.model.entity.EventBenefit;
import com.thedavelopers.eventqr.features.rewards.model.entity.EventActivity;
import com.thedavelopers.eventqr.features.rewards.model.entity.PointRule;
import com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction;
import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.features.rewards.model.entity.RewardRedemption;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointRuleRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.event.TransactionRecordedEvent;
import com.thedavelopers.eventqr.shared.exception.ConflictException;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;

@Service
@Transactional
public class RewardService {

    private final PointRuleRepository pointRuleRepository;
    private final AttendeePointBalanceRepository attendeePointBalanceRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final RewardRepository rewardRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;

    public RewardService(PointRuleRepository pointRuleRepository,
                         AttendeePointBalanceRepository attendeePointBalanceRepository,
                         PointTransactionRepository pointTransactionRepository,
                         RewardRepository rewardRepository,
                         RewardRedemptionRepository rewardRedemptionRepository) {
        this.pointRuleRepository = pointRuleRepository;
        this.attendeePointBalanceRepository = attendeePointBalanceRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.rewardRepository = rewardRepository;
        this.rewardRedemptionRepository = rewardRedemptionRepository;
    }

    public PointRuleRequest savePointRule(PointRuleRequest request) {
        PointRule rule = pointRuleRepository.findByEventIdAndScanPurposeId(request.eventId(), request.scanPurposeId())
                .orElseGet(PointRule::new);
        rule.setEventId(request.eventId());
        rule.setScanPurposeId(request.scanPurposeId());
        rule.setPoints(request.points());
        rule.setActive(request.active());
        pointRuleRepository.save(rule);
        return request;
    }

    public RewardResponse saveReward(RewardRequest request) {
        Reward reward = new Reward();
        reward.setEventId(request.eventId());
        reward.setName(request.name());
        reward.setPointsRequired(request.pointsRequired());
        reward.setStockQuantity(request.stockQuantity());
        reward.setStatus(RewardStatus.ACTIVE);
        return toResponse(rewardRepository.save(reward));
    }

    public PointBalanceResponse getBalance(UUID eventId, UUID attendeeUserId) {
        AttendeePointBalance balance = balanceFor(eventId, attendeeUserId);
        return new PointBalanceResponse(balance.getEventId(), balance.getAttendeeUserId(), balance.getPointsBalance());
    }

    public RewardRedemptionResponse redeem(RewardRedemptionRequest request) {
        Reward reward = rewardRepository.findById(request.rewardId())
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found"));
        if (reward.getStatus() != RewardStatus.ACTIVE) {
            throw new ConflictException("Reward is inactive");
        }
        if (!reward.getEventId().equals(request.eventId())) {
            throw new ConflictException("Reward does not belong to the event");
        }
        AttendeePointBalance balance = balanceFor(request.eventId(), request.attendeeUserId());
        if (balance.getPointsBalance() < reward.getPointsRequired()) {
            throw new ConflictException("Not enough points to redeem reward");
        }
        rewardRedemptionRepository.findByEventIdAndAttendeeUserIdAndRewardId(request.eventId(), request.attendeeUserId(), request.rewardId())
                .ifPresent(existing -> {
                    if (existing.getStatus() == RedemptionStatus.REDEEMED) {
                        throw new ConflictException("Reward already redeemed by this attendee");
                    }
                });

        balance.setPointsBalance(balance.getPointsBalance() - reward.getPointsRequired());
        attendeePointBalanceRepository.save(balance);

        RewardRedemption redemption = new RewardRedemption();
        redemption.setEventId(request.eventId());
        redemption.setAttendeeUserId(request.attendeeUserId());
        redemption.setRewardId(request.rewardId());
        redemption.setPointsSpent(reward.getPointsRequired());
        redemption.setStatus(RedemptionStatus.REDEEMED);
        redemption.setRedeemedAt(Instant.now());
        redemption = rewardRedemptionRepository.save(redemption);

        PointTransaction transaction = new PointTransaction();
        transaction.setEventId(request.eventId());
        transaction.setAttendeeUserId(request.attendeeUserId());
        transaction.setSourceTransactionId(redemption.getId());
        transaction.setPointsChanged(-reward.getPointsRequired());
        transaction.setOccurredAt(Instant.now());
        transaction.setReason("Reward redemption");
        pointTransactionRepository.save(transaction);

        return new RewardRedemptionResponse(redemption.getId(), redemption.getEventId(), redemption.getAttendeeUserId(),
                redemption.getRewardId(), redemption.getPointsSpent(), redemption.getStatus(), redemption.getRedeemedAt(),
                redemption.getReason());
    }

    public List<RewardResponse> findRewards(UUID eventId) {
        return rewardRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    public List<RewardRedemptionResponse> findRedemptions(UUID eventId) {
        return rewardRedemptionRepository.findByEventId(eventId).stream().map(redemption -> new RewardRedemptionResponse(
                redemption.getId(), redemption.getEventId(), redemption.getAttendeeUserId(), redemption.getRewardId(),
                redemption.getPointsSpent(), redemption.getStatus(), redemption.getRedeemedAt(), redemption.getReason())).toList();
    }

    @EventListener
    public void onTransactionRecorded(TransactionRecordedEvent event) {
        if (event.transactionResult() != TransactionResult.APPROVED) {
            return;
        }
        if (event.pointsDelta() <= 0) {
            PointRule pointRule = pointRuleRepository.findByEventIdAndScanPurposeId(event.eventId(), event.scanPurposeId())
                    .orElse(null);
            if (pointRule == null || !pointRule.isActive()) {
                return;
            }
            event = new TransactionRecordedEvent(event.transactionId(), event.eventId(), event.attendeeUserId(),
                    event.registrationId(), event.qrCredentialId(), event.scanPurposeId(), event.transactionType(),
                    event.transactionResult(), pointRule.getPoints(), event.staffUserId(), event.reason());
        }
        if (event.transactionType() == TransactionType.REWARD_REDEMPTION_SCAN || event.transactionType() == TransactionType.REWARD_REDEMPTION) {
            return;
        }
        AttendeePointBalance balance = balanceFor(event.eventId(), event.attendeeUserId());
        balance.setPointsBalance(balance.getPointsBalance() + event.pointsDelta());
        attendeePointBalanceRepository.save(balance);

        PointTransaction transaction = new PointTransaction();
        transaction.setEventId(event.eventId());
        transaction.setAttendeeUserId(event.attendeeUserId());
        transaction.setSourceTransactionId(event.transactionId());
        transaction.setPointsChanged(event.pointsDelta());
        transaction.setOccurredAt(Instant.now());
        transaction.setReason(event.reason() == null ? "Scan reward points" : event.reason());
        pointTransactionRepository.save(transaction);
    }

    public List<PointRule> listPointRules(UUID eventId) {
        return pointRuleRepository.findByEventId(eventId);
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

    private RewardResponse toResponse(Reward reward) {
        return new RewardResponse(reward.getId(), reward.getEventId(), reward.getName(), reward.getPointsRequired(),
                reward.getStatus(), reward.getStockQuantity());
    }
}