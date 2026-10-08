package com.thedavelopers.eventqr.features.events.scheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.shared.constants.EventStatus;

/**
 * Scheduled sweep that transitions events between statuses based on wall-clock time.
 *
 * <p>SINGLE-INSTANCE CONSTRAINT: this scheduler (and any other @Scheduled job in the app,
 * e.g. the password-reset-token purge) assumes exactly one app instance is running.
 * No distributed lock (ShedLock) is applied yet; running multiple replicas would cause
 * duplicate sweeps/updates. Introduce ShedLock before scaling to >1 instance.
 */
@Component
public class EventStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventStatusScheduler.class);

    private static final long SWEEP_INTERVAL_MS = 60_000L;

    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final CacheManager cacheManager;

    public EventStatusScheduler(EventRepository eventRepository, NotificationService notificationService,
                                CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
    }

    /** Evicts after the surrounding transaction commits so readers cannot re-cache pre-commit state. */
    private void evictEventsCache() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clearEventsCache();
                }
            });
        } else {
            clearEventsCache();
        }
    }

    private void clearEventsCache() {
        Cache cache = cacheManager.getCache("events");
        if (cache != null) {
            cache.clear();
        }
    }

    @Scheduled(fixedRate = SWEEP_INTERVAL_MS)
    @Transactional
    public void transitionOverdueEvents() {
        Instant now = Instant.now();

        List<Event> started = eventRepository.findByStatusAndEventStartAtLessThanEqual(
                EventStatus.APPROVED, now);
        // Events already ACTIVE whose end has passed, plus events that start AND end within this sweep
        // (activated below, then ended in the same sweep) so they still receive a completed notification.
        List<Event> completing = new ArrayList<>(eventRepository.findByStatusAndEventEndAtLessThanEqual(
                EventStatus.ACTIVE, now));
        started.stream()
                .filter(e -> e.getEventEndAt() != null && !e.getEventEndAt().isAfter(now))
                .forEach(completing::add);

        int activated = eventRepository.bulkUpdateStatusForStartedEvents(
                EventStatus.APPROVED, EventStatus.ACTIVE, now);
        int ended = eventRepository.bulkUpdateStatusForFinishedEvents(
                EventStatus.ACTIVE, EventStatus.ENDED, now);

        if (activated > 0 || ended > 0) {
            log.info("Event status sweep: {} event(s) moved to ACTIVE, {} event(s) moved to ENDED", activated, ended);
        }

        if (activated > 0 || ended > 0) {
            evictEventsCache();
        }

        if (!started.isEmpty()) {
            notificationService.createEventStartingSoonNotifications(started);
        }
        if (!completing.isEmpty()) {
            notificationService.createEventCompletedNotifications(completing);
        }
    }
}
