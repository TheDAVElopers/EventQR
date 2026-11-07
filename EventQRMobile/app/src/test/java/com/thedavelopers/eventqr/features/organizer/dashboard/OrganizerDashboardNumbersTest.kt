package com.thedavelopers.eventqr.features.organizer.dashboard

import com.thedavelopers.eventqr.features.organizer.testOrganizerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Organizer dashboard numbers (§6.4): REGISTERED / ATTENDED / LIVE NOW in the pass head and
 * Upcoming / Ended / Redeemed as metric rows, all from one events response. Unknown is null
 * (`--` on screen) and a known zero is 0.
 */
class OrganizerDashboardNumbersTest {

    private val now = Instant.parse("2026-01-10T12:00:00Z")

    private fun event(
        startAt: Instant?,
        endAt: Instant?,
        registered: Long = 0,
        attended: Long = 0,
        redemptions: Long = 0,
    ) = testOrganizerEvent().copy(
        eventStartAt = startAt,
        eventEndAt = endAt,
        registeredCount = registered,
        attendedCount = attended,
        rewardRedemptions = redemptions,
    )

    private val live = event(now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), 120, 80, 9)
    private val upcoming = event(now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS), 30, 0, 4)
    private val ended = event(now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), 50, 10, 6)

    @Test
    fun head_sumsRegisteredAndAttended_countsOnlyLiveEvents() {
        val numbers = OrganizerDashboardNumbers.of(listOf(live, upcoming, ended), now = now)

        assertEquals(200L, numbers.registered)
        assertEquals(90L, numbers.attended)
        assertEquals(1L, numbers.liveNow)
    }

    @Test
    fun rows_countUpcomingAndEnded_andSumRedemptions() {
        val numbers = OrganizerDashboardNumbers.of(listOf(live, upcoming, ended), now = now)

        assertEquals(1L, numbers.upcoming)
        assertEquals(1L, numbers.ended)
        assertEquals(19L, numbers.redeemed)
    }

    @Test
    fun boundary_startOrEndExactlyNow_isLive() {
        val atNow = event(now, now, 1, 1, 0)

        assertEquals(1L, OrganizerDashboardNumbers.of(listOf(atNow), now = now).liveNow)
    }

    @Test
    fun missingDates_countAsLive_likeEventStatusBadgeStylerFromDates() {
        val undated = event(startAt = null, endAt = null, registered = 3, attended = 2, redemptions = 1)

        val numbers = OrganizerDashboardNumbers.of(listOf(undated), now = now)

        assertEquals(1L, numbers.liveNow)
        assertEquals(0L, numbers.upcoming)
        assertEquals(0L, numbers.ended)
    }

    @Test
    fun zeroEvents_areKnownZeros_notUnknown() {
        val numbers = OrganizerDashboardNumbers.of(emptyList(), now = now)

        assertEquals(0L, numbers.registered)
        assertEquals(0L, numbers.attended)
        assertEquals(0L, numbers.liveNow)
        assertEquals(0L, numbers.upcoming)
        assertEquals(0L, numbers.ended)
        assertEquals(0L, numbers.redeemed)
    }

    @Test
    fun failedEventsLoad_isUnknown_notZero() {
        val numbers = OrganizerDashboardNumbers.of(null, now = now)

        assertNull(numbers.registered)
        assertNull(numbers.attended)
        assertNull(numbers.liveNow)
        assertNull(numbers.upcoming)
        assertNull(numbers.ended)
        assertNull(numbers.redeemed)
    }
}
