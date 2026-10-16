package com.thedavelopers.eventqr.features.rewards.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;

/** A redemption whose event row is gone fails closed: nothing is granted, deducted or recorded. */
class RewardRedemptionServiceMissingEventTest {

    private static RewardRedemptionService service(RewardRepository rewards, RewardRedemptionRepository redemptions,
                                                   AttendeePointBalanceRepository balances, TransactionLogRepository logs,
                                                   EventRepository events, PointTransactionRepository points) {
        RegistrationLookupPort registrations = mock(RegistrationLookupPort.class);
        when(registrations.hasActiveRegistration(any(), any())).thenReturn(true);
        return new RewardRedemptionService(rewards, redemptions, balances, logs,
                mock(DuplicateRewardClaimChecker.class), events, mock(NotificationService.class), points, registrations);
    }

    /** A fresh APPROVED redemption scan that matches the request, so only the condition under test can fail. */
    private static TransactionLog scanLog(UUID scanLogId, UUID eventId, UUID attendeeId, UUID staffId) {
        TransactionLog log = new TransactionLog();
        log.setId(scanLogId);
        log.setEventId(eventId);
        log.setAttendeeUserId(attendeeId);
        log.setStaffUserId(staffId);
        log.setTransactionType(TransactionType.REWARD_REDEMPTION_SCAN);
        log.setTransactionResult(TransactionResult.APPROVED);
        log.setScannedAt(Instant.now());
        return log;
    }

    @Test
    void aMissingEventIsNotFoundAndNothingIsRedeemed() {
        RewardRepository rewards = mock(RewardRepository.class);
        RewardRedemptionRepository redemptions = mock(RewardRedemptionRepository.class);
        TransactionLogRepository logs = mock(TransactionLogRepository.class);
        EventRepository events = mock(EventRepository.class);
        PointTransactionRepository points = mock(PointTransactionRepository.class);
        RewardRedemptionService service = service(rewards, redemptions,
                mock(AttendeePointBalanceRepository.class), logs, events, points);

        UUID eventId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID scanLogId = UUID.randomUUID();
        UUID attendeeId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Reward reward = new Reward();
        reward.setId(rewardId);
        reward.setEventId(eventId);
        when(logs.findByIdForUpdate(scanLogId)).thenReturn(Optional.of(scanLog(scanLogId, eventId, attendeeId, staffId)));
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.of(reward));
        when(events.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(
                new RewardRedemptionGrantRequest(eventId, attendeeId, rewardId, staffId, scanLogId)))
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
        RewardRedemptionService service = service(rewards, redemptions,
                mock(AttendeePointBalanceRepository.class), logs, events, points);

        UUID eventId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID scanLogId = UUID.randomUUID();
        when(logs.findByIdForUpdate(scanLogId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(
                new RewardRedemptionGrantRequest(eventId, UUID.randomUUID(), rewardId, UUID.randomUUID(), scanLogId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Redemption scan log not found");

        verify(logs, never()).findById(any());
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
        RewardRedemptionService service = service(rewards, redemptions,
                mock(AttendeePointBalanceRepository.class), logs, events, points);

        UUID eventId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID scanLogId = UUID.randomUUID();
        UUID attendeeId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        when(logs.findByIdForUpdate(scanLogId)).thenReturn(Optional.of(scanLog(scanLogId, eventId, attendeeId, staffId)));
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(
                new RewardRedemptionGrantRequest(eventId, attendeeId, rewardId, staffId, scanLogId)))
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
        RewardRedemptionService service = service(rewards, redemptions, balances, logs, events, points);

        UUID requestEventId = UUID.randomUUID();
        UUID rewardEventId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID scanLogId = UUID.randomUUID();
        UUID attendeeId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Reward reward = new Reward();
        reward.setId(rewardId);
        reward.setEventId(rewardEventId);
        when(logs.findByIdForUpdate(scanLogId))
                .thenReturn(Optional.of(scanLog(scanLogId, requestEventId, attendeeId, staffId)));
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.of(reward));
        when(redemptions.save(any(RewardRedemption.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.redeem(
                new RewardRedemptionGrantRequest(requestEventId, attendeeId, rewardId, staffId, scanLogId));

        org.assertj.core.api.Assertions.assertThat(response.status())
                .isEqualTo(com.thedavelopers.eventqr.shared.constants.RedemptionStatus.REJECTED);
        org.assertj.core.api.Assertions.assertThat(response.reason())
                .isEqualTo("Reward does not belong to the event");
        org.assertj.core.api.Assertions.assertThat(response.approved()).isFalse();
        verify(points, never()).save(any(PointTransaction.class));
    }
}
