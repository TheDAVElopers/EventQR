package com.thedavelopers.eventqr.features.attendee

fun toFriendlyQrError(message: String): String {
    val normalized = message.lowercase()
    return when {
        normalized.contains("qr credential not found") || normalized.contains("credential missing") || normalized.contains("registration not found") -> "QR credential missing. Please try again."
        normalized.contains("unauthorized") || normalized.contains("forbidden") -> "You do not have permission to view this QR credential."
        normalized.contains("not found") -> "QR credential missing. Please try again."
        normalized.contains("unable to resolve host") || normalized.contains("failed to connect") || normalized.contains("timeout") -> "Network error. Please check your connection and try again."
        normalized.contains("database") || normalized.contains("sql") || normalized.contains("jpa") || normalized.contains("statement") -> "Could not load QR credential. Please try again."
        else -> "Could not load QR credential. Please try again."
    }
}
