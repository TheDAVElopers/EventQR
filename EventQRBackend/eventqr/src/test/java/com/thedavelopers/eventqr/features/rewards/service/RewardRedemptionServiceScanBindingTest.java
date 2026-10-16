package com.thedavelopers.eventqr.features.rewards.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
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
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;

/**
 * Staff redemption is bound to one fresh, APPROVED, unused redemption-purpose scan of the same attendee at the same
 * event by the same staff member. Binding failures throw before any reward lock or redemption row is written.
 */
class RewardRedemptionServiceScanBindingTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID rewardId = UUID.randomUUID();
    private final UUID scanLogId = UUID.randomUUID();

    private RewardRepository rewards;
    private RewardRedemptionRepository redemptions;
    private AttendeePointBalanceRepository balances;
    private TransactionLogRepository logs;
    private EventRepository events;
    private PointTransactionRepository points;
    private RegistrationLookupPort registrations;
    private RewardRedemptionService service;
    private TransactionLog scanLog;
    private AttendeePointBalance balance;

    @BeforeEach
    void setUp() {
        rewards = mock(RewardRepository.class);
        redemptions = mock(RewardRedemptionRepository.class);
        balances = mock(AttendeePointBalanceRepository.class);
        logs = mock(TransactionLogRepository.class);
        events = mock(EventRepository.class);
        points = mock(PointTransactionRepository.class);
        registrations = mock(RegistrationLookupPort.class);
        service = new RewardRedemptionService(rewards, redemptions, balances, logs,
                mock(DuplicateRewardClaimChecker.class), events, mock(NotificationService.class), points, registrations);

        scanLog = new TransactionLog();
        scanLog.setId(scanLogId);
        scanLog.setEventId(eventId);
        scanLog.setAttendeeUserId(attendeeId);
        scanLog.setStaffUserId(staffId);
        scanLog.setTransactionType(TransactionType.REWARD_REDEMPTION_SCAN);
        scanLog.setTransactionResult(TransactionResult.APPROVED);
        scanLog.setScannedAt(Instant.now());
        when(logs.findByIdForUpdate(scanLogId)).thenReturn(Optional.of(scanLog));

        Reward reward = new Reward();
        reward.setId(rewardId);
        reward.setEventId(eventId);
        reward.setName("Tote");
        reward.setPointsRequired(20);
        reward.setStatus(RewardStatus.ACTIVE);
        reward.setStockQuantity(null);
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.of(reward));
        when(rewards.decrementStockIfAvailable(rewardId)).thenReturn(1);

        Event event = new Event();
        event.setRewardsEnabled(true);
        event.setTitle("Expo");
        when(events.findById(eventId)).thenReturn(Optional.of(event));

        balance = new AttendeePointBalance();
        balance.setEventId(eventId);
        balance.setAttendeeUserId(attendeeId);
        balance.setPointsBalance(50);
        when(balances.findByEventIdAndAttendeeUserIdForUpdate(eventId, attendeeId)).thenReturn(Optional.of(balance));
        when(balances.findByEventIdAndAttendeeUserId(eventId, attendeeId)).thenReturn(Optional.of(balance));

        when(registrations.hasActiveRegistration(eventId, attendeeId)).thenReturn(true);
        when(redemptions.existsByRedemptionScanLogIdAndStatus(scanLogId, RedemptionStatus.REDEEMED)).thenReturn(false);
        when(redemptions.save(any(RewardRedemption.class))).thenAnswer(inv -> {
            RewardRedemption r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    private RewardRedemptionGrantRequest request() {
        return new RewardRedemptionGrantRequest(eventId, attendeeId, rewardId, staffId, scanLogId);
    }

    private void assertNothingWritten() {
        verify(rewards, never()).findByIdForUpdate(any());
        verify(redemptions, never()).save(any(RewardRedemption.class));
        verify(points, never()).save(any(PointTransaction.class));
        verify(logs, never()).save(any(TransactionLog.class));
        assertThat(balance.getPointsBalance()).isEqualTo(50);
    }

    // --- binding: same event / attendee / staff ----------------------------

    @Test
    void scanFromAnotherEventIsForbidden() {
        scanLog.setEventId(UUID.randomUUID());
        assertThatThrownBy(() -> service.redeem(request()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Redemption scan does not match this attendee");
        assertNothingWritten();
    }

    @Test
    void scanOfAnotherAttendeeIsForbidden() {
        scanLog.setAttendeeUserId(UUID.randomUUID());
        assertThatThrownBy(() -> service.redeem(request())).isInstanceOf(ForbiddenException.class);
        assertNothingWritten();
    }

    @Test
    void scanByAnotherStaffMemberIsForbidden() {
        scanLog.setStaffUserId(UUID.randomUUID());
        assertThatThrownBy(() -> service.redeem(request())).isInstanceOf(ForbiddenException.class);
        assertNothingWritten();
    }

    @Test
    void missingCallerStaffIdIsForbidden() {
        scanLog.setStaffUserId(null);
        assertThatThrownBy(() -> service.redeem(
                new RewardRedemptionGrantRequest(eventId, attendeeId, rewardId, null, scanLogId)))
                .isInstanceOf(ForbiddenException.class);
        assertNothingWritten();
    }

    // --- kind / result / freshness / single use -----------------------------

    @Test
    void redemptionOutcomeRowIsNotAScan() {
        scanLog.setTransactionType(TransactionType.REWARD_REDEMPTION);
        scanLog.setRewardId(UUID.randomUUID());
        assertThatThrownBy(() -> service.redeem(request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Not a reward redemption scan");
        assertNothingWritten();
    }

    @Test
    void entryScanIsNotARedemptionScan() {
        scanLog.setTransactionType(TransactionType.ENTRY);
        assertThatThrownBy(() -> service.redeem(request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Not a reward redemption scan");
        assertNothingWritten();
    }

    @Test
    void rejectedScanIsBadRequest() {
        scanLog.setTransactionResult(TransactionResult.REJECTED);
        assertThatThrownBy(() -> service.redeem(request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Redemption scan was rejected. Scan the attendee again.");
        assertNothingWritten();
    }

    @Test
    void scanOlderThanTheTtlHasExpired() {
        scanLog.setScannedAt(Instant.now().minus(Duration.ofMinutes(16)));
        assertThatThrownBy(() -> service.redeem(request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Redemption scan has expired. Scan the attendee again.");
        assertNothingWritten();
    }

    @Test
    void scanWithinTheTtlProceeds() {
        scanLog.setScannedAt(Instant.now().minus(Duration.ofMinutes(14)));
        assertThat(service.redeem(request()).status()).isEqualTo(RedemptionStatus.REDEEMED);
    }

    @Test
    void alreadyRedeemedScanIsConflict() {
        when(redemptions.existsByRedemptionScanLogIdAndStatus(scanLogId, RedemptionStatus.REDEEMED)).thenReturn(true);
        assertThatThrownBy(() -> service.redeem(request()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This redemption scan was already used. Scan the attendee again.");
        assertNothingWritten();
    }

    // --- registration cancelled after the scan -----------------------------

    @Test
    void registrationCancelledAfterTheScanIsRejectedAndSpendsNothing() {
        when(registrations.hasActiveRegistration(eventId, attendeeId)).thenReturn(false);

        RewardRedemptionResultResponse result = service.redeem(request());

        assertThat(result.status()).isEqualTo(RedemptionStatus.REJECTED);
        assertThat(result.reason()).isEqualTo("Registration is not active");
        assertThat(result.remainingBalance()).isEqualTo(50);
        assertThat(balance.getPointsBalance()).isEqualTo(50);
        verify(rewards, never()).decrementStockIfAvailable(any());
        verify(points, never()).save(any(PointTransaction.class));
    }

    // --- happy path and lock order ------------------------------------------

    @Test
    void happyPathDeductsWritesLedgerAndTransactionLog() {
        RewardRedemptionResultResponse result = service.redeem(request());

        assertThat(result.status()).isEqualTo(RedemptionStatus.REDEEMED);
        assertThat(result.pointsSpent()).isEqualTo(20);
        assertThat(result.remainingBalance()).isEqualTo(30);
        assertThat(balance.getPointsBalance()).isEqualTo(30);

        ArgumentCaptor<RewardRedemption> redemption = ArgumentCaptor.forClass(RewardRedemption.class);
        verify(redemptions).save(redemption.capture());
        assertThat(redemption.getValue().getRedemptionScanLogId()).isEqualTo(scanLogId);
        assertThat(redemption.getValue().getStaffUserId()).isEqualTo(staffId);

        ArgumentCaptor<PointTransaction> ledger = ArgumentCaptor.forClass(PointTransaction.class);
        verify(points).save(ledger.capture());
        assertThat(ledger.getValue().getPointsChanged()).isEqualTo(-20);

        ArgumentCaptor<TransactionLog> outcome = ArgumentCaptor.forClass(TransactionLog.class);
        verify(logs).save(outcome.capture());
        assertThat(outcome.getValue().getTransactionType()).isEqualTo(TransactionType.REWARD_REDEMPTION);
        assertThat(outcome.getValue().getRewardId()).isEqualTo(rewardId);
        assertThat(outcome.getValue().getTransactionResult()).isEqualTo(TransactionResult.APPROVED);
    }

    @Test
    void scanLogIsLockedBeforeTheRewardAndTheBalance() {
        service.redeem(request());

        var order = inOrder(logs, rewards, balances);
        order.verify(logs).boundLockWaits();
        order.verify(logs).findByIdForUpdate(scanLogId);
        order.verify(rewards).findByIdForUpdate(rewardId);
        order.verify(balances).findByEventIdAndAttendeeUserIdForUpdate(eventId, attendeeId);
        verify(logs, never()).findById(any());
    }
}
