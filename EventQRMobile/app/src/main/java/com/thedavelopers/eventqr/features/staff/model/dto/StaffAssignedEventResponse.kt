package com.thedavelopers.eventqr.features.staff.model.dto

import com.thedavelopers.eventqr.core.api.dto.EventStatus
import java.time.Instant
import java.util.UUID

data class StaffAssignedEventResponse(
    val assignmentId: UUID,
    val eventId: UUID,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val eventStartAt: Instant? = null,
    val eventEndAt: Instant? = null,
    val status: EventStatus,
    val canScan: Boolean = false,
    val canPrintId: Boolean = false,
    val canViewLogs: Boolean = false,
    val canManageRewards: Boolean = false,
)
