package com.thedavelopers.eventqr.features.registrations.model.dto

import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot

data class RegistrationSubmissionResponse(
    val registration: RegistrationResponse,
    val qrCredential: QrCredentialSnapshot,
)