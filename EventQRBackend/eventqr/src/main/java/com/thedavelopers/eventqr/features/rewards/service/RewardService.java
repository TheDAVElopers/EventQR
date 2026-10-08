package com.thedavelopers.eventqr.features.rewards.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.TransactionRecordedEvent;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
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
    private final AttendeeDirectoryPort attendeeDirectoryPort;
    private final DuplicateRewardClaimChecker duplicateRewardClaimChecker;

    public RewardService(AttendeePointBalanceRepository attendeePointBalanceRepository,
                         PointTransactionRepository pointTransactionRepository,
                         RewardRepository rewardRepository,
                         RewardRedemptionRepository rewardRedemptionRepository,
                         EventRepository eventRepository,
                         ScanPurposeRepository scanPurposeRepository, NotificationService notificationService,
                         AttendeeDirectoryPort attendeeDirectoryPort,
                         DuplicateRewardClaimChecker duplicateRewardClaimChecker) {
        this.attendeeDirectoryPort = attendeeDirectoryPort;
        this.duplicateRewardClaimChecker = duplicateRewardClaimChecker;
        this.notificationService = notificationService;
        this.attendeePointBalanceRepository = attendeePointBalanceRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.rewardRepository = rewardRepository;
        this.rewardRedemptionRepository = rewardRedemptionRepository;
        this.eventRepository = eventRepository;
        this.scanPurposeRepository = scanPurposeRepository;
    }

    @CacheEvict(cacheNames = {"scan-purposes"}, allEntries = true)
    public RewardResponse saveReward(RewardRequest request) {
        Reward reward = new Reward();
        reward.setEventId(request.eventId());
        reward.setName(request.name());
        reward.setDescription(request.description());
        reward.setPointsRequired(request.pointsRequired());
        reward.setStockQuantity(resolveCreateStock(request));
        reward.setAllowDuplicateClaims(request.allowDuplicateClaims());
        reward.setStatus(RewardStatus.ACTIVE);
        Reward saved = rewardRepository.save(reward);
        ensureRewardRedemptionScanPurposeForReward(request.eventId());
        return toResponse(saved, 0L);
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

    @CacheEvict(cacheNames = {"scan-purposes"}, allEntries = true)
    public RewardResponse updateReward(UUID eventId, UUID rewardId, RewardRequest request) {
        // Row lock: serialises with redemptions (both paths lock the reward first) so the
        // total - claimed computation below cannot race a concurrent claim and lose its decrement.
        Reward reward = rewardRepository.findByIdForUpdate(rewardId)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found"));
        if (!reward.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Reward not found for event");
        }
        reward.setName(request.name());
        if (request.description() != null) {
            // null/absent keeps the stored value; "" (or blank) clears it; otherwise replace.
            reward.setDescription(request.description().isBlank() ? null : request.description());
        }
        reward.setPointsRequired(request.pointsRequired());
        long claimed = claimedCount(reward.getId());
        applyUpdateStock(reward, request, claimed);
        reward.setAllowDuplicateClaims(request.allowDuplicateClaims());
        return toResponse(rewardRepository.save(reward), claimed);
    }

    @CacheEvict(cacheNames = {"scan-purposes"}, allEntries = true)
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
        return toResponse(reward, claimedCount(reward.getId()));
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
            notificationService.createPointsAdjustedNotification(eventId, event.getOrganizerUserId(), event.getTitle(), attendeeDisplayName(attendeeUserId), points, reason == null ? "Points assigned" : reason, true);
            if (attendeeUserId != null) {
                notificationService.createPointsAdjustedForAttendeeNotification(eventId, attendeeUserId, event.getTitle(), points, reason, true);
            }
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
            notificationService.createPointsAdjustedNotification(eventId, event.getOrganizerUserId(), event.getTitle(), attendeeDisplayName(attendeeUserId), points, reason == null ? "Points deducted" : reason, false);
            if (attendeeUserId != null) {
                notificationService.createPointsAdjustedForAttendeeNotification(eventId, attendeeUserId, event.getTitle(), points, reason, false);
            }
        }
        return new PointBalanceResponse(balance.getEventId(), balance.getAttendeeUserId(), balance.getPointsBalance());
    }

    private String attendeeDisplayName(UUID attendeeUserId) {
        if (attendeeUserId == null || attendeeDirectoryPort == null) {
            return "";
        }
        return attendeeDirectoryPort.findById(attendeeUserId)
                .map(AttendeeDirectoryPort.AttendeeSnapshot::fullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("an attendee");
    }

    public RewardRedemptionResponse redeem(RewardRedemptionRequest request) {
        Event redeemEvent = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.eventId()));
        if (!redeemEvent.isRewardsEnabled()) {
            throw new ConflictException("Reward redemption is disabled for this event");
        }
        Reward reward = rewardRepository.findByIdForUpdate(request.rewardId())
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
        if (duplicateRewardClaimChecker != null
                && duplicateRewardClaimChecker.checkDuplicate(reward, request.attendeeUserId()) != null) {
            throw new ConflictException("Reward already claimed. Duplicate claims are not allowed for this reward.");
        }

        // Same atomic guarded decrement as RewardRedemptionService (NULL stock = unlimited).
        if (rewardRepository.decrementStockIfAvailable(reward.getId()) == 0) {
            throw new ConflictException("Reward is out of stock");
        }

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

        Event event = redeemEvent;
        if (notificationService != null && request.attendeeUserId() != null) {
            notificationService.createRewardRedeemedNotification(request.eventId(), request.attendeeUserId(), event.getTitle(), reward.getName(), reward.getPointsRequired());
        }

        return new RewardRedemptionResponse(redemption.getId(), redemption.getEventId(), redemption.getAttendeeUserId(),
                redemption.getRewardId(), redemption.getPointsSpent(), redemption.getStatus(), redemption.getRedeemedAt(),
                redemption.getReason());
    }

    public List<RewardResponse> findRewards(UUID eventId) {
        return toResponses(eventId, rewardRepository.findByEventId(eventId));
    }

    /** Attendee-facing / unguarded read: no claimedCount or totalQuantity. */
    public List<RewardResponse> findRewardsForAttendee(UUID eventId) {
        return rewardRepository.findByEventId(eventId).stream().map(RewardResponse::withoutCounts).toList();
    }

    /** Rewards an attendee can still claim: ACTIVE and unlimited (null stock) or in stock (no counts). */
    public List<RewardResponse> findClaimableRewards(UUID eventId) {
        return rewardRepository.findByEventId(eventId).stream()
                .filter(r -> r.getStatus() == RewardStatus.ACTIVE
                        && (r.getStockQuantity() == null || r.getStockQuantity() > 0))
                .map(RewardResponse::withoutCounts).toList();
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

    private Integer resolveCreateStock(RewardRequest request) {
        if (Boolean.TRUE.equals(request.unlimitedStock())) {
            return null;
        }
        if (request.totalQuantity() != null) {
            return request.totalQuantity();
        }
        return request.stockQuantity();
    }

    private void applyUpdateStock(Reward reward, RewardRequest request, long claimed) {
        if (Boolean.TRUE.equals(request.unlimitedStock())) {
            reward.setStockQuantity(null);
        } else if (request.totalQuantity() != null) {
            long remaining = (long) request.totalQuantity() - claimed;
            if (remaining < 0) {
                throw new BadRequestException(
                        "Total quantity cannot be less than the " + claimed + " already claimed");
            }
            reward.setStockQuantity((int) remaining);
        } else if (request.stockQuantity() != null) {
            reward.setStockQuantity(request.stockQuantity());
        }
    }

    private long claimedCount(UUID rewardId) {
        return rewardRedemptionRepository.countByRewardIdAndStatus(rewardId, RedemptionStatus.REDEEMED);
    }

    /** Maps rewards with a single batched claimed-count query for the whole event (no N+1). */
    private List<RewardResponse> toResponses(UUID eventId, List<Reward> rewards) {
        if (rewards.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> claimed = new HashMap<>();
        for (var row : rewardRedemptionRepository.countByEventIdAndStatusGroupedByReward(eventId, RedemptionStatus.REDEEMED)) {
            claimed.put(row.getRewardId(), row.getTotal() == null ? 0L : row.getTotal());
        }
        return rewards.stream().map(r -> RewardResponse.of(r, claimed.getOrDefault(r.getId(), 0L))).toList();
    }

    private RewardResponse toResponse(Reward reward, long claimed) {
        return RewardResponse.of(reward, claimed);
    }
}
