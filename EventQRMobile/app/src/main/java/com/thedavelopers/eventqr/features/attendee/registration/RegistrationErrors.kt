package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
fun toFriendlyRegistrationError(
    message: String,
    strings: UiStrings,
    ownEmailMessage: String = strings.get(R.string.registration_own_email_required),
): String {
    val normalized = message.lowercase()
    return when {
        normalized.contains("own account email") -> ownEmailMessage
        normalized.contains("duplicate registration") || normalized.contains("already registered") || normalized.contains("duplicate key") || normalized.contains("unique constraint") -> strings.get(R.string.registration_error_already_registered)
        normalized.contains("event is at capacity") || normalized.contains("capacity") || normalized.contains("full") -> strings.get(R.string.registration_error_event_full)
        normalized.contains("registration is closed") || normalized.contains("registration closed") || normalized.contains("closed") -> strings.get(R.string.registration_error_closed)
        normalized.contains("event is not open for registration") || normalized.contains("not open for registration") -> strings.get(R.string.registration_error_not_open)
        normalized.contains("event is not active") || normalized.contains("not active") -> strings.get(R.string.registration_error_event_not_active)
        normalized.contains("unauthorized") || normalized.contains("forbidden") -> strings.get(R.string.registration_error_no_permission)
        normalized.contains("not found") -> strings.get(R.string.registration_error_event_not_found)
        normalized.contains("could not execute statement") || normalized.contains("foreign key") || normalized.contains("sql") || normalized.contains("database") || normalized.contains("statement") || normalized.contains("jpa") -> strings.get(R.string.registration_error_failed)
        normalized.contains("unable to resolve host") || normalized.contains("failed to connect") || normalized.contains("timeout") -> strings.get(R.string.registration_error_network)
        else -> strings.get(R.string.registration_error_failed)
    }
}
