package com.thedavelopers.eventqr.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object RelativeTimeUtils {

    private val fullFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMM d, yyyy • h:mm a", Locale.ENGLISH)
        .withZone(ZoneId.of("Asia/Manila"))

    fun formatRelative(instant: Instant?): String {
        if (instant == null) return "--"
        val now = Instant.now()
        val diffMs = TimeUnit.SECONDS.toMillis(now.epochSecond - instant.epochSecond)
        return when {
            diffMs < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diffMs < TimeUnit.HOURS.toMillis(1) -> "${diffMs / TimeUnit.MINUTES.toMillis(1)}m ago"
            diffMs < TimeUnit.DAYS.toMillis(1) -> "${diffMs / TimeUnit.HOURS.toMillis(1)}h ago"
            diffMs < TimeUnit.DAYS.toMillis(7) -> "${diffMs / TimeUnit.DAYS.toMillis(1)}d ago"
            else -> fullFormatter.format(instant)
        }
    }

    fun formatRelativeOrDash(instant: Instant?): String =
        if (instant == null) "--" else formatRelative(instant)

    fun formatFull(instant: Instant?): String {
        if (instant == null) return "--"
        return fullFormatter.format(instant)
    }
}
