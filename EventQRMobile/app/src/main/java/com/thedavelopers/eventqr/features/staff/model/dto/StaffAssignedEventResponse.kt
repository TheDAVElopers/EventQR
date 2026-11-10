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
) {
    /** Mirrors the server's scan guard: assignment permission plus a status that accepts scans. */
    val canStartScan: Boolean
        get() = canScan && status != EventStatus.ENDED && status != EventStatus.REJECTED && status != EventStatus.CANCELLED
}
