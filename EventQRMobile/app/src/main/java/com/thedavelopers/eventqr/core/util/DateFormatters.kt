package com.thedavelopers.eventqr.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The single date/time vocabulary for the app (§7.6).
 *
 *     date, other years    d MMM yyyy         12 Oct 2026
 *     date, current year   d MMM              12 Oct
 *     time                 h:mm a             9:41 AM
 *     date and time        d MMM yyyy, h:mm a 12 Oct 2026, 9:41 AM
 *
 * Banned: numeric dates (`10/12/2026`), ISO dates (`2026-10-10`), two-digit years, and the old
 * `MMM d, yyyy • h:mm a` shape that this object used to emit. Event times are always shown in
 * event-local time (Asia/Manila), and the locale is fixed so a Turkish device cannot silently
 * rename a month.
 *
 * Date+time values are printed with the year even inside the current year: a timestamp without a
 * year is ambiguous in a transaction log that can span a year boundary.
 *
 * One date, and one clock, are each a single unbreakable unit: the patterns below use non-breaking
 * spaces (U+00A0) inside the unit, so a narrow row may wrap between the date, the time and the
 * range separator, never inside `11 Nov 2026` or `5:20 AM`. The rendered characters are unchanged
 * (NBSP is invisible); only the break opportunities move. Exact-string assertions therefore carry
 * `\u00A0`, not a plain space.
 */
object DateFormatters {

    private val ZONE: ZoneId = ZoneId.of("Asia/Manila")

    /** Date and time: `d MMM yyyy, h:mm a`; the space after the comma is breakable, the rest is not. */
    val displayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d\u00A0MMM\u00A0yyyy, h:mm\u00A0a", Locale.ENGLISH).withZone(ZONE)

    /** Date only: always carries the year, so a stored value never has to guess a boundary. */
    val eventDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d\u00A0MMM\u00A0yyyy", Locale.ENGLISH).withZone(ZONE)

    /** Date only, year omitted: `d MMM`. */
    val eventDateThisYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d\u00A0MMM", Locale.ENGLISH).withZone(ZONE)

    /** Time only: `h:mm a`; the meridiem never separates from the clock. */
    val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm\u00A0a", Locale.ENGLISH).withZone(ZONE)

    /** Month and year: `MMM yyyy` (`Nov 2026`). Used by the Discover date filter. */
    val monthYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM\u00A0yyyy", Locale.ENGLISH)

    /** `d MMM yyyy, h:mm a`; `--` when there is no value. */
    fun formatInstant(instant: Instant?): String =
        instant?.let { displayFormatter.format(it) } ?: PLACEHOLDER

    /** Date and time in Asia/Manila (event times are always shown in event-local time). */
    fun formatEventDateTime(instant: Instant?): String =
        instant?.let { displayFormatter.format(it) } ?: PLACEHOLDER

    /** Date only, year included: `d MMM yyyy`. */
    fun formatEventDate(instant: Instant?): String =
        instant?.let { eventDateFormatter.format(it) } ?: PLACEHOLDER

    /**
     * True when both instants land on the same calendar day in the one event zone, so a range can
     * print its date once (`13 Nov 2026, 12:20 AM – 12:20 PM`) instead of twice. Same zone as
     * [formatEventDate] / [formatTime]: a range must never mix two calendars.
     */
    fun isSameEventDay(start: Instant, end: Instant): Boolean =
        start.atZone(ZONE).toLocalDate() == end.atZone(ZONE).toLocalDate()

    /**
     * Date only with the §7.6 current-year rule: `12 Oct` inside the current year, `12 Oct 2026`
     * otherwise. [now] is injectable so the rule is testable without a clock.
     */
    fun formatEventDateShort(instant: Instant?, now: Instant = Instant.now()): String {
        if (instant == null) return PLACEHOLDER
        val date: LocalDate = instant.atZone(ZONE).toLocalDate()
        val thisYear = now.atZone(ZONE).year
        val formatter = if (date.year == thisYear) eventDateThisYearFormatter else eventDateFormatter
        return formatter.format(instant)
    }

    /** Time only: `h:mm a`. */
    fun formatTime(instant: Instant?): String =
        instant?.let { timeFormatter.format(it) } ?: PLACEHOLDER

    /** Month and year: `Nov 2026`. */
    fun formatMonthYear(month: YearMonth): String = monthYearFormatter.format(month)

    // ── Audit log (§6.5) ────────────────────────────────────────────────────────────────────────
    // Three calls, one audit zone. The adapter owns no formatter, no zone and no pattern string.

    /** Audit row time: absolute, `h:mm a`, in the audit zone. */
    fun auditTime(instant: Instant): String = timeFormatter.format(instant)

    /** Audit grouping key: the entry's date in the audit zone, so a near-midnight entry cannot land under the wrong header. */
    fun auditDay(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()

    /**
     * Audit day header: `Today`, `Yesterday`, `12 Oct`, and `12 Oct 2025` outside the current year.
     * Context-free by the §6.5 contract, so the two relative words are literals here; the header
     * style uppercases at render (§7.2).
     */
    fun auditDayLabel(day: LocalDate, today: LocalDate): String = when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> eventDateThisYearFormatter.format(day)
        else -> eventDateFormatter.format(day)
    }

    private const val PLACEHOLDER = "--"
}
