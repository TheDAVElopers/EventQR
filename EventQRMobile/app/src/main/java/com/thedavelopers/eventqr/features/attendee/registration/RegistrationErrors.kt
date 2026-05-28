package com.thedavelopers.eventqr.features.attendee

fun toFriendlyRegistrationError(message: String): String {
    val normalized = message.lowercase()
    return when {
        normalized.contains("duplicate registration") || normalized.contains("already registered") || normalized.contains("duplicate key") || normalized.contains("unique constraint") -> "You are already registered for this event."
        normalized.contains("event is at capacity") || normalized.contains("capacity") || normalized.contains("full") -> "This event is full."
        normalized.contains("registration is closed") || normalized.contains("registration closed") || normalized.contains("closed") -> "Registration is closed."
        normalized.contains("event is not active") || normalized.contains("not active") -> "This event is not active."
        normalized.contains("unauthorized") || normalized.contains("forbidden") -> "You do not have permission to register for this event."
        normalized.contains("not found") -> "Event not found."
        normalized.contains("could not execute statement") || normalized.contains("foreign key") || normalized.contains("sql") || normalized.contains("database") || normalized.contains("statement") || normalized.contains("jpa") -> "Registration failed. Please try again."
        normalized.contains("unable to resolve host") || normalized.contains("failed to connect") || normalized.contains("timeout") -> "Network error. Please check your connection and try again."
        else -> "Registration failed. Please try again."
    }
}
