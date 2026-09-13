package com.thedavelopers.eventqr.features.events.scheduler;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    public EventStatusScheduler(EventRepository eventRepository, NotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = SWEEP_INTERVAL_MS)
    @Transactional
    public void transitionOverdueEvents() {
        Instant now = Instant.now();

        List<Event> startingSoon = eventRepository.findByStatusAndEventStartAtLessThanEqual(
                EventStatus.APPROVED, now);
        List<Event> completing = eventRepository.findByStatusAndEventEndAtLessThanEqual(
                EventStatus.ACTIVE, now);

        int activated = eventRepository.bulkUpdateStatusForStartedEvents(
                EventStatus.APPROVED, EventStatus.ACTIVE, now);
        int ended = eventRepository.bulkUpdateStatusForFinishedEvents(
                EventStatus.ACTIVE, EventStatus.ENDED, now);

        if (activated > 0 || ended > 0) {
            log.info("Event status sweep: {} event(s) moved to ACTIVE, {} event(s) moved to ENDED", activated, ended);
        }

        if (!startingSoon.isEmpty()) {
            notificationService.createEventStartingSoonNotifications(startingSoon);
        }
        if (!completing.isEmpty()) {
            notificationService.createEventCompletedNotifications(completing);
        }
    }
}
