package com.thedavelopers.eventqr.features.rewards.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
import com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction;
import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.features.rewards.model.entity.RewardRedemption;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;

@Service
@Transactional
public class RewardRedemptionService {

    private static final Logger log = LoggerFactory.getLogger(RewardRedemptionService.class);

    /** How long an APPROVED redemption scan can be spent after it was recorded. */
    public static final Duration REDEMPTION_SCAN_TTL = Duration.ofMinutes(15);

private final NotificationService notificationService;

    private final RewardRepository rewardRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final AttendeePointBalanceRepository attendeePointBalanceRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final DuplicateRewardClaimChecker duplicateRewardClaimChecker;
    private final EventRepository eventRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final RegistrationLookupPort registrationLookupPort;

    public RewardRedemptionService(RewardRepository rewardRepository,
                                   RewardRedemptionRepository rewardRedemptionRepository,
                                   AttendeePointBalanceRepository attendeePointBalanceRepository,
                                   TransactionLogRepository transactionLogRepository,
                                   DuplicateRewardClaimChecker duplicateRewardClaimChecker,
                                   EventRepository eventRepository,
                                   NotificationService notificationService,
                                   PointTransactionRepository pointTransactionRepository,
                                   RegistrationLookupPort registrationLookupPort) {
        this.registrationLookupPort = registrationLookupPort;
        this.pointTransactionRepository = pointTransactionRepository;
        this.notificationService = notificationService;
        this.rewardRepository = rewardRepository;
        this.rewardRedemptionRepository = rewardRedemptionRepository;
        this.attendeePointBalanceRepository = attendeePointBalanceRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.duplicateRewardClaimChecker = duplicateRewardClaimChecker;
        this.eventRepository = eventRepository;
    }

    /**
     * Redeems against a prior redemption scan. {@code request.staffUserId()} must already be the authenticated
     * caller (the controller overwrites it). The scan log is row-locked and must be a fresh, APPROVED, unused
     * redemption-purpose scan of the same attendee at the same event by the same staff member. Those checks throw
     * (no REJECTED row is written) because they are integrity/authorization failures, not business rejections.
     * Lock order: scan log, then reward, then balance.
     */
    public RewardRedemptionResultResponse redeem(RewardRedemptionGrantRequest request) {
        transactionLogRepository.boundLockWaits(); // bounded wait on the scan-log row lock below
        TransactionLog scanLog = transactionLogRepository.findByIdForUpdate(request.redemptionScanLogId())
                .orElseThrow(() -> new ResourceNotFoundException("Redemption scan log not found"));
        requireUsableRedemptionScan(scanLog, request);

        rewardRepository.boundLockWaits(); // bounded wait on the reward/balance row locks below
        Reward reward = rewardRepository.findByIdForUpdate(request.rewardId())
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found"));
        if (!reward.getEventId().equals(request.eventId())) {
            return reject(request, reward, scanLog, "Reward does not belong to the event");
        }
        Event rewardEvent = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (!rewardEvent.isRewardsEnabled()) {
            return reject(request, reward, scanLog, "Reward redemption is disabled for this event");
        }
        // The registration can be cancelled between the scan and the redemption (inside the TTL window).
        if (!registrationLookupPort.hasActiveRegistration(request.eventId(), request.attendeeUserId())) {
            return reject(request, reward, scanLog, "Registration is not active");
        }
        if (reward.getStatus() != RewardStatus.ACTIVE) {
            return reject(request, reward, scanLog, "Reward is no longer active");
        }
        if (reward.getStockQuantity() != null && reward.getStockQuantity() <= 0) {
            return reject(request, reward, scanLog, "Reward is out of stock");
        }

        // Lock the balance row (after the reward row, the same order RewardService.redeem uses) so two
        // redemptions of different rewards cannot both spend from the same balance snapshot. The row is created
        // first because FOR UPDATE cannot lock a row that does not exist yet.
        attendeePointBalanceRepository.insertZeroIfAbsent(request.eventId(), request.attendeeUserId());
        AttendeePointBalance balance = attendeePointBalanceRepository
                .findByEventIdAndAttendeeUserIdForUpdate(request.eventId(), request.attendeeUserId())
                .orElseThrow(() -> new IllegalStateException("Point balance row missing after insert"));
        if (balance.getPointsBalance() < reward.getPointsRequired()) {
            return reject(request, reward, scanLog, "Not enough points to redeem this reward");
        }

        String duplicateReason = duplicateRewardClaimChecker.checkDuplicate(reward, request.attendeeUserId());
        if (duplicateReason != null) {
            return reject(request, reward, scanLog, duplicateReason);
        }

        // Single atomic guarded decrement (reward row is already locked above); NULL stock = unlimited.
        // Done before touching the balance so an out-of-stock reject never costs points.
        Integer stockBefore = reward.getStockQuantity();
        if (rewardRepository.decrementStockIfAvailable(reward.getId()) == 0) {
            return reject(request, reward, scanLog, "Reward is out of stock");
        }

        balance.setPointsBalance(balance.getPointsBalance() - reward.getPointsRequired());
        attendeePointBalanceRepository.save(balance);

        if (stockBefore != null && stockBefore == 1 && notificationService != null) {
            Event event = eventRepository.findById(request.eventId()).orElse(null);
            if (event != null) {
                notificationService.createRewardExhaustedNotification(request.eventId(), event.getOrganizerUserId(), event.getTitle(), reward.getName());
                notificationService.createRewardExhaustedAttendeeNotifications(List.of(event));
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

        // Ledger entry for the deduction, mirroring RewardService.redeem. (writeTransactionLog only writes the
        // scan audit row in transaction_logs; it never touches point_transactions, so there is no double write.)
        PointTransaction pointTransaction = new PointTransaction();
        pointTransaction.setEventId(request.eventId());
        pointTransaction.setAttendeeUserId(request.attendeeUserId());
        pointTransaction.setSourceTransactionId(saved.getId());
        pointTransaction.setPointsChanged(-reward.getPointsRequired());
        pointTransaction.setOccurredAt(Instant.now());
        pointTransaction.setReason("Reward redemption");
        pointTransactionRepository.save(pointTransaction);

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

    private void requireUsableRedemptionScan(TransactionLog scanLog, RewardRedemptionGrantRequest request) {
        if (!Objects.equals(scanLog.getEventId(), request.eventId())
                || !Objects.equals(scanLog.getAttendeeUserId(), request.attendeeUserId())
                || request.staffUserId() == null
                || !Objects.equals(scanLog.getStaffUserId(), request.staffUserId())) {
            throw new ForbiddenException("Redemption scan does not match this attendee");
        }
        // rewardId != null marks the outcome rows writeTransactionLog writes (type REWARD_REDEMPTION); those are
        // never replayable as scans.
        if (scanLog.getRewardId() != null
                || (scanLog.getTransactionType() != TransactionType.REWARD_REDEMPTION_SCAN
                    && scanLog.getTransactionType() != TransactionType.REWARD_REDEMPTION)) {
            throw new BadRequestException("Not a reward redemption scan");
        }
        if (scanLog.getTransactionResult() != TransactionResult.APPROVED) {
            throw new BadRequestException("Redemption scan was rejected. Scan the attendee again.");
        }
        if (scanLog.getScannedAt() == null
                || scanLog.getScannedAt().isBefore(Instant.now().minus(REDEMPTION_SCAN_TTL))) {
            throw new BadRequestException("Redemption scan has expired. Scan the attendee again.");
        }
        // Single use counts only REDEEMED rows: a business rejection (not enough points, out of stock) does not
        // consume the scan. The partial unique index from V41 is the DB backstop.
        if (rewardRedemptionRepository.existsByRedemptionScanLogIdAndStatus(scanLog.getId(), RedemptionStatus.REDEEMED)) {
            throw new ConflictException(GlobalExceptionHandler.REDEMPTION_SCAN_ALREADY_USED_MESSAGE);
        }
    }

    private RewardRedemptionResultResponse reject(RewardRedemptionGrantRequest request, Reward reward,
                                                  TransactionLog scanLog, String reason) {
        RewardRedemption redemption = new RewardRedemption();
        redemption.setEventId(request.eventId());
        redemption.setAttendeeUserId(request.attendeeUserId());
        redemption.setRewardId(reward.getId());
        redemption.setPointsSpent(0); // a rejected redemption spends nothing
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
