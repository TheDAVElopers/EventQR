package com.thedavelopers.eventqr.features.organizer

data class OrganizerMvpAttendee(
    val id: String,
    val eventId: String,
    val name: String,
    val email: String,
    val phone: String,
    val registrationStatus: String,
    val currentEventStatus: String,
    val points: Int,
    val lastTransactionTime: String,
    val registeredDate: String,
    val qrCredentialStatus: String,
    val recentTransactions: List<String>,
    val recentRejectedScans: List<String>,
)
