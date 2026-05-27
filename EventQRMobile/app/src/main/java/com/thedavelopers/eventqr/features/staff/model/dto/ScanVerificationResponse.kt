package com.thedavelopers.eventqr.features.staff.model.dto

import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import java.time.Instant
import java.util.UUID

data class ScanVerificationResponse(
    val eventId: UUID,
    val attendeeUserId: UUID,
    val registrationId: UUID,
    val qrCredentialId: UUID,
    val qrValue: String,
    val attendeeName: String? = null,
    val attendeeEmail: String? = null,
    val registrationStatus: RegistrationStatus,
    val scanPurposeId: UUID,
    val scanPurposeCode: ScanPurposeCode,
    val qrActive: Boolean,
    val message: String? = null,
    val verifiedAt: Instant? = null,
)