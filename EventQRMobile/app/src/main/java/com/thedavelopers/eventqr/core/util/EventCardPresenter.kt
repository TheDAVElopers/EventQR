package com.thedavelopers.eventqr.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class EventCardDateParts(
    val day: String,
    val month: String,
    val time: String,
)

object EventCardPresenter {

    const val UNKNOWN_DAY = "--"
    const val UNKNOWN_MONTH = "---"
    const val UNKNOWN_TIME = "-"
    const val UNKNOWN_LOCATION = "Location not set"

    val eventZone: ZoneId = ZoneId.of("Asia/Manila")

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    fun dateParts(startAt: Instant?): EventCardDateParts {
        val start = startAt ?: return EventCardDateParts(UNKNOWN_DAY, UNKNOWN_MONTH, UNKNOWN_TIME)
        val zoned = start.atZone(eventZone)
        return EventCardDateParts(
            day = zoned.dayOfMonth.toString(),
            month = zoned.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            time = zoned.format(timeFormatter),
        )
    }

    fun location(raw: String?): String = raw?.takeIf { it.isNotBlank() } ?: UNKNOWN_LOCATION

    fun capacity(raw: Int): Int = raw.coerceAtLeast(1)
}
