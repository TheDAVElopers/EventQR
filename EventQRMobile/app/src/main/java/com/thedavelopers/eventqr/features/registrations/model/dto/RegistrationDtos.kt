package com.thedavelopers.eventqr.features.registrations.model.dto

import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import java.time.Instant
import java.util.UUID

data class RegistrationRequest(
    val eventId: UUID,
    val email: String,
    val fullName: String,
    val phoneNumber: String? = null,
)

data class RegistrationResponse(
    val registrationId: UUID,
    val eventId: UUID,
    val attendeeUserId: UUID,
    val attendeeEmail: String,
    val attendeeName: String,
    val status: RegistrationStatus,
    val qrCredentialId: UUID? = null,
    val registeredAt: Instant? = null,
    val eventTitle: String? = null,
    val eventLocation: String? = null,
    val eventStartAt: Instant? = null,
    val eventEndAt: Instant? = null,
)
