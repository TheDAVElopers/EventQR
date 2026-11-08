package com.thedavelopers.eventqr.features.attendee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** The half-open Asia/Manila windows of the Discover date filter (§6.2 amendment). */
class DateFilterRangesTest {

    private fun manila(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(DateFilterRanges.ZONE).toInstant()

    private val november = DiscoverDateFilter.MonthOf(YearMonth.of(2026, 11))

    @Test
    fun month_startsAtManilaMidnightOnTheFirst() {
        assertFalse(
            "31 Oct 23:59 is still October",
            DateFilterRanges.matches(november, manila(2026, 10, 31, 23, 59)),
        )
        assertTrue(
            "1 Nov 00:00 is inside November",
            DateFilterRanges.matches(november, manila(2026, 11, 1)),
        )
    }

    @Test
    fun month_endsExclusiveAtTheNextMonthStart() {
        assertFalse(
            "1 Dec 00:00 is not November",
            DateFilterRanges.matches(november, manila(2026, 12, 1)),
        )
        assertTrue(
            "30 Nov 23:59 is still November",
            DateFilterRanges.matches(november, manila(2026, 11, 30, 23, 59)),
        )
    }

    @Test
    fun day_coversItsOwnMidnightToTheNextMidnight() {
        val day = DiscoverDateFilter.DayOf(LocalDate.of(2026, 11, 12))
        assertTrue(DateFilterRanges.matches(day, manila(2026, 11, 12)))
        assertTrue(DateFilterRanges.matches(day, manila(2026, 11, 12, 23, 59)))
        assertFalse(DateFilterRanges.matches(day, manila(2026, 11, 13)))
        assertFalse(DateFilterRanges.matches(day, manila(2026, 11, 11, 23, 59)))
    }

    @Test
    fun month_includesALeapDay() {
        val february = DiscoverDateFilter.MonthOf(YearMonth.of(2028, 2))
        assertTrue(DateFilterRanges.matches(february, manila(2028, 2, 29, 12)))
        assertEquals(
            LocalDate.of(2028, 2, 29),
            DateFilterRanges.endExclusive(february).atZone(DateFilterRanges.ZONE).toLocalDate().minusDays(1),
        )
    }

    @Test
    fun december_window_endsAtTheNextYearStart() {
        val december = DiscoverDateFilter.MonthOf(YearMonth.of(2026, 12))
        assertTrue(DateFilterRanges.matches(december, manila(2026, 12, 31, 23, 59)))
        assertFalse(DateFilterRanges.matches(december, manila(2027, 1, 1)))
    }

    @Test
    fun nullStart_neverMatchesADateFilter() {
        assertFalse(DateFilterRanges.matches(november, null))
        assertFalse(
            DateFilterRanges.matches(DiscoverDateFilter.DayOf(LocalDate.of(2026, 11, 12)), null),
        )
    }

    /** A stale 31 is not a crash and not a date: Exact day stays un-applyable until it is valid. */
    @Test
    fun draft_dayOfAMonthWithoutThatDay_isNull() {
        assertNull(DateFilterDraft(DateFilterMode.MONTH, 2026, 2, 31).dayOrNull())
        assertEquals(
            LocalDate.of(2026, 2, 28),
            DateFilterDraft(DateFilterMode.MONTH, 2026, 2, 28).dayOrNull(),
        )
    }
}
