package com.thedavelopers.eventqr.features.rewards.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionGrantRequest;
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
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

/** A redemption whose event row is gone fails closed: nothing is granted, deducted or recorded. */
class RewardRedemptionServiceMissingEventTest {

    @Test
    void aMissingEventIsNotFoundAndNothingIsRedeemed() {
        RewardRepository rewards = mock(RewardRepository.class);
        RewardRedemptionRepository redemptions = mock(RewardRedemptionRepository.class);
        TransactionLogRepository logs = mock(TransactionLogRepository.class);
        EventRepository events = mock(EventRepository.class);
        PointTransactionRepository points = mock(PointTransactionRepository.class);
        RewardRedemptionService service = new RewardRedemptionService(rewards, redemptions,
                mock(AttendeePointBalanceRepository.class), logs, mock(DuplicateRewardClaimChecker.class), events,
                mock(NotificationService.class), points);

        UUID eventId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID scanLogId = UUID.randomUUID();
        Reward reward = new Reward();
        reward.setId(rewardId);
        reward.setEventId(eventId);
        when(logs.findById(scanLogId)).thenReturn(Optional.of(new TransactionLog()));
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.of(reward));
        when(events.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(
                new RewardRedemptionGrantRequest(eventId, UUID.randomUUID(), rewardId, UUID.randomUUID(), scanLogId)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(redemptions, never()).save(any(RewardRedemption.class));
        verify(points, never()).save(any(PointTransaction.class));
    }

    @Test
    void aMissingScanLogIsNotFoundAndFailsClosed() {
        RewardRepository rewards = mock(RewardRepository.class);
        RewardRedemptionRepository redemptions = mock(RewardRedemptionRepository.class);
        TransactionLogRepository logs = mock(TransactionLogRepository.class);
        EventRepository events = mock(EventRepository.class);
        PointTransactionRepository points = mock(PointTransactionRepository.class);
        RewardRedemptionService service = new RewardRedemptionService(rewards, redemptions,
                mock(AttendeePointBalanceRepository.class), logs, mock(DuplicateRewardClaimChecker.class), events,
                mock(NotificationService.class), points);

        UUID eventId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID scanLogId = UUID.randomUUID();
        when(logs.findById(scanLogId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(
                new RewardRedemptionGrantRequest(eventId, UUID.randomUUID(), rewardId, UUID.randomUUID(), scanLogId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Redemption scan log not found");

        verify(rewards, never()).findByIdForUpdate(any());
        verify(redemptions, never()).save(any(RewardRedemption.class));
        verify(points, never()).save(any(PointTransaction.class));
    }

    @Test
    void aMissingRewardIsNotFoundAndFailsClosed() {
        RewardRepository rewards = mock(RewardRepository.class);
        RewardRedemptionRepository redemptions = mock(RewardRedemptionRepository.class);
        TransactionLogRepository logs = mock(TransactionLogRepository.class);
        EventRepository events = mock(EventRepository.class);
        PointTransactionRepository points = mock(PointTransactionRepository.class);
        RewardRedemptionService service = new RewardRedemptionService(rewards, redemptions,
                mock(AttendeePointBalanceRepository.class), logs, mock(DuplicateRewardClaimChecker.class), events,
                mock(NotificationService.class), points);

        UUID eventId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID scanLogId = UUID.randomUUID();
        when(logs.findById(scanLogId)).thenReturn(Optional.of(new TransactionLog()));
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(
                new RewardRedemptionGrantRequest(eventId, UUID.randomUUID(), rewardId, UUID.randomUUID(), scanLogId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Reward not found");

        verify(events, never()).findById(any());
        verify(redemptions, never()).save(any(RewardRedemption.class));
        verify(points, never()).save(any(PointTransaction.class));
    }

    @Test
    void aRewardBelongingToDifferentEventFailsClosedAndRejects() {
        RewardRepository rewards = mock(RewardRepository.class);
        RewardRedemptionRepository redemptions = mock(RewardRedemptionRepository.class);
        TransactionLogRepository logs = mock(TransactionLogRepository.class);
        EventRepository events = mock(EventRepository.class);
        PointTransactionRepository points = mock(PointTransactionRepository.class);
        AttendeePointBalanceRepository balances = mock(AttendeePointBalanceRepository.class);
        when(balances.save(any(AttendeePointBalance.class))).thenAnswer(inv -> inv.getArgument(0));
        RewardRedemptionService service = new RewardRedemptionService(rewards, redemptions,
                balances, logs, mock(DuplicateRewardClaimChecker.class), events,
                mock(NotificationService.class), points);

        UUID requestEventId = UUID.randomUUID();
        UUID rewardEventId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID scanLogId = UUID.randomUUID();
        Reward reward = new Reward();
        reward.setId(rewardId);
        reward.setEventId(rewardEventId);
        when(logs.findById(scanLogId)).thenReturn(Optional.of(new TransactionLog()));
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.of(reward));
        when(redemptions.save(any(RewardRedemption.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.redeem(
                new RewardRedemptionGrantRequest(requestEventId, UUID.randomUUID(), rewardId, UUID.randomUUID(), scanLogId));

        org.assertj.core.api.Assertions.assertThat(response.status())
                .isEqualTo(com.thedavelopers.eventqr.shared.constants.RedemptionStatus.REJECTED);
        org.assertj.core.api.Assertions.assertThat(response.reason())
                .isEqualTo("Reward does not belong to the event");
        org.assertj.core.api.Assertions.assertThat(response.approved()).isFalse();
        verify(points, never()).save(any(PointTransaction.class));
    }
}
