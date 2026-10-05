package com.thedavelopers.eventqr.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class RelativeTimeUtilsTest {

    @Test
    fun formatRelative_nullReturnsPlaceholder() {
        assertEquals("--", RelativeTimeUtils.formatRelative(null))
    }

    @Test
    fun formatRelative_secondsAgo_returnsJustNow() {
        val instant = Instant.now().minus(10, ChronoUnit.SECONDS)
        assertEquals("just now", RelativeTimeUtils.formatRelative(instant))
    }

    @Test
    fun formatRelative_minutesAgo_returnsMinutes() {
        val instant = Instant.now().minus(15, ChronoUnit.MINUTES)
        assertEquals("15m ago", RelativeTimeUtils.formatRelative(instant))
    }

    @Test
    fun formatRelative_hoursAgo_returnsHours() {
        val instant = Instant.now().minus(3, ChronoUnit.HOURS)
        assertEquals("3h ago", RelativeTimeUtils.formatRelative(instant))
    }

    @Test
    fun formatRelative_daysAgo_returnsDays() {
        val instant = Instant.now().minus(4, ChronoUnit.DAYS)
        assertEquals("4d ago", RelativeTimeUtils.formatRelative(instant))
    }

    @Test
    fun formatRelative_olderThanWeek_returnsFormattedDate() {
        val instant = Instant.now().minus(30, ChronoUnit.DAYS)
        val formatted = RelativeTimeUtils.formatRelative(instant)
        assertTrue(formatted.contains("•") || formatted.contains(","))
    }

    @Test
    fun formatFull_nullReturnsPlaceholder() {
        assertEquals("--", RelativeTimeUtils.formatFull(null))
    }

    @Test
    fun formatFull_validInstant_returnsPattern() {
        val instant = Instant.parse("2026-05-15T10:00:00Z")
        val formatted = RelativeTimeUtils.formatFull(instant)
        assertTrue(formatted.contains("2026"))
        assertTrue(formatted.contains("•"))
    }
}
