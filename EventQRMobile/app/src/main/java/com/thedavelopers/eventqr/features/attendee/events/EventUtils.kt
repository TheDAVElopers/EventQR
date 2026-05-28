package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import java.time.Instant

fun computedStatusLabel(item: AttendeeEventResponse): String {
    val now = Instant.now()
    return when {
        item.eventEndAt?.isBefore(now) == true -> "Completed"
        item.eventStartAt?.isAfter(now) == true -> "Upcoming"
        item.eventStartAt != null && item.eventEndAt != null &&
            !item.eventStartAt.isAfter(now) && !item.eventEndAt.isBefore(now) -> "Ongoing"
        else -> "Scheduled"
    }
}
