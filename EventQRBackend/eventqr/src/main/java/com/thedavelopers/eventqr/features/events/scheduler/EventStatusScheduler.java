package com.thedavelopers.eventqr.features.events.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.features.events.scheduler.EventStatusTransitionService.SweepResult;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;

/**
 * Scheduled entry point for the event status sweep.
 *
 * <p>SINGLE-INSTANCE CONSTRAINT: this scheduler (and any other @Scheduled job in the app, e.g. the
 * password-reset-token purge) assumes exactly one app instance is running.
 * No distributed lock (ShedLock) is applied yet; running multiple replicas would cause
 * duplicate sweeps/updates. Introduce ShedLock before scaling to >1 instance.
 *
 * <p>The timed, transactional transition lives in {@link EventStatusTransitionService}. Notification fan-out is
 * done here, after that transaction has committed, so the timeout (and any rollback) covers the transition work
 * only: a slow notification write can no longer undo the status change on every sweep and wedge the state machine.
 * The trade-off is that notifications for an event are no longer retried by the sweep once the event has moved;
 * they are logged and dropped if their own transaction fails.
 *
 * <p>Each fan-out batch is isolated: a failure in one is logged and swallowed so it cannot suppress the other batch
 * (both would otherwise be lost, since the transition has already committed) nor kill the scheduler task itself.
 */
@Component
public class EventStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventStatusScheduler.class);

    private static final long SWEEP_INTERVAL_MS = 60_000L;

    private final EventStatusTransitionService transitionService;
    private final NotificationService notificationService;

    public EventStatusScheduler(EventStatusTransitionService transitionService,
                                NotificationService notificationService) {
        this.transitionService = transitionService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = SWEEP_INTERVAL_MS)
    public void transitionOverdueEvents() {
        SweepResult result = transitionService.transitionOverdueEvents();

        if (!result.started().isEmpty()) {
            try {
                notificationService.createEventStartingSoonNotifications(result.started());
            } catch (Exception exception) {
                log.error("Event status sweep: starting-soon notifications failed for {} event(s); the events have "
                        + "already transitioned, so they will not be retried", result.started().size(), exception);
            }
        }
        if (!result.completing().isEmpty()) {
            try {
                notificationService.createEventCompletedNotifications(result.completing());
            } catch (Exception exception) {
                log.error("Event status sweep: completed notifications failed for {} event(s); the events have "
                        + "already transitioned, so they will not be retried", result.completing().size(), exception);
            }
        }
    }
}
