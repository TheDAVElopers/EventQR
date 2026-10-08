package com.thedavelopers.eventqr.features.audit.model.dto

import java.time.Instant
import java.util.UUID

data class AuditLogRequest(
    val action: String,
    val details: String? = null,
    val eventId: UUID? = null,
    val targetUserId: UUID? = null
)

data class AuditLogResponse(
    val auditLogId: UUID,
    val action: String,
    val details: String? = null,
    val performedByUserId: UUID,
    val performedByFullName: String?,
    val eventId: UUID? = null,
    val targetUserId: UUID? = null,
    val targetUserFullName: String? = null,
    val timestamp: Instant
)
