package com.thedavelopers.eventqr.features.events.scheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.shared.constants.EventStatus;

/**
 * Transactional half of the event status sweep: moves APPROVED → ACTIVE and ACTIVE → ENDED by wall-clock time.
 *
 * <p>The transaction timeout below covers the transition statements only. Notification fan-out is deliberately
 * kept out of it (see {@link EventStatusScheduler}): each notification batch opens its own transaction and must
 * not be able to roll the status change back, otherwise a slow or failing notification write would undo the
 * transition on every sweep and wedge the state machine.
 */
@Service
public class EventStatusTransitionService {

    private static final Logger log = LoggerFactory.getLogger(EventStatusTransitionService.class);

    /**
     * Upper bound on the JDBC statement timeout for one transition sweep. Spring's HibernateJpaDialect turns a
     * transaction timeout into {@code org.hibernate.Transaction.setTimeout}, which Hibernate applies to the
     * statements issued inside this transaction, so a single stalled statement cannot pin one of the five pooled
     * connections (and therefore a request thread) between sweeps. The sweep's queries are index-backed and it runs
     * once a minute, so 20 s is far above a healthy run; if it does fire, the transaction rolls back and the next
     * sweep picks the same time-based transitions back up (the predicates are wall-clock based, not cursors).
     */
    private static final int SWEEP_STATEMENT_TIMEOUT_SECONDS = 20;

    private final EventRepository eventRepository;
    private final CacheManager cacheManager;

    public EventStatusTransitionService(EventRepository eventRepository, CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.eventRepository = eventRepository;
    }

    /**
     * Applies the transitions due at the call instant and returns the events that are owed a notification, so the
     * caller can fan those out <em>after</em> this transaction has committed.
     */
    @Transactional(timeout = SWEEP_STATEMENT_TIMEOUT_SECONDS)
    public SweepResult transitionOverdueEvents() {
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
            evictEventsCache();
        }

        return new SweepResult(List.copyOf(started), List.copyOf(completing));
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

    /** Events owed a notification, handed back from the timed transaction so the fan-out runs after it commits. */
    public record SweepResult(List<Event> started, List<Event> completing) {
    }
}
