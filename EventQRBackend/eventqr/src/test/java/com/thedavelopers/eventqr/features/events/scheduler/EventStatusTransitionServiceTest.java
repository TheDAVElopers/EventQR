package com.thedavelopers.eventqr.features.events.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.shared.constants.EventStatus;

/**
 * Coverage for the transactional half of the event status sweep: which events it reports as owed a notification,
 * and that the events cache is evicted only once the transition transaction has committed.
 */
class EventStatusTransitionServiceTest {

    private EventRepository events;
    private Cache cache;
    private EventStatusTransitionService transitionService;

    @BeforeEach
    void setUp() {
        events = mock(EventRepository.class);
        cache = mock(Cache.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache("events")).thenReturn(cache);
        when(events.findByStatusAndEventStartAtLessThanEqual(any(), any(Instant.class))).thenReturn(List.of());
        when(events.findByStatusAndEventEndAtLessThanEqual(any(), any(Instant.class))).thenReturn(List.of());
        transitionService = new EventStatusTransitionService(events, cacheManager);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void sweepMoves(int activated, int ended) {
        when(events.bulkUpdateStatusForStartedEvents(any(EventStatus.class), any(EventStatus.class), any(Instant.class)))
                .thenReturn(activated);
        when(events.bulkUpdateStatusForFinishedEvents(any(EventStatus.class), any(EventStatus.class), any(Instant.class)))
                .thenReturn(ended);
    }

    @Test
    void withoutATransactionTheCacheIsClearedImmediately() {
        sweepMoves(1, 0);

        transitionService.transitionOverdueEvents();

        verify(cache, times(1)).clear();
    }

    @Test
    void insideATransactionTheCacheIsClearedOnlyAfterCommit() {
        sweepMoves(1, 0);
        TransactionSynchronizationManager.initSynchronization();

        transitionService.transitionOverdueEvents();

        verify(cache, never()).clear();
        for (TransactionSynchronization synchronization : new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())) {
            synchronization.afterCommit();
        }
        verify(cache, times(1)).clear();
    }

    @Test
    void aSweepThatChangesNothingLeavesTheCacheAlone() {
        sweepMoves(0, 0);

        transitionService.transitionOverdueEvents();

        verify(cache, never()).clear();
    }

    @Test
    void resultReportsTheEventsOwedAStartingSoonAndACompletedNotification() {
        Event starting = mock(Event.class);
        Event active = mock(Event.class);
        when(events.findByStatusAndEventStartAtLessThanEqual(any(), any(Instant.class))).thenReturn(List.of(starting));
        when(events.findByStatusAndEventEndAtLessThanEqual(any(), any(Instant.class))).thenReturn(List.of(active));
        sweepMoves(1, 1);

        EventStatusTransitionService.SweepResult result = transitionService.transitionOverdueEvents();

        assertThat(result.started()).containsExactly(starting);
        assertThat(result.completing()).containsExactly(active);
    }

    @Test
    void anEventThatStartsAndEndsWithinTheSweepIsAlsoReportedAsCompleting() {
        Event shortLived = mock(Event.class);
        when(shortLived.getEventEndAt()).thenReturn(Instant.now().minusSeconds(1));
        when(events.findByStatusAndEventStartAtLessThanEqual(any(), any(Instant.class))).thenReturn(List.of(shortLived));
        sweepMoves(1, 0);

        EventStatusTransitionService.SweepResult result = transitionService.transitionOverdueEvents();

        assertThat(result.started()).containsExactly(shortLived);
        assertThat(result.completing()).containsExactly(shortLived);
    }
}
