package com.thedavelopers.eventqr.features.rewards.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest;
import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;
import com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;

/** Manual point adjustments: bounds, balance ceiling, rewards-enabled and active-registration guards. */
class RewardServicePointsTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private AttendeePointBalanceRepository balances;
    private PointTransactionRepository points;
    private RewardRepository rewards;
    private EventRepository events;
    private RegistrationLookupPort registrations;
    private RewardService service;
    private Event event;

    @BeforeEach
    void setUp() {
        balances = mock(AttendeePointBalanceRepository.class);
        points = mock(PointTransactionRepository.class);
        rewards = mock(RewardRepository.class);
        events = mock(EventRepository.class);
        registrations = mock(RegistrationLookupPort.class);
        service = new RewardService(balances, points, rewards, mock(RewardRedemptionRepository.class), events,
                mock(ScanPurposeRepository.class), mock(NotificationService.class), mock(AttendeeDirectoryPort.class),
                mock(DuplicateRewardClaimChecker.class), registrations);
        event = new Event();
        event.setRewardsEnabled(true);
        event.setTitle("Expo");
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(registrations.hasActiveRegistration(eventId, attendeeId)).thenReturn(true);
    }

    private AttendeePointBalance balanceOf(int value) {
        AttendeePointBalance balance = new AttendeePointBalance();
        balance.setEventId(eventId);
        balance.setAttendeeUserId(attendeeId);
        balance.setPointsBalance(value);
        when(balances.findByEventIdAndAttendeeUserIdForUpdate(eventId, attendeeId)).thenReturn(Optional.of(balance));
        return balance;
    }

    private void verifyNoBalanceTouched() {
        verify(balances, never()).insertZeroIfAbsent(any(), any());
        verify(balances, never()).save(any(AttendeePointBalance.class));
        verify(points, never()).save(any(PointTransaction.class));
    }

    @Test
    void nonRegistrantIsNotFoundAndNoBalanceRowIsCreated() {
        // hasActiveRegistration is false both for no registration and for a CANCELLED / NO_SHOW one.
        when(registrations.hasActiveRegistration(eventId, attendeeId)).thenReturn(false);

        assertThatThrownBy(() -> service.assignPoints(eventId, attendeeId, 10, null, actorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Attendee is not registered for this event");
        assertThatThrownBy(() -> service.deductPoints(eventId, attendeeId, 10, null, actorId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoBalanceTouched();
    }

    @Test
    void activeRegistrantIsCredited() {
        AttendeePointBalance balance = balanceOf(5);

        var response = service.assignPoints(eventId, attendeeId, 10, "Bonus", actorId);

        assertThat(response.pointsBalance()).isEqualTo(15);
        assertThat(balance.getPointsBalance()).isEqualTo(15);
        verify(points).save(any(PointTransaction.class));
    }

    @Test
    void outOfRangePointsAreBadRequestBeforeAnyLookup() {
        for (int p : new int[] {0, -1, 1001, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> service.assignPoints(eventId, attendeeId, p, null, actorId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Points must be between 1 and 1000");
            assertThatThrownBy(() -> service.deductPoints(eventId, attendeeId, p, null, actorId))
                    .isInstanceOf(BadRequestException.class);
        }
        verify(registrations, never()).hasActiveRegistration(any(), any());
        verifyNoBalanceTouched();
    }

    @Test
    void assignAboveTheBalanceCeilingIsConflictAndUnchanged() {
        AttendeePointBalance balance = balanceOf(999_500);

        assertThatThrownBy(() -> service.assignPoints(eventId, attendeeId, 1000, null, actorId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Point balance limit reached");

        assertThat(balance.getPointsBalance()).isEqualTo(999_500);
        verify(balances, never()).save(any(AttendeePointBalance.class));
        verify(points, never()).save(any(PointTransaction.class));
    }

    @Test
    void assignUpToTheCeilingIsAllowed() {
        balanceOf(999_000);
        assertThat(service.assignPoints(eventId, attendeeId, 1000, null, actorId).pointsBalance())
                .isEqualTo(RewardService.MAX_POINT_BALANCE);
    }

    @Test
    void rewardsDisabledIsBadRequest() {
        event.setRewardsEnabled(false);

        assertThatThrownBy(() -> service.assignPoints(eventId, attendeeId, 10, null, actorId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Reward redemption is disabled for this event");
        assertThatThrownBy(() -> service.deductPoints(eventId, attendeeId, 10, null, actorId))
                .isInstanceOf(BadRequestException.class);
        verifyNoBalanceTouched();
    }

    @Test
    void deductMoreThanBalanceIsStillConflict() {
        balanceOf(5);
        assertThatThrownBy(() -> service.deductPoints(eventId, attendeeId, 10, null, actorId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Not enough points to deduct");
    }

    @Test
    void scanLessRedeemRequiresAnActiveRegistration() {
        UUID rewardId = UUID.randomUUID();
        com.thedavelopers.eventqr.features.rewards.model.entity.Reward reward =
                new com.thedavelopers.eventqr.features.rewards.model.entity.Reward();
        reward.setId(rewardId);
        reward.setEventId(eventId);
        reward.setStatus(com.thedavelopers.eventqr.shared.constants.RewardStatus.ACTIVE);
        when(rewards.findByIdForUpdate(rewardId)).thenReturn(Optional.of(reward));
        when(registrations.hasActiveRegistration(eventId, attendeeId)).thenReturn(false);

        assertThatThrownBy(() -> service.redeem(new RewardRedemptionRequest(eventId, attendeeId, rewardId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Attendee is not registered for this event");
        verify(rewards, never()).findByIdForUpdate(any());
        verify(balances, never()).insertZeroIfAbsent(any(), any());
        verify(rewards, never()).decrementStockIfAvailable(any());
    }
}
