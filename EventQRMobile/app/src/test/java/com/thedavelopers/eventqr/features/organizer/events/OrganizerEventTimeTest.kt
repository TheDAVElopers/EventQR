package com.thedavelopers.eventqr.features.organizer.events

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.format.formatEventTime
import com.thedavelopers.eventqr.core.format.formatRegistrationWindow
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * §6.4 EVENT TIME: one row, one string. The same-day collapse is decided on the event zone
 * (Asia/Manila, the zone every other formatter in the app reads), never the device's or UTC's
 * calendar, so a near-midnight range cannot print a date that disagrees with its own times.
 *
 * Instants below are UTC; Manila is UTC+8.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OrganizerEventTimeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Manila 13 Nov 2026 00:20 -> prints `12:20 AM`. */
    private val start = Instant.parse("2026-11-12T16:20:00Z")

    @Test
    fun sameEventDay_printsTheDateOnce() {
        val end = Instant.parse("2026-11-13T04:20:00Z") // Manila 12:20\u00A0PM

        assertEquals(
            "13\u00A0Nov\u00A02026, 12:20\u00A0AM \u2013\u00A012:20\u00A0PM",
            formatEventTime(context, start, end),
        )
    }

    @Test
    fun differentEventDays_keepsBothFullDatetimes() {
        val end = Instant.parse("2026-11-13T17:00:00Z") // Manila 14\u00A0Nov 1:00\u00A0AM

        assertEquals(
            "13\u00A0Nov\u00A02026, 12:20\u00A0AM\n\u2013\u00A014\u00A0Nov\u00A02026, 1:00\u00A0AM",
            formatEventTime(context, start, end),
        )
    }

    /** Same UTC day, different Manila days: the day test must follow the event zone. */
    @Test
    fun nearMidnightAcrossTheEventZone_doesNotCollapse() {
        val beforeMidnight = Instant.parse("2026-11-13T15:30:00Z") // Manila 13\u00A0Nov 11:30\u00A0PM
        val afterMidnight = Instant.parse("2026-11-13T16:10:00Z") // Manila 14\u00A0Nov 12:10\u00A0AM

        assertEquals(
            "13\u00A0Nov\u00A02026, 11:30\u00A0PM\n\u2013\u00A014\u00A0Nov\u00A02026, 12:10\u00A0AM",
            formatEventTime(context, beforeMidnight, afterMidnight),
        )
    }

    /**
     * A multi-day range breaks in one deterministic place on both rows: after the start datetime, so
     * "– end" opens line 2. The break never depends on the label width, which is what made EVENT TIME
     * and REGISTRATION WINDOW wrap differently.
     */
    @Test
    fun multiDayRange_breaksOnceAfterTheStartOnBothRows() {
        val eventEnd = Instant.parse("2026-11-14T09:00:00Z") // Manila 14\u00A0Nov 5:00\u00A0PM

        listOf(
            "EVENT TIME" to formatEventTime(context, start, eventEnd),
            "REGISTRATION WINDOW" to formatRegistrationWindow(context, windowOpen, windowClose),
        ).forEach { (row, value) ->
            val lines = requireNotNull(value).split("\n")
            assertEquals("$row breaks exactly once", 2, lines.size)
            assertTrue("$row line 2 opens with the dash", lines[1].startsWith("\u2013\u00A0"))
        }
    }

    @Test
    fun noEnd_printsTheStartAlone() {
        assertEquals("13\u00A0Nov\u00A02026, 12:20\u00A0AM", formatEventTime(context, start, null))
    }

    @Test
    fun noStart_hidesTheRow() {
        assertNull(formatEventTime(context, null, start))
    }

    // ── REGISTRATION WINDOW: the same literal shape the attendee and organizer tables print ──────

    /** Manila 1 May 2026 9:00 AM. */
    private val windowOpen = Instant.parse("2026-05-01T01:00:00Z")

    /** Manila 1 Jun 2026 11:59 PM. */
    private val windowClose = Instant.parse("2026-06-01T15:59:00Z")

    @Test
    fun registrationWindow_printsBothEndsWithYearAndClock() {
        assertEquals(
            "1\u00A0May\u00A02026, 9:00\u00A0AM\n\u2013\u00A01\u00A0Jun\u00A02026, 11:59\u00A0PM",
            formatRegistrationWindow(context, windowOpen, windowClose),
        )
    }

    @Test
    fun registrationWindow_singleEndPrintsAlone() {
        assertEquals(
            "1\u00A0Jun\u00A02026, 11:59\u00A0PM",
            formatRegistrationWindow(context, null, windowClose),
        )
        assertEquals(
            "1\u00A0May\u00A02026, 9:00\u00A0AM",
            formatRegistrationWindow(context, windowOpen, null),
        )
    }

    @Test
    fun registrationWindow_noEnds_hidesTheRow() {
        assertNull(formatRegistrationWindow(context, null, null))
    }
}
