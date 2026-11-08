package com.thedavelopers.eventqr.features.attendee

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The optional date filter on Discover (§6.2 amendment). It ANDs with the required status chip and
 * the search text; it never replaces either, and it never re-sorts the list.
 *
 * Event times are stored as instants and read in Asia/Manila, so both ranges are half-open on the
 * event-local calendar: a month is `[1st 00:00, 1st of next month 00:00)`, a day is
 * `[00:00, next day 00:00)`. An event with no start instant matches no date filter - it cannot be
 * proven to fall inside the chosen window.
 */
enum class DateFilterMode { ANY, MONTH, DAY }

sealed interface DiscoverDateFilter {
    data class MonthOf(val month: YearMonth) : DiscoverDateFilter
    data class DayOf(val day: LocalDate) : DiscoverDateFilter
}

/**
 * The sheet's uncommitted state. It seeds from the applied filter, or from Any date + the current
 * Manila month. [day] is an int so a month change can leave a stale 31 that simply stops being a
 * valid day ([dayOrNull]) instead of throwing.
 *
 * Use [current] for a fresh draft: it reads the clock once, so the default year and month cannot
 * straddle a month boundary.
 */
data class DateFilterDraft(
    val mode: DateFilterMode,
    val year: Int,
    val month: Int,
    val day: Int? = null,
) {
    companion object {
        /** Any date (or [mode]) in the current Manila month, from a single clock read. */
        fun current(mode: DateFilterMode = DateFilterMode.ANY, day: Int? = null): DateFilterDraft {
            val now = YearMonth.now(DateFilterRanges.ZONE)
            return DateFilterDraft(mode, now.year, now.monthValue, day)
        }
    }
}

/** The draft's day as a real date, or null when the month has no such day. */
fun DateFilterDraft.dayOrNull(): LocalDate? =
    day?.let { runCatching { LocalDate.of(year, month, it) }.getOrNull() }

object DateFilterRanges {

    val ZONE: ZoneId = ZoneId.of("Asia/Manila")

    fun start(filter: DiscoverDateFilter): Instant = when (filter) {
        is DiscoverDateFilter.MonthOf -> filter.month.atDay(1).atStartOfDay(ZONE).toInstant()
        is DiscoverDateFilter.DayOf -> filter.day.atStartOfDay(ZONE).toInstant()
    }

    fun endExclusive(filter: DiscoverDateFilter): Instant = when (filter) {
        is DiscoverDateFilter.MonthOf ->
            filter.month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant()
        is DiscoverDateFilter.DayOf -> filter.day.plusDays(1).atStartOfDay(ZONE).toInstant()
    }

    /** Half-open `[start, endExclusive)` on the Manila calendar; a null start matches nothing. */
    fun matches(filter: DiscoverDateFilter, startAt: Instant?): Boolean {
        if (startAt == null) return false
        return !startAt.isBefore(start(filter)) && startAt.isBefore(endExclusive(filter))
    }
}
