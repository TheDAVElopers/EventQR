package com.thedavelopers.eventqr.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormatters {

    val displayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"))

    val eventDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"))

    fun formatInstant(instant: Instant?): String =
        instant?.let { displayFormatter.format(it) } ?: "--"

    /** Date and time in Asia/Manila (event times are always shown in event-local time). */
    fun formatEventDateTime(instant: Instant?): String =
        instant?.let { displayFormatter.format(it) } ?: "--"

    fun formatEventDate(instant: Instant?): String =
        instant?.let { eventDateFormatter.format(it) } ?: "--"
}
