package com.thedavelopers.eventqr.features.organizer

data class OrganizerMvpTransaction(
    val id: String,
    val eventId: String,
    val eventTitle: String,
    val attendeeId: String,
    val attendeeName: String,
    val qrId: String,
    val staffId: String,
    val staffName: String,
    val scanPurpose: String,
    val type: String,
    val timestamp: String,
    val status: String,
    val message: String,
    val reason: String,
    val deviceSource: String,
    val pointsDelta: Int,
    val relatedItem: String,
)
