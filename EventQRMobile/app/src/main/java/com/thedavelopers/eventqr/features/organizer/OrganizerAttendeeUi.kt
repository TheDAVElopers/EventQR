package com.thedavelopers.eventqr.features.organizer

import android.content.Context
import com.thedavelopers.eventqr.R
import java.util.Locale

/**
 * Buckets come straight from the backend `currentEventStatus`: Registered / Checked In / Exited / Cancelled / No Show.
 * "Checked In" means exactly that; an attendee who already left is "Exited", never double counted as checked in.
 * Falls back to `registrationStatus` only when the server sent no current status.
 */
internal fun OrganizerMvpAttendee.statusBucket(): String {
    val current = currentEventStatus.trim()
    val registration = registrationStatus.trim()
    val source = current.ifBlank { registration }
    return when {
        source.equals("Checked In", ignoreCase = true) -> "Checked In"
        source.equals("Exited", ignoreCase = true) -> "Exited"
        source.equals("Cancelled", ignoreCase = true) || source.equals("Canceled", ignoreCase = true) -> "Cancelled"
        source.contains("no-show", ignoreCase = true) || source.contains("no show", ignoreCase = true) ||
            source.equals("NO_SHOW", ignoreCase = true) -> "No Show"
        source.equals("Registered", ignoreCase = true) -> "Registered"
        source.isNotBlank() -> source
        else -> "Registered"
    }
}

/** Attendees shown in the "Total" tile: only those the backend counts as registered (excludes Cancelled / No Show). */
internal fun List<OrganizerMvpAttendee>.registeredTotal(): Int = count { it.countedAsRegistered }

internal fun List<OrganizerMvpAttendee>.cancelledTotal(): Int = count { it.statusBucket() == "Cancelled" }

internal fun List<OrganizerMvpAttendee>.checkedInTotal(): Int = count { it.statusBucket() == "Checked In" }

internal fun OrganizerMvpAttendee.statusPalette(context: Context): Pair<Int, Int> {
    val (bgRes, textRes) = when (statusBucket()) {
        "Checked In" -> R.color.eventqr_badge_entered_bg to R.color.eventqr_badge_entered_text
        "Exited" -> R.color.eventqr_badge_default_bg to R.color.eventqr_badge_default_text
        "No Show" -> R.color.eventqr_badge_pending_bg to R.color.eventqr_badge_pending_text
        "Cancelled" -> R.color.eventqr_badge_cancelled_bg to R.color.eventqr_badge_cancelled_text
        else -> R.color.eventqr_badge_registered_bg to R.color.eventqr_badge_registered_text
    }
    return context.getColor(bgRes) to context.getColor(textRes)
}

internal fun transactionTypeLabel(value: String): String = when (value.trim().uppercase(Locale.ENGLISH)) {
    "ENTRY" -> "Entry"
    "ATTENDANCE" -> "Attendance"
    "BENEFIT_CLAIM" -> "Benefit Claim"
    "BOOTH_VISIT", "SESSION_VISIT" -> "Booth/Session Visit"
    "REWARD_REDEMPTION_SCAN", "REWARD_REDEMPTION" -> "Reward Redemption"
    "EXIT" -> "Exit"
    "ID_PRINT" -> "ID Printing"
    "REGISTRATION", "REGISTRATION_LOOKUP" -> "Registration"
    else -> value
}

internal fun OrganizerMvpAttendee.matchesOrganizerAttendeeQuery(query: String, filter: String): Boolean {
    val normalizedQuery = query.trim()
    val matchesFilter = when (filter) {
        "All" -> true
        "Registered" -> statusBucket().equals("Registered", ignoreCase = true)
        "Checked In" -> statusBucket().equals("Checked In", ignoreCase = true)
        "Exited" -> statusBucket().equals("Exited", ignoreCase = true)
        "No Show" -> statusBucket().equals("No Show", ignoreCase = true)
        else -> statusBucket().equals(filter, ignoreCase = true) || registrationStatus.equals(filter, ignoreCase = true)
    }
    if (!matchesFilter) return false
    if (normalizedQuery.isBlank()) return true
    return name.contains(normalizedQuery, ignoreCase = true) ||
        email.contains(normalizedQuery, ignoreCase = true) ||
        phone.contains(normalizedQuery, ignoreCase = true) ||
        id.contains(normalizedQuery, ignoreCase = true) ||
        registrationStatus.contains(normalizedQuery, ignoreCase = true) ||
        currentEventStatus.contains(normalizedQuery, ignoreCase = true) ||
        points.toString().contains(normalizedQuery, ignoreCase = true)
}

internal fun attendeeInitial(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

internal fun organizerEventDateLine(shortDate: String, eventTitle: String, venue: String): String {
    val rawDate = shortDate.trim()
    return rawDate
        .replace(Regex("T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z?"), "")
        .replace(Regex("\\s*·.*$"), "")
        .trim()
        .ifBlank { eventTitle.trim() }
}
