package com.thedavelopers.eventqr.features.rewards.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RewardServiceClaimableTest {

    @Mock private AttendeePointBalanceRepository attendeePointBalanceRepository;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private RewardRepository rewardRepository;
    @Mock private RewardRedemptionRepository rewardRedemptionRepository;
    @Mock private EventRepository eventRepository;
    @Mock private ScanPurposeRepository scanPurposeRepository;
    @Mock private NotificationService notificationService;
    @InjectMocks private RewardService service;

    private Reward reward(String name, RewardStatus status, Integer stock) {
        Reward r = new Reward();
        r.setId(UUID.randomUUID());
        r.setName(name);
        r.setStatus(status);
        r.setStockQuantity(stock);
        return r;
    }

    @Test
    void claimableKeepsOnlyActiveRewardsWithStockOrUnlimited() {
        UUID eventId = UUID.randomUUID();
        when(rewardRepository.findByEventId(eventId)).thenReturn(List.of(
                reward("unlimited", RewardStatus.ACTIVE, null),
                reward("in-stock", RewardStatus.ACTIVE, 3),
                reward("sold-out", RewardStatus.ACTIVE, 0),
                reward("inactive", RewardStatus.INACTIVE, 5),
                reward("inactive-unlimited", RewardStatus.INACTIVE, null)));

        List<RewardResponse> result = service.findClaimableRewards(eventId);

        assertThat(result).extracting(RewardResponse::name).containsExactly("unlimited", "in-stock");
    }
}
