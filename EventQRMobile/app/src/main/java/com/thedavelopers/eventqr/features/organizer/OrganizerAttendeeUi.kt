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
        source.equals("Checked In", ignoreCase = true) || source.equals("ENTERED", ignoreCase = true) -> "Checked In"
        source.equals("Exited", ignoreCase = true) || source.equals("EXITED", ignoreCase = true) -> "Exited"
        source.equals("Cancelled", ignoreCase = true) || source.equals("Canceled", ignoreCase = true) ||
            source.equals("CANCELLED", ignoreCase = true) -> "Cancelled"
        source.contains("no-show", ignoreCase = true) || source.contains("no show", ignoreCase = true) ||
            source.equals("NO_SHOW", ignoreCase = true) -> "No Show"
        source.equals("Registered", ignoreCase = true) || source.equals("REGISTERED", ignoreCase = true) -> "Registered"
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

internal fun OrganizerMvpAttendee.statusLabel(context: Context): String = when (statusBucket().trim().uppercase(Locale.ENGLISH)) {
    "CHECKED IN", "ENTERED" -> context.getString(R.string.common_checked_in)
    "EXITED" -> context.getString(R.string.search_attendees_exited)
    "CANCELLED", "CANCELED" -> context.getString(R.string.search_attendees_cancelled)
    "NO SHOW", "NO_SHOW" -> context.getString(R.string.common_no_show)
    "REGISTERED" -> context.getString(R.string.common_registered)
    else -> statusBucket()
}

internal fun transactionTypeLabel(context: Context, value: String): String = when (value.trim().uppercase(Locale.ENGLISH)) {
    "ENTRY" -> context.getString(R.string.txn_type_entry)
    "ATTENDANCE" -> context.getString(R.string.txn_type_attendance)
    "BENEFIT_CLAIM" -> context.getString(R.string.txn_type_benefit_claim)
    "BOOTH_VISIT", "SESSION_VISIT" -> context.getString(R.string.txn_type_booth_session_visit)
    "REWARD_REDEMPTION_SCAN", "REWARD_REDEMPTION" -> context.getString(R.string.txn_type_reward_redemption)
    "EXIT" -> context.getString(R.string.txn_type_exit)
    "ID_PRINT" -> context.getString(R.string.txn_type_id_printing)
    "REGISTRATION", "REGISTRATION_LOOKUP" -> context.getString(R.string.txn_type_registration)
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
        "Cancelled" -> statusBucket().equals("Cancelled", ignoreCase = true)
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
