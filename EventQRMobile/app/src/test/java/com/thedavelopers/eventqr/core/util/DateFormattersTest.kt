package com.thedavelopers.eventqr.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Locale

/**
 * §7.6 date/time contract. The old suite asserted `MMM d, yyyy • h:mm a`; that shape is banned, so
 * these assertions are the specification now.
 */
class DateFormattersTest {

    private lateinit var defaultLocale: Locale

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun formatEventDate_nullReturnsPlaceholder() {
        assertEquals("--", DateFormatters.formatEventDate(null))
    }

    @Test
    fun formatEventDate_usesManilaCalendarDay() {
        val instant = Instant.parse("2026-05-15T18:30:00Z")
        assertEquals("16 May 2026", DateFormatters.formatEventDate(instant))
    }

    @Test
    fun formatEventDate_ignoresTurkishLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        val instant = Instant.parse("2026-05-15T18:30:00Z")
        assertEquals("16 May 2026", DateFormatters.formatEventDate(instant))
    }

    @Test
    fun formatInstant_usesDateTimeShapeWithComma() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        val instant = Instant.parse("2026-05-15T10:00:00Z")
        assertEquals("15 May 2026, 6:00 PM", DateFormatters.formatInstant(instant))
    }

    @Test
    fun formatInstant_nullReturnsPlaceholder() {
        assertEquals("--", DateFormatters.formatInstant(null))
    }

    @Test
    fun formatTime_usesTwelveHourClockWithSpaceBeforeMeridiem() {
        val instant = Instant.parse("2026-05-15T10:00:00Z")
        assertEquals("6:00 PM", DateFormatters.formatTime(instant))
    }

    @Test
    fun formatEventDateShort_omitsTheYearInsideTheCurrentYear() {
        val instant = Instant.parse("2026-05-15T10:00:00Z")
        val now = Instant.parse("2026-10-01T00:00:00Z")
        assertEquals("15 May", DateFormatters.formatEventDateShort(instant, now))
    }

    @Test
    fun formatEventDateShort_keepsTheYearOutsideTheCurrentYear() {
        val instant = Instant.parse("2025-12-31T10:00:00Z")
        val now = Instant.parse("2026-01-02T00:00:00Z")
        assertEquals("31 Dec 2025", DateFormatters.formatEventDateShort(instant, now))
    }

    @Test
    fun formatEventDateShort_nullReturnsPlaceholder() {
        assertEquals("--", DateFormatters.formatEventDateShort(null))
    }
}
