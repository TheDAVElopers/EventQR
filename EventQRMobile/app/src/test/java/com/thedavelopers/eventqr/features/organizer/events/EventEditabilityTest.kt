package com.thedavelopers.eventqr.features.organizer.events

import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The edit lock follows the date-derived status, not the raw backend label: a future event can
 * already be ACTIVE (admin "activate" or an early status sweep), and locking on that label hid
 * editing for an event that had not started yet.
 */
class EventEditabilityTest {

    private val now = Instant.parse("2026-01-10T12:00:00Z")
    private val tomorrowStart = now.plus(1, ChronoUnit.DAYS)
    private val tomorrowEnd = tomorrowStart.plus(4, ChronoUnit.HOURS)

    private fun isEditable(rawStatus: String, startAt: Instant?, endAt: Instant?): Boolean =
        EventStatusBadgeStyler.resolve(EventStatusBadgeStyler.fromLabel(rawStatus), startAt, endAt, now)
            .isEventEditable()

    @Test
    fun backendActive_startingTomorrow_isEditable() {
        assertTrue(isEditable("Active", tomorrowStart, tomorrowEnd))
    }

    @Test
    fun backendApproved_startingTomorrow_isEditable() {
        assertTrue(isEditable("Approved", tomorrowStart, tomorrowEnd))
    }

    @Test
    fun backendDraftAndPending_isEditable() {
        assertTrue(isEditable("Draft", tomorrowStart, tomorrowEnd))
        assertTrue(isEditable("Pending review", tomorrowStart, tomorrowEnd))
    }

    @Test
    fun insideWindow_isLocked() {
        assertFalse(isEditable("Active", now.minus(2, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS)))
    }

    @Test
    fun ended_isLocked() {
        assertFalse(isEditable("Completed", now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)))
    }

    @Test
    fun cancelled_isLocked() {
        assertFalse(isEditable("Cancelled", tomorrowStart, tomorrowEnd))
    }
}
