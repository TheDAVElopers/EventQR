package com.thedavelopers.eventqr.features.organizer

data class OrganizerMvpStaff(
    val id: String,
    val name: String,
    val email: String,
    val assignedEventId: String,
    val assignedEvent: String,
    val roleLabel: String,
    val accessStatus: String,
    val addedDate: String,
    val permissions: List<String>,
)
