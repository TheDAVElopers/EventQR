package com.thedavelopers.eventqr.features.events.scheduler;

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

import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.shared.constants.EventStatus;

class EventStatusSchedulerCacheEvictionTest {

    private EventRepository events;
    private Cache cache;
    private EventStatusScheduler scheduler;

    @BeforeEach
    void setUp() {
        events = mock(EventRepository.class);
        cache = mock(Cache.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache("events")).thenReturn(cache);
        when(events.findByStatusAndEventStartAtLessThanEqual(any(), any(Instant.class))).thenReturn(List.of());
        when(events.findByStatusAndEventEndAtLessThanEqual(any(), any(Instant.class))).thenReturn(List.of());
        scheduler = new EventStatusScheduler(events, mock(NotificationService.class), cacheManager);
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

        scheduler.transitionOverdueEvents();

        verify(cache, times(1)).clear();
    }

    @Test
    void insideATransactionTheCacheIsClearedOnlyAfterCommit() {
        sweepMoves(1, 0);
        TransactionSynchronizationManager.initSynchronization();

        scheduler.transitionOverdueEvents();

        verify(cache, never()).clear();
        for (TransactionSynchronization synchronization : new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())) {
            synchronization.afterCommit();
        }
        verify(cache, times(1)).clear();
    }

    @Test
    void aSweepThatChangesNothingLeavesTheCacheAlone() {
        sweepMoves(0, 0);

        scheduler.transitionOverdueEvents();

        verify(cache, never()).clear();
    }
}
