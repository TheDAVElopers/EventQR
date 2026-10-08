package com.thedavelopers.eventqr.features.organizer

data class OrganizerMvpTransactionEntry(
    val type: String,
    val timestamp: String? = null,
)

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
    val recentTransactions: List<OrganizerMvpTransactionEntry>,
    val recentRejectedScans: List<String>,
    /** Backend `countedAsRegistered`: false for Cancelled / No Show registrations. */
    val countedAsRegistered: Boolean = true,
)
