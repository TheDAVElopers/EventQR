package com.thedavelopers.eventqr.features.events

import com.thedavelopers.eventqr.core.api.dto.EventStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class EventStatusBadgeStylerTest {

    @Test
    fun displayLabel_mapsAllEventStatuses() {
        assertEquals("Draft", EventStatusBadgeStyler.displayLabel(EventStatus.DRAFT))
        assertEquals("Pending Review", EventStatusBadgeStyler.displayLabel(EventStatus.PENDING_REVIEW))
        assertEquals("Upcoming", EventStatusBadgeStyler.displayLabel(EventStatus.APPROVED))
        assertEquals("Active", EventStatusBadgeStyler.displayLabel(EventStatus.ACTIVE))
        assertEquals("Completed", EventStatusBadgeStyler.displayLabel(EventStatus.ENDED))
        assertEquals("Rejected", EventStatusBadgeStyler.displayLabel(EventStatus.REJECTED))
        assertEquals("Cancelled", EventStatusBadgeStyler.displayLabel(EventStatus.CANCELLED))
        assertEquals("Status: PENDING", EventStatusBadgeStyler.displayLabel(EventStatus.UNKNOWN, "PENDING"))
        assertEquals("Status not recognized", EventStatusBadgeStyler.displayLabel(EventStatus.UNKNOWN, null))
    }

    @Test
    fun fromLabel_mapsStringsToEventStatus() {
        assertEquals(EventStatus.ENDED, EventStatusBadgeStyler.fromLabel("Completed"))
        assertEquals(EventStatus.ENDED, EventStatusBadgeStyler.fromLabel("ended"))
        assertEquals(EventStatus.ACTIVE, EventStatusBadgeStyler.fromLabel("Active"))
        assertEquals(EventStatus.ACTIVE, EventStatusBadgeStyler.fromLabel("ongoing"))
        assertEquals(EventStatus.APPROVED, EventStatusBadgeStyler.fromLabel("Upcoming"))
        assertEquals(EventStatus.APPROVED, EventStatusBadgeStyler.fromLabel("approved"))
        assertEquals(EventStatus.DRAFT, EventStatusBadgeStyler.fromLabel("Draft"))
        assertEquals(EventStatus.PENDING_REVIEW, EventStatusBadgeStyler.fromLabel("Pending Review"))
        assertEquals(EventStatus.REJECTED, EventStatusBadgeStyler.fromLabel("Rejected"))
        assertEquals(EventStatus.CANCELLED, EventStatusBadgeStyler.fromLabel("Cancelled"))
        assertEquals(EventStatus.UNKNOWN, EventStatusBadgeStyler.fromLabel("unrecognized"))
    }

    @Test
    fun fromDates_resolvesStatusAccurately() {
        val now = Instant.now()
        val futureStart = now.plus(2, ChronoUnit.HOURS)
        val futureEnd = now.plus(4, ChronoUnit.HOURS)
        val pastStart = now.minus(4, ChronoUnit.HOURS)
        val pastEnd = now.minus(2, ChronoUnit.HOURS)

        // Future start -> Upcoming (APPROVED)
        assertEquals(EventStatus.APPROVED, EventStatusBadgeStyler.fromDates(futureStart, futureEnd, now))

        // Past end -> Completed (ENDED)
        assertEquals(EventStatus.ENDED, EventStatusBadgeStyler.fromDates(pastStart, pastEnd, now))

        // In progress (past start, future end) -> ACTIVE
        assertEquals(EventStatus.ACTIVE, EventStatusBadgeStyler.fromDates(pastStart, futureEnd, now))
    }

    @Test
    fun resolve_preservesNonDateStatuses() {
        val now = Instant.now()
        val futureStart = now.plus(2, ChronoUnit.HOURS)
        val futureEnd = now.plus(4, ChronoUnit.HOURS)

        // Non-date statuses must be kept regardless of dates
        assertEquals(EventStatus.DRAFT, EventStatusBadgeStyler.resolve(EventStatus.DRAFT, futureStart, futureEnd, now))
        assertEquals(EventStatus.PENDING_REVIEW, EventStatusBadgeStyler.resolve(EventStatus.PENDING_REVIEW, futureStart, futureEnd, now))
        assertEquals(EventStatus.REJECTED, EventStatusBadgeStyler.resolve(EventStatus.REJECTED, futureStart, futureEnd, now))
        assertEquals(EventStatus.CANCELLED, EventStatusBadgeStyler.resolve(EventStatus.CANCELLED, futureStart, futureEnd, now))

        // Date-dependent statuses are overridden by date
        assertEquals(EventStatus.APPROVED, EventStatusBadgeStyler.resolve(EventStatus.ACTIVE, futureStart, futureEnd, now))
    }
}
