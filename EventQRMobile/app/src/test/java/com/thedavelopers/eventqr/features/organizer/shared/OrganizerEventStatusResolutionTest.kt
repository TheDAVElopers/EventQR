package com.thedavelopers.eventqr.features.organizer.shared

import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Organizer screens resolve the badge from the backend status plus the event window, because the
 * backend enum has no UPCOMING: "Upcoming" is only a display state of APPROVED before start.
 */
class OrganizerEventStatusResolutionTest {

    private val now = Instant.parse("2026-01-10T12:00:00Z")
    private val tomorrowStart = now.plus(1, ChronoUnit.DAYS)
    private val tomorrowEnd = tomorrowStart.plus(4, ChronoUnit.HOURS)
    private val yesterdayEnd = now.minus(1, ChronoUnit.DAYS)

    private fun resolve(rawStatus: String, startAt: Instant?, endAt: Instant?): EventStatus =
        EventStatusBadgeStyler.resolve(EventStatusBadgeStyler.fromLabel(rawStatus), startAt, endAt, now)

    @Test
    fun backendActive_startingTomorrow_rendersUpcoming() {
        val status = resolve("Active", tomorrowStart, tomorrowEnd)
        assertEquals(EventStatus.APPROVED, status)
        assertEquals("Upcoming", EventStatusBadgeStyler.displayLabel(status))
    }

    @Test
    fun backendActive_insideWindow_rendersActive() {
        val status = resolve("Active", now.minus(2, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS))
        assertEquals(EventStatus.ACTIVE, status)
        assertEquals("Active", EventStatusBadgeStyler.displayLabel(status))
    }

    @Test
    fun backendActive_endedYesterday_rendersCompleted() {
        val status = resolve("Active", now.minus(2, ChronoUnit.DAYS), yesterdayEnd)
        assertEquals(EventStatus.ENDED, status)
        assertEquals("Completed", EventStatusBadgeStyler.displayLabel(status))
    }

    @Test
    fun backendDraft_keepsBackendStatus() {
        val status = resolve("Draft", tomorrowStart, tomorrowEnd)
        assertEquals(EventStatus.DRAFT, status)
        assertEquals("Draft", EventStatusBadgeStyler.displayLabel(status))
    }
}
