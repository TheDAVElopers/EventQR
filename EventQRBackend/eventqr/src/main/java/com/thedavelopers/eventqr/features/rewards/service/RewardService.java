package com.thedavelopers.eventqr.features.rewards.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;
import com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction;
import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.features.rewards.model.entity.RewardRedemption;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.scanning.model.entity.ScanPurpose;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.interfaces.TransactionRecordedEvent;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

@Service
@Transactional
public class RewardService {

    private final AttendeePointBalanceRepository attendeePointBalanceRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final RewardRepository rewardRepository;
    private final NotificationService notificationService;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final EventRepository eventRepository;
    private final ScanPurposeRepository scanPurposeRepository;

    public RewardService(AttendeePointBalanceRepository attendeePointBalanceRepository,
                         PointTransactionRepository pointTransactionRepository,
                         RewardRepository rewardRepository,
                         RewardRedemptionRepository rewardRedemptionRepository,
                         EventRepository eventRepository,
                         ScanPurposeRepository scanPurposeRepository, NotificationService notificationService) {
        this.notificationService = notificationService;
        this.attendeePointBalanceRepository = attendeePointBalanceRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.rewardRepository = rewardRepository;
        this.rewardRedemptionRepository = rewardRedemptionRepository;
        this.eventRepository = eventRepository;
        this.scanPurposeRepository = scanPurposeRepository;
    }

    @CacheEvict(cacheNames = {"scan-purposes", "transaction-rules"}, key = "#request.eventId()")
    public RewardResponse saveReward(RewardRequest request) {
        Reward reward = new Reward();
        reward.setEventId(request.eventId());
        reward.setName(request.name());
        reward.setDescription(request.description());
        reward.setPointsRequired(request.pointsRequired());
        reward.setStockQuantity(request.stockQuantity());
        reward.setAllowDuplicateClaims(request.allowDuplicateClaims());
        reward.setStatus(RewardStatus.ACTIVE);
        Reward saved = rewardRepository.save(reward);
        ensureRewardRedemptionScanPurposeForReward(request.eventId());
        return toResponse(saved);
    }

    private void ensureRewardRedemptionScanPurposeForReward(UUID eventId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null || !event.isRewardsEnabled()) {
            return;
        }
        if (scanPurposeRepository.findByEventIdAndCode(eventId, ScanPurposeCode.REWARD_REDEMPTION_SCAN).isPresent()) {
            return;
        }
        ScanPurpose scanPurpose = new ScanPurpose();
        scanPurpose.setEventId(eventId);
        scanPurpose.setName("Reward Redemption");
        scanPurpose.setCode(ScanPurposeCode.REWARD_REDEMPTION_SCAN);
        scanPurpose.setActive(true);
        scanPurpose.setTrackingOnly(false);
        scanPurpose.setDescription("Staff-scan flow for redeeming attendee rewards");
        scanPurposeRepository.save(scanPurpose);
    }

    @CacheEvict(cacheNames = {"scan-purposes", "transaction-rules"}, key = "#eventId")
    public RewardResponse updateReward(UUID eventId, UUID rewardId, RewardRequest request) {
        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found"));
        if (!reward.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Reward not found for event");
        }
        reward.setName(request.name());
        reward.setDescription(request.description());
        reward.setPointsRequired(request.pointsRequired());
        reward.setStockQuantity(request.stockQuantity());
        reward.setAllowDuplicateClaims(request.allowDuplicateClaims());
        return toResponse(rewardRepository.save(reward));
    }

    @CacheEvict(cacheNames = {"scan-purposes", "transaction-rules"}, key = "#eventId")
    public void deleteReward(UUID eventId, UUID rewardId) {
        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found"));
        if (!reward.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Reward not found for event");
        }
        rewardRepository.delete(reward);
    }

    public RewardResponse findReward(UUID eventId, UUID rewardId) {
        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found"));
        if (!reward.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Reward not found for event");
        }
        return toResponse(reward);
    }

    public PointBalanceResponse getBalance(UUID eventId, UUID attendeeUserId) {
        AttendeePointBalance balance = balanceFor(eventId, attendeeUserId);
        return new PointBalanceResponse(balance.getEventId(), balance.getAttendeeUserId(), balance.getPointsBalance());
    }

    public PointBalanceResponse assignPoints(UUID eventId, UUID attendeeUserId, int points, String reason) {
        if (points < 0) {
            throw new ConflictException("Points must be non-negative");
        }
        AttendeePointBalance balance = balanceFor(eventId, attendeeUserId);
        balance.setPointsBalance(balance.getPointsBalance() + points);
        attendeePointBalanceRepository.save(balance);

        PointTransaction transaction = new PointTransaction();
        transaction.setEventId(eventId);
        transaction.setAttendeeUserId(attendeeUserId);
        transaction.setSourceTransactionId(UUID.randomUUID());
        transaction.setPointsChanged(points);
        transaction.setOccurredAt(Instant.now());
        transaction.setReason(reason == null || reason.isBlank() ? "Manual point assignment" : reason);
        pointTransactionRepository.save(transaction);
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event != null && notificationService != null) {
            notificationService.createPointsAdjustedNotification(eventId, event.getOrganizerUserId(), event.getTitle(), attendeeUserId != null ? attendeeUserId.toString() : "", points, reason == null ? "Points assigned" : reason, true);
        }
        return new PointBalanceResponse(balance.getEventId(), balance.getAttendeeUserId(), balance.getPointsBalance());
    }

    public PointBalanceResponse deductPoints(UUID eventId, UUID attendeeUserId, int points, String reason) {
        if (points < 0) {
            throw new ConflictException("Points must be non-negative");
        }
        AttendeePointBalance balance = balanceFor(eventId, attendeeUserId);
        if (balance.getPointsBalance() < points) {
            throw new ConflictException("Not enough points to deduct");
        }
        balance.setPointsBalance(balance.getPointsBalance() - points);
        attendeePointBalanceRepository.save(balance);

        PointTransaction transaction = new PointTransaction();
        transaction.setEventId(eventId);
        transaction.setAttendeeUserId(attendeeUserId);
        transaction.setSourceTransactionId(UUID.randomUUID());
        transaction.setPointsChanged(-points);
        transaction.setOccurredAt(Instant.now());
        transaction.setReason(reason == null || reason.isBlank() ? "Manual point deduction" : reason);
        pointTransactionRepository.save(transaction);
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event != null && notificationService != null) {
            notificationService.createPointsAdjustedNotification(eventId, event.getOrganizerUserId(), event.getTitle(), attendeeUserId != null ? attendeeUserId.toString() : "", points, reason == null ? "Points deducted" : reason, false);
        }
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

    public List<RewardRedemptionResponse> findRedemptions(UUID eventId, UUID attendeeUserId) {
        return rewardRedemptionRepository.findByEventIdAndAttendeeUserId(eventId, attendeeUserId).stream().map(redemption -> new RewardRedemptionResponse(
                redemption.getId(), redemption.getEventId(), redemption.getAttendeeUserId(), redemption.getRewardId(),
                redemption.getPointsSpent(), redemption.getStatus(), redemption.getRedeemedAt(), redemption.getReason())).toList();
    }

    public List<PointTransaction> findPointTransactions(UUID eventId) {
        return pointTransactionRepository.findByEventId(eventId);
    }

    public List<PointTransaction> findPointTransactions(UUID eventId, UUID attendeeUserId) {
        return pointTransactionRepository.findByEventIdAndAttendeeUserId(eventId, attendeeUserId);
    }

    @Async("eventTaskExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTransactionRecorded(TransactionRecordedEvent event) {
        if (event.transactionResult() != TransactionResult.APPROVED) {
            return;
        }
        if (event.pointsDelta() <= 0) {
            return;
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
        return new RewardResponse(reward.getId(), reward.getEventId(), reward.getName(), reward.getDescription(), reward.getPointsRequired(),
                reward.getStatus(), reward.getStockQuantity(), reward.isAllowDuplicateClaims());
    }
}
