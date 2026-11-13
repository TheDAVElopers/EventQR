package com.thedavelopers.eventqr.core.format

import android.content.Context
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import java.time.Instant

/**
 * The `EVENT TIME` value (§6.2, §6.4), one row, one string, shared by the attendee event detail and
 * the organizer event details so the two screens can never print the same range differently.
 *
 *  * same calendar day: the date prints once, `13 Nov 2026, 12:20 AM – 12:20 PM`
 *  * different days: both full datetimes, joined by `event_detail_date_range`, which breaks after the
 *    start so `– end` opens line 2
 *  * no end: the start alone; no start: null, so the caller hides the row and its hairline
 *
 * The same-day test and both formatters read the one event zone inside [DateFormatters], so a
 * near-midnight event can never print a date that disagrees with its own times.
 */
internal fun formatEventTime(context: Context, start: Instant?, end: Instant?): String? {
    start ?: return null
    if (end == null) return DateFormatters.formatEventDateTime(start)
    if (DateFormatters.isSameEventDay(start, end)) {
        return context.getString(
            R.string.ui_organizer_event_time_same_day,
            DateFormatters.formatEventDate(start),
            DateFormatters.formatTime(start),
            DateFormatters.formatTime(end),
        )
    }
    return context.getString(
        R.string.event_detail_date_range,
        DateFormatters.formatEventDateTime(start),
        DateFormatters.formatEventDateTime(end),
    )
}

/**
 * The `REGISTRATION WINDOW` value (§6.2, §6.4): both ends as full datetimes, year and clock
 * included, joined by `event_detail_date_range` (start on line 1, `– end` on line 2). One end alone
 * prints on its own; neither end
 * returns null, so the caller hides the row and its hairline. Shared by the attendee event detail
 * and the organizer event details.
 */
internal fun formatRegistrationWindow(context: Context, openAt: Instant?, closeAt: Instant?): String? {
    val open = openAt?.let { DateFormatters.displayFormatter.format(it) }
    val close = closeAt?.let { DateFormatters.displayFormatter.format(it) }
    return when {
        open != null && close != null -> context.getString(R.string.event_detail_date_range, open, close)
        else -> open ?: close
    }
}
