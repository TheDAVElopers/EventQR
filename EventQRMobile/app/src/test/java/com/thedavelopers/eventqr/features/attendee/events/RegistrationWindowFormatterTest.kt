package com.thedavelopers.eventqr.features.attendee

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * §6.2 registration window contract: compact range, a year-spanning window names both years,
 * times only inside the next 24 hours.
 */
class RegistrationWindowFormatterTest {

    private val zone = ZoneId.of("Asia/Manila")
    private val now = Instant.parse("2026-11-12T02:00:00Z") // 12 Nov 2026, 10:00 AM in Manila

    private fun format(open: String?, close: String?, at: Instant = now): String? = formatRegistrationWindow(
        openAt = open?.let(Instant::parse),
        closeAt = close?.let(Instant::parse),
        now = at,
        zone = zone,
        rangeFormat = "%1\$s – %2\$s",
        opensPrefix = "Opens %1\$s",
        closesPrefix = "Closes %1\$s",
    )

    @Test
    fun currentYearRange_hasNoYear() {
        assertEquals(
            "10 Dec – 11 Dec",
            format("2026-12-10T01:00:00Z", "2026-12-11T09:00:00Z"),
        )
    }

    @Test
    fun nextYearRange_hasYearOnceOnTheClosingEnd() {
        assertEquals(
            "10 Jan – 12 Jan 2027",
            format("2027-01-10T01:00:00Z", "2027-01-12T09:00:00Z"),
        )
    }

    @Test
    fun yearSpanningRange_hasYearOnBothEnds() {
        assertEquals(
            "30 Dec 2026 – 2 Jan 2027",
            format("2026-12-30T01:00:00Z", "2027-01-02T09:00:00Z"),
        )
    }

    @Test
    fun yearSpanningRangeViewedInsideTheClosingYear_keepsBothYears() {
        // 5 Jan 2027 in Manila. The closing year is the current year, so the buggy build printed
        // "28 Dec 2026 – 2 Jan", which reads as a deadline a year in the past.
        assertEquals(
            "28 Dec 2026 – 2 Jan 2027",
            format(
                "2026-12-28T01:00:00Z",
                "2027-01-02T09:00:00Z",
                at = Instant.parse("2027-01-05T02:00:00Z"),
            ),
        )
    }

    @Test
    fun sameDayRange_carriesTimesInsteadOfARepeatedDate() {
        assertEquals(
            "12 Nov, 9:00 AM – 5:00 PM",
            format("2026-11-12T01:00:00Z", "2026-11-12T09:00:00Z"),
        )
    }

    @Test
    fun closeOnly_readsCloses() {
        assertEquals("Closes 11 Dec", format(null, "2026-12-11T09:00:00Z"))
    }

    // The year rule lives on all four sites, not only the range and the open-only label.
    @Test
    fun closeOnlyOutsideTheCurrentYear_readsTheYear() {
        assertEquals("Closes 11 Dec 2027", format(null, "2027-12-11T09:00:00Z"))
    }

    @Test
    fun sameDayRangeOutsideTheCurrentYear_readsTheYearOnce() {
        assertEquals(
            "10 Dec 2027, 9:00 AM – 5:00 PM",
            format("2027-12-10T01:00:00Z", "2027-12-10T09:00:00Z"),
        )
    }

    // The same-day rule ignores the 24h clock window: the date alone would repeat.
    @Test
    fun sameDayRangeMoreThanADayAway_stillPrintsBothClocks() {
        assertEquals(
            "20 Nov, 9:00 AM – 5:00 PM",
            format("2026-11-20T01:00:00Z", "2026-11-20T09:00:00Z"),
        )
    }

    @Test
    fun openOnly_readsOpens() {
        assertEquals("Opens 10 Dec", format("2026-12-10T01:00:00Z", null))
    }

    @Test
    fun bothNull_returnsNull() {
        assertNull(format(null, null))
    }

    @Test
    fun closeWithinLastDay_getsItsTime() {
        // 13 Nov 2026, 7:00 AM in Manila: 21 hours away.
        assertEquals("Closes 13 Nov, 7:00 AM", format(null, "2026-11-12T23:00:00Z"))
    }

    @Test
    fun closeThirtyEightHoursAway_hasNoTime() {
        // 14 Nov 2026, 12:00 AM in Manila: 38 hours away.
        assertEquals("Closes 14 Nov", format(null, "2026-11-13T16:00:00Z"))
    }

    @Test
    fun rangeOpenWithinLastDay_getsItsTime() {
        // 13 Nov 2026, 9:00 AM in Manila: 23 hours away.
        assertEquals(
            "13 Nov, 9:00 AM – 11 Dec",
            format("2026-11-13T01:00:00Z", "2026-12-11T09:00:00Z"),
        )
    }

    @Test
    fun rangeCloseWithinLastDay_getsItsTime() {
        // 12 Nov 2026, 9:00 AM (past) to 13 Nov 2026, 7:00 AM in Manila: 21 hours away.
        assertEquals(
            "12 Nov – 13 Nov, 7:00 AM",
            format("2026-11-12T01:00:00Z", "2026-11-12T23:00:00Z"),
        )
    }

    @Test
    fun closeExactlyTwentyFourHoursAway_hasNoTime() {
        // 13 Nov 2026, 10:00 AM in Manila: exactly 24 hours away; the contract is less than 24.
        assertEquals("Closes 13 Nov", format(null, "2026-11-13T02:00:00Z"))
    }

    @Test
    fun pastClose_hasNoTime() {
        // 12 Nov 2026, 9:00 AM in Manila: one hour in the past.
        assertEquals("Closes 12 Nov", format(null, "2026-11-12T01:00:00Z"))
    }
}
