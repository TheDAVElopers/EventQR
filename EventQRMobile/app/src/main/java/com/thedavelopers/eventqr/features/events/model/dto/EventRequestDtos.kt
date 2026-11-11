package com.thedavelopers.eventqr.features.events.model.dto

import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import java.time.Instant
import java.util.UUID

data class EventCreationRequestDto(
    val eventName: String,
    val eventDescription: String,
    val eventCategory: String,
    val targetAudience: String? = null,
    val capacity: Int,
    val venue: String,
    val startDateTime: String,
    val endDateTime: String,
    val registrationStartDateTime: String? = null,
    val registrationEndDateTime: String? = null,
    val requesterName: String,
    val contactEmail: String,
    val contactNumber: String,
    val requestedFeatures: List<String>? = null,
    val eventLogoUrl: String? = null,
    val additionalNotes: String? = null,
    val reasonForRequest: String,
)

data class EventRequestDecisionRequest(
    val adminRemarks: String? = null,
)

data class EventRequestResponse(
    val eventRequestId: UUID,
    val requesterUserId: UUID,
    val eventName: String,
    val eventDescription: String? = null,
    val eventCategory: String? = null,
    val targetAudience: String? = null,
    val capacity: Int = 0,
    val venue: String? = null,
    val startDateTime: Instant? = null,
    val endDateTime: Instant? = null,
    val registrationStartDateTime: Instant? = null,
    val registrationEndDateTime: Instant? = null,
    val requesterName: String? = null,
    val contactEmail: String? = null,
    val contactNumber: String? = null,
    val requestedFeatures: List<String>? = null,
    val eventLogoUrl: String? = null,
    val additionalNotes: String? = null,
    val reasonForRequest: String? = null,
    val status: EventRequestStatus,
    val eventId: UUID? = null,
    val adminRemarks: String? = null,
    val reviewedByUserId: UUID? = null,
    val reviewedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
