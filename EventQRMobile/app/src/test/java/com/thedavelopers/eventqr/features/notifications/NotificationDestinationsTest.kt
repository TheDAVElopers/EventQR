package com.thedavelopers.eventqr.features.notifications

import com.thedavelopers.eventqr.core.api.dto.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The row chevron and the in-app tap share this table, so a row never promises a missing destination. */
class NotificationDestinationsTest {

    @Test
    fun `rejected opens the organizer event details`() {
        assertEquals(
            NotificationDestination.ORGANIZER_EVENT_DETAILS,
            notificationDestinationFor(NotificationType.EVENT_REJECTED, "event-1"),
        )
    }

    @Test
    fun `approval and every other type open the hub`() {
        assertEquals(
            NotificationDestination.ORGANIZER_HUB,
            notificationDestinationFor(NotificationType.EVENT_APPROVED, "event-1"),
        )
        assertEquals(
            NotificationDestination.ORGANIZER_HUB,
            notificationDestinationFor(NotificationType.REGISTRATION_NEW, "event-1"),
        )
    }

    @Test
    fun `a rejection without an event id still opens the request list`() {
        assertEquals(
            NotificationDestination.ATTENDEE_REQUESTS,
            notificationDestinationFor(NotificationType.EVENT_REJECTED, null),
        )
        assertEquals(
            NotificationDestination.ATTENDEE_REQUESTS,
            notificationDestinationFor(NotificationType.EVENT_REJECTED, ""),
        )
        assertNull(notificationDestinationFor(NotificationType.GENERAL, " "))
        assertNull(notificationDestinationFor(NotificationType.EVENT_APPROVED, null))
    }

    @Test
    fun `rejection routes by role and never to a screen the reader cannot see`() {
        assertEquals(
            NotificationDestination.ORGANIZER_EVENT_DETAILS,
            resolvedDestination(NotificationType.EVENT_REJECTED, "event-1", organizer = true),
        )
        assertEquals(
            NotificationDestination.ATTENDEE_REQUESTS,
            resolvedDestination(NotificationType.EVENT_REJECTED, "event-1", organizer = false),
        )
    }

    @Test
    fun `every other type is the hub for an organizer and the event detail for everyone else`() {
        assertEquals(
            NotificationDestination.ORGANIZER_HUB,
            resolvedDestination(NotificationType.EVENT_STARTING_SOON, "event-1", organizer = true),
        )
        assertEquals(
            NotificationDestination.ATTENDEE_EVENT_DETAIL,
            resolvedDestination(NotificationType.EVENT_STARTING_SOON, "event-1", organizer = false),
        )
    }

    @Test
    fun `a rejection without an event id reaches the request list for every role`() {
        assertEquals(
            NotificationDestination.ATTENDEE_REQUESTS,
            resolvedDestination(NotificationType.EVENT_REJECTED, null, organizer = true),
        )
        assertEquals(
            NotificationDestination.ATTENDEE_REQUESTS,
            resolvedDestination(NotificationType.EVENT_REJECTED, null, organizer = false),
        )
        assertEquals(
            NotificationDestination.ATTENDEE_REQUESTS,
            resolvedDestination(NotificationType.EVENT_REJECTED, "", organizer = false),
        )
        assertNull(resolvedDestination(NotificationType.EVENT_APPROVED, null, organizer = true))
    }
}
