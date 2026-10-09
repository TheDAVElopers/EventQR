package com.thedavelopers.eventqr.features.rewards.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;

class RewardsActivityLookupTest {

    private final PointTransactionRepository points = mock(PointTransactionRepository.class);
    private final RewardsActivityLookup lookup = new RewardsActivityLookup(points);
    private final UUID eventId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();

    @Test
    void anyPointTransactionCountsAsActivity() {
        when(points.existsByEventIdAndAttendeeUserId(eventId, attendeeId)).thenReturn(true);
        assertThat(lookup.hasRecordedActivity(eventId, attendeeId, UUID.randomUUID())).isTrue();
    }

    @Test
    void noPointTransactionsMeansNoActivity() {
        assertThat(lookup.hasRecordedActivity(eventId, attendeeId, UUID.randomUUID())).isFalse();
    }
}
