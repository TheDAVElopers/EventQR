package com.thedavelopers.eventqr.features.rewards.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository.ClaimedCountRow;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest;
import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;

class RewardServiceStockTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID rewardId = UUID.randomUUID();
    private RewardRepository rewards;
    private RewardRedemptionRepository redemptions;
    private EventRepository events;
    private RewardService service;
    private AttendeePointBalanceRepository balances;
    private Reward reward;

    @BeforeEach
    void setUp() {
        rewards = mock(RewardRepository.class);
        redemptions = mock(RewardRedemptionRepository.class);
        balances = mock(AttendeePointBalanceRepository.class);
        events = mock(EventRepository.class);
        service = new RewardService(balances, mock(PointTransactionRepository.class),
                rewards, redemptions, events, mock(ScanPurposeRepository.class),
                mock(NotificationService.class),
                mock(com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort.class),
                mock(DuplicateRewardClaimChecker.class));
        reward = new Reward();
        reward.setId(rewardId);
        reward.setEventId(eventId);
        reward.setName("Tote");
        reward.setDescription("Canvas bag");
        reward.setPointsRequired(20);
        reward.setStockQuantity(5); // remaining
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.of(reward));
        when(rewards.save(any(Reward.class))).thenAnswer(inv -> {
            Reward saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(redemptions.countByRewardIdAndStatus(rewardId, RedemptionStatus.REDEEMED)).thenReturn(5L);
    }

    private RewardRequest update(String description, Integer stock, Integer total, Boolean unlimited) {
        return new RewardRequest(eventId, "Tote", description, 20, stock, false, total, unlimited);
    }

    private static ClaimedCountRow claimed(UUID id, long n) {
        return new ClaimedCountRow() {
            @Override public UUID getRewardId() { return id; }
            @Override public Long getTotal() { return n; }
        };
    }

    @Test
    void responseReportsClaimedAndTotalFromRemainingStock() {
        when(rewards.findByEventId(eventId)).thenReturn(List.of(reward));
        when(redemptions.countByEventIdAndStatusGroupedByReward(eventId, RedemptionStatus.REDEEMED))
                .thenReturn(List.of(claimed(rewardId, 5)));

        RewardResponse r = service.findRewards(eventId).get(0);

        assertThat(r.stockQuantity()).isEqualTo(5);
        assertThat(r.claimedCount()).isEqualTo(5L);
        assertThat(r.totalQuantity()).isEqualTo(10);
    }

    @Test
    void unlimitedRewardHasNullTotal() {
        reward.setStockQuantity(null);
        when(rewards.findByEventId(eventId)).thenReturn(List.of(reward));
        when(redemptions.countByEventIdAndStatusGroupedByReward(eventId, RedemptionStatus.REDEEMED)).thenReturn(List.of());

        RewardResponse r = service.findRewards(eventId).get(0);

        assertThat(r.totalQuantity()).isNull();
        assertThat(r.claimedCount()).isZero();
    }

    @Test
    void editTotalBelowClaimedIsRejected() {
        assertThatThrownBy(() -> service.updateReward(eventId, rewardId, update(null, null, 4, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Total quantity cannot be less than the 5 already claimed");
        verify(rewards, never()).save(any(Reward.class));
    }

    @Test
    void editTotalAboveClaimedRecomputesRemainingStock() {
        RewardResponse r = service.updateReward(eventId, rewardId, update(null, null, 12, null));

        assertThat(reward.getStockQuantity()).isEqualTo(7);
        assertThat(r.stockQuantity()).isEqualTo(7);
        assertThat(r.totalQuantity()).isEqualTo(12);
    }

    @Test
    void unlimitedFlagClearsStock() {
        RewardResponse r = service.updateReward(eventId, rewardId, update(null, null, null, true));

        assertThat(reward.getStockQuantity()).isNull();
        assertThat(r.totalQuantity()).isNull();
    }

    @Test
    void updateWithoutStockFieldsLeavesStockUnchanged() {
        service.updateReward(eventId, rewardId, update(null, null, null, null));
        assertThat(reward.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void legacyStockQuantityStillHonoredWhenTotalAbsent() {
        service.updateReward(eventId, rewardId, update(null, 9, null, null));
        assertThat(reward.getStockQuantity()).isEqualTo(9);
    }

    @Test
    void updateWithoutDescriptionKeepsDescription() {
        service.updateReward(eventId, rewardId, update(null, null, null, null));
        assertThat(reward.getDescription()).isEqualTo("Canvas bag");

        service.updateReward(eventId, rewardId, update("New text", null, null, null));
        assertThat(reward.getDescription()).isEqualTo("New text");
    }

    @Test
    void createWithTotalOrUnlimited() {
        RewardResponse withTotal = service.saveReward(new RewardRequest(eventId, "A", null, 1, null, false, 10, null));
        assertThat(withTotal.stockQuantity()).isEqualTo(10);
        assertThat(withTotal.totalQuantity()).isEqualTo(10);
        assertThat(withTotal.claimedCount()).isZero();

        RewardResponse unlimited = service.saveReward(new RewardRequest(eventId, "B", null, 1, null, false, null, null));
        assertThat(unlimited.stockQuantity()).isNull();
        assertThat(unlimited.totalQuantity()).isNull();
    }

    @Test
    void emptyDescriptionClearsIt() {
        RewardResponse r = service.updateReward(eventId, rewardId, update("", null, null, null));
        assertThat(reward.getDescription()).isNull();
        assertThat(r.description()).isNull();
    }

    @Test
    void updateTakesTheRowLockNotAPlainFind() {
        service.updateReward(eventId, rewardId, update(null, null, 12, null));
        verify(rewards).findByIdForUpdate(rewardId);
        verify(rewards, never()).findById(any());
    }

    @Test
    void attendeeReadsDoNotExposeCounts() {
        when(rewards.findByEventId(eventId)).thenReturn(List.of(reward));
        RewardResponse r = service.findRewardsForAttendee(eventId).get(0);
        assertThat(r.claimedCount()).isNull();
        assertThat(r.totalQuantity()).isNull();
        assertThat(r.stockQuantity()).isEqualTo(5);
        assertThat(service.findClaimableRewards(eventId).get(0).totalQuantity()).isNull();
    }

    // --- legacy redeem(): stock check + decrement through the shared guarded query ---

    private void redeemSetup(Integer stock, int decrementResult) {
        com.thedavelopers.eventqr.features.events.model.entity.Event event =
                new com.thedavelopers.eventqr.features.events.model.entity.Event();
        event.setRewardsEnabled(true);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        reward.setStockQuantity(stock);
        reward.setStatus(RewardStatus.ACTIVE);
        AttendeePointBalance balance = new AttendeePointBalance();
        balance.setPointsBalance(100);
        // Redeem must spend from the row-locked balance, never from a plain (unlocked) read.
        when(balances.findByEventIdAndAttendeeUserIdForUpdate(any(), any())).thenReturn(Optional.of(balance));
        when(redemptions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rewards.decrementStockIfAvailable(rewardId)).thenReturn(decrementResult);
    }

    @Test
    void legacyRedeemDecrementsAndRejectsWhenOutOfStock() {
        redeemSetup(2, 1);
        RewardRedemptionRequest req = new RewardRedemptionRequest(eventId, UUID.randomUUID(), rewardId);
        service.redeem(req);
        verify(rewards).decrementStockIfAvailable(rewardId);

        when(rewards.decrementStockIfAvailable(rewardId)).thenReturn(0);
        assertThatThrownBy(() -> service.redeem(req)).isInstanceOf(ConflictException.class)
                .hasMessage("Reward is out of stock");
    }

    @Test
    void legacyRedeemRejectedWhenRewardsDisabled() {
        redeemSetup(2, 1);
        com.thedavelopers.eventqr.features.events.model.entity.Event event =
                new com.thedavelopers.eventqr.features.events.model.entity.Event();
        event.setRewardsEnabled(false);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        assertThatThrownBy(() -> service.redeem(new RewardRedemptionRequest(eventId, UUID.randomUUID(), rewardId)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Reward redemption is disabled for this event");
    }

    @Test
    void legacyRedeemLocksTheRewardThenTheBalanceRow() {
        redeemSetup(null, 1);
        service.redeem(new RewardRedemptionRequest(eventId, UUID.randomUUID(), rewardId));
        var order = org.mockito.Mockito.inOrder(rewards, balances);
        order.verify(rewards).findByIdForUpdate(rewardId);
        order.verify(balances).findByEventIdAndAttendeeUserIdForUpdate(any(), any());
        verify(balances, never()).findByEventIdAndAttendeeUserId(any(), any());
    }

    @Test
    void scanPointsAreAwardedOnlyAfterTheScanTransactionCommits() throws Exception {
        java.lang.reflect.Method listener = RewardService.class.getMethod("onTransactionRecorded",
                com.thedavelopers.eventqr.shared.interfaces.TransactionRecordedEvent.class);
        var annotation = listener.getAnnotation(org.springframework.transaction.event.TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
        assertThat(listener.getAnnotation(org.springframework.context.event.EventListener.class)).isNull();
        assertThat(listener.getAnnotation(org.springframework.scheduling.annotation.Async.class)).isNotNull();
        assertThat(listener.getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
    }

    @Test
    void firstScanAwardCreatesTheBalanceRowBeforeLockingIt() {
        // No balance row yet: it must be created via ON CONFLICT DO NOTHING (never a plain save that a concurrent
        // first writer could collide with on the unique index), then locked and credited.
        UUID attendeeId = UUID.randomUUID();
        AttendeePointBalance created = new AttendeePointBalance();
        created.setEventId(eventId);
        created.setAttendeeUserId(attendeeId);
        created.setPointsBalance(0);
        when(balances.insertZeroIfAbsent(eventId, attendeeId)).thenReturn(1);
        when(balances.findByEventIdAndAttendeeUserIdForUpdate(eventId, attendeeId)).thenReturn(Optional.of(created));

        service.onTransactionRecorded(new com.thedavelopers.eventqr.shared.interfaces.TransactionRecordedEvent(
                UUID.randomUUID(), eventId, attendeeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                com.thedavelopers.eventqr.shared.constants.TransactionType.ENTRY,
                com.thedavelopers.eventqr.shared.constants.TransactionResult.APPROVED, 15, UUID.randomUUID(), null));

        var order = org.mockito.Mockito.inOrder(balances);
        order.verify(balances).boundLockWaits();
        order.verify(balances).insertZeroIfAbsent(eventId, attendeeId);
        order.verify(balances).findByEventIdAndAttendeeUserIdForUpdate(eventId, attendeeId);
        order.verify(balances).save(created);
        assertThat(created.getPointsBalance()).isEqualTo(15);
        verify(balances, never()).findByEventIdAndAttendeeUserId(any(), any());
    }

    @Test
    void legacyRedeemOnUnlimitedRewardStillSucceeds() {
        redeemSetup(null, 1); // guarded query returns 1 for NULL stock
        service.redeem(new RewardRedemptionRequest(eventId, UUID.randomUUID(), rewardId));
        verify(rewards).decrementStockIfAvailable(rewardId);
    }

    @Test
    void totalQuantityStaysConstantAcrossRedeems() {
        // remaining + claimed is invariant: each redeem moves one unit from stock to claimed.
        for (int redeemed = 0; redeemed <= 2; redeemed++) {
            reward.setStockQuantity(2 - redeemed);
            Reward snapshot = reward;
            assertThat(RewardResponse.of(snapshot, redeemed).totalQuantity()).isEqualTo(2);
        }
    }

    @Test
    void rewardNotFoundThrows404() {
        UUID unknown = UUID.randomUUID();
        when(rewards.findById(unknown)).thenReturn(Optional.empty());
        when(rewards.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findReward(eventId, unknown))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class)
                .hasMessage("Reward not found");

        assertThatThrownBy(() -> service.updateReward(eventId, unknown, update(null, null, 10, null)))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class)
                .hasMessage("Reward not found");

        assertThatThrownBy(() -> service.deleteReward(eventId, unknown))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class)
                .hasMessage("Reward not found");
    }

    @Test
    void rewardMismatchedEventThrows404FailClosed() {
        UUID otherEventId = UUID.randomUUID();
        when(rewards.findById(rewardId)).thenReturn(Optional.of(reward));
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.of(reward));

        assertThatThrownBy(() -> service.findReward(otherEventId, rewardId))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class)
                .hasMessage("Reward not found for event");

        assertThatThrownBy(() -> service.updateReward(otherEventId, rewardId, update(null, null, 10, null)))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class)
                .hasMessage("Reward not found for event");

        assertThatThrownBy(() -> service.deleteReward(otherEventId, rewardId))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class)
                .hasMessage("Reward not found for event");
    }

    @Test
    void redeemFailsClosedWhenRewardBelongsToAnotherEvent() {
        redeemSetup(5, 1);
        reward.setEventId(UUID.randomUUID());

        assertThatThrownBy(() -> service.redeem(new RewardRedemptionRequest(eventId, UUID.randomUUID(), rewardId)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Reward does not belong to the event");
    }
}
