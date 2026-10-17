package com.thedavelopers.eventqr.features.events.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;

/**
 * Orchestration coverage for the event status sweep: notification fan-out must run only after the timed transition
 * transaction has returned successfully, so a rollback can never send notifications for a transition that did not
 * happen, and a failed sweep leaves the next one free to re-transition the same wall-clock-due events.
 *
 * <p>This is a unit-level simulation of the timeout/rollback branch (the transition fails, the orchestrator skips
 * the fan-out and the exception propagates for the next sweep to retry). Reproducing a real statement timeout
 * would need a database plus a lock/sleep harness and is not attempted here.
 */
class EventStatusSchedulerTest {

    private EventStatusTransitionService transitionService;
    private NotificationService notificationService;
    private EventStatusScheduler scheduler;

    @BeforeEach
    void setUp() {
        transitionService = mock(EventStatusTransitionService.class);
        notificationService = mock(NotificationService.class);
        scheduler = new EventStatusScheduler(transitionService, notificationService);
    }

    @Test
    void notificationsAreSentForTheStartedAndCompletingEvents() {
        Event starting = mock(Event.class);
        Event completing = mock(Event.class);
        when(transitionService.transitionOverdueEvents())
                .thenReturn(new EventStatusTransitionService.SweepResult(List.of(starting), List.of(completing)));

        scheduler.transitionOverdueEvents();

        verify(notificationService).createEventStartingSoonNotifications(List.of(starting));
        verify(notificationService).createEventCompletedNotifications(List.of(completing));
    }

    @Test
    void aSweepWithNothingToTransitionSendsNoNotifications() {
        when(transitionService.transitionOverdueEvents())
                .thenReturn(new EventStatusTransitionService.SweepResult(List.of(), List.of()));

        scheduler.transitionOverdueEvents();

        verify(notificationService, never()).createEventStartingSoonNotifications(any());
        verify(notificationService, never()).createEventCompletedNotifications(any());
    }

    @Test
    void aRolledBackTransitionSendsNoNotifications_andTheNextSweepRetriesIt() {
        Event starting = mock(Event.class);
        when(transitionService.transitionOverdueEvents())
                .thenThrow(new DataAccessResourceFailureException("statement timed out"))
                .thenReturn(new EventStatusTransitionService.SweepResult(List.of(starting), List.of()));

        assertThatThrownBy(() -> scheduler.transitionOverdueEvents())
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(notificationService, never()).createEventStartingSoonNotifications(any());
        verify(notificationService, never()).createEventCompletedNotifications(any());

        scheduler.transitionOverdueEvents();

        verify(transitionService, times(2)).transitionOverdueEvents();
        verify(notificationService).createEventStartingSoonNotifications(List.of(starting));
    }

    @Test
    void aFailingStartedSoonFanOutDoesNotSuppressTheCompletedFanOut() {
        Event starting = mock(Event.class);
        Event completing = mock(Event.class);
        when(transitionService.transitionOverdueEvents())
                .thenReturn(new EventStatusTransitionService.SweepResult(List.of(starting), List.of(completing)));
        willThrow(new DataAccessResourceFailureException("write failed"))
                .given(notificationService).createEventStartingSoonNotifications(any());

        // The exception is contained and logged, not propagated into the scheduler task.
        assertDoesNotThrow(() -> scheduler.transitionOverdueEvents());

        verify(notificationService).createEventStartingSoonNotifications(List.of(starting));
        // The second batch still runs, so a transient failure cannot lose it as well.
        verify(notificationService).createEventCompletedNotifications(List.of(completing));
    }

    @Test
    void aFailingCompletedFanOutIsContained() {
        Event completing = mock(Event.class);
        when(transitionService.transitionOverdueEvents())
                .thenReturn(new EventStatusTransitionService.SweepResult(List.of(), List.of(completing)));
        willThrow(new DataAccessResourceFailureException("write failed"))
                .given(notificationService).createEventCompletedNotifications(any());

        assertDoesNotThrow(() -> scheduler.transitionOverdueEvents());

        verify(notificationService).createEventCompletedNotifications(List.of(completing));
    }
}
