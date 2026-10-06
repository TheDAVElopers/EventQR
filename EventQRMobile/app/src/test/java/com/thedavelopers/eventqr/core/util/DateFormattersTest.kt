package com.thedavelopers.eventqr.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Locale

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
        assertEquals("May 16, 2026", DateFormatters.formatEventDate(instant))
    }

    @Test
    fun formatEventDate_ignoresTurkishLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        val instant = Instant.parse("2026-05-15T18:30:00Z")
        assertEquals("May 16, 2026", DateFormatters.formatEventDate(instant))
    }

    @Test
    fun formatInstant_ignoresTurkishLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        val instant = Instant.parse("2026-05-15T10:00:00Z")
        assertTrue(DateFormatters.formatInstant(instant).matches(Regex("^May 1[0-9], 2026 • .+$")))
    }
}
