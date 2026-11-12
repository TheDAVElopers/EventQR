package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.DateFormatters
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * §6.2 registration window: a compact range. A window whose ends fall in different years prints
 * the year on both ends, so a past deadline can never read as a current one; otherwise the year
 * appears once, on the closing end, and only outside the current Manila year.
 */
internal fun formatRegistrationWindow(
    openAt: Instant?,
    closeAt: Instant?,
    now: Instant,
    zone: ZoneId,
    rangeFormat: String,
    opensPrefix: String,
    closesPrefix: String,
): String? {
    val currentYear = now.atZone(zone).year

    if (openAt != null && closeAt != null) {
        val open = openAt.atZone(zone)
        val close = closeAt.atZone(zone)
        return if (open.toLocalDate() == close.toLocalDate()) {
            // Same-day windows keep both clocks at any distance: the date alone would repeat.
            val date = dayMonth(open)
            val year = if (open.year != currentYear) " ${open.year}" else ""
            String.format(
                Locale.ENGLISH,
                rangeFormat,
                "$date$year, ${clock(open)}",
                clock(close),
            )
        } else {
            // Different years: both ends name their year. Same year: the year appears once on the
            // closing end, and only when that year is not the current Manila year.
            val spansYears = open.year != close.year
            val start = dayMonth(open) +
                (if (spansYears) " ${open.year}" else "") +
                timeSuffix(openAt, now, zone)
            val end = dayMonth(close) +
                (if (spansYears || close.year != currentYear) " ${close.year}" else "") +
                timeSuffix(closeAt, now, zone)
            String.format(Locale.ENGLISH, rangeFormat, start, end)
        }
    }

    val singleAt = openAt ?: closeAt ?: return null
    val local = singleAt.atZone(zone)
    val arg = dayMonth(local) +
        (if (local.year != currentYear) " ${local.year}" else "") +
        timeSuffix(singleAt, now, zone)
    return String.format(Locale.ENGLISH, if (openAt != null) opensPrefix else closesPrefix, arg)
}

// The app vocabulary (§7.6), fed a zone-resolved value so the injected zone stays authoritative.
private fun dayMonth(at: ZonedDateTime): String =
    DateFormatters.eventDateThisYearFormatter.format(at.toLocalDate())

private fun clock(at: ZonedDateTime): String =
    DateFormatters.timeFormatter.format(at.toLocalTime())

// The exact hour matters only inside the next 24 hours; a past end carries no time. Beyond 24h
// STATUS already says open, closed or not yet open, so the hour is not decision-relevant.
private fun timeSuffix(at: Instant, now: Instant, zone: ZoneId): String {
    val remaining = Duration.between(now, at)
    val withinNextDay = !remaining.isNegative && !remaining.isZero && remaining < Duration.ofHours(24)
    return if (withinNextDay) ", ${clock(at.atZone(zone))}" else ""
}
