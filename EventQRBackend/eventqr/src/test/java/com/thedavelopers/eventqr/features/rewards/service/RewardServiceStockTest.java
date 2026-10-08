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
    private RewardService service;
    private AttendeePointBalanceRepository balances;
    private Reward reward;

    @BeforeEach
    void setUp() {
        rewards = mock(RewardRepository.class);
        redemptions = mock(RewardRedemptionRepository.class);
        balances = mock(AttendeePointBalanceRepository.class);
        service = new RewardService(balances, mock(PointTransactionRepository.class),
                rewards, redemptions, mock(EventRepository.class), mock(ScanPurposeRepository.class),
                mock(NotificationService.class));
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
        reward.setStockQuantity(stock);
        reward.setStatus(RewardStatus.ACTIVE);
        AttendeePointBalance balance = new AttendeePointBalance();
        balance.setPointsBalance(100);
        when(balances.findByEventIdAndAttendeeUserId(any(), any())).thenReturn(Optional.of(balance));
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
}
