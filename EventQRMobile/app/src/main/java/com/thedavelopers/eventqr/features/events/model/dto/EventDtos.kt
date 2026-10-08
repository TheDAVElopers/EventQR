package com.thedavelopers.eventqr.features.events.model.dto

import com.thedavelopers.eventqr.core.api.dto.EventStatus
import java.time.Instant
import java.util.UUID

data class EventRequest(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val eventLogoUrl: String? = null,
    val category: String? = null,
    val registrationOpenAt: Instant? = null,
    val registrationCloseAt: Instant? = null,
    val eventStartAt: Instant? = null,
    val eventEndAt: Instant? = null,
    val capacity: Int,
    val rewardsEnabled: Boolean,
    val organizerUserId: UUID,
)

data class EventApprovalRequest(
    val approved: Boolean,
    val reviewerUserId: UUID? = null,
    val rejectionReason: String? = null,
)

data class EventResponse(
    val eventId: UUID,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val eventLogoUrl: String? = null,
    val category: String? = null,
    val registrationOpenAt: Instant? = null,
    val registrationCloseAt: Instant? = null,
    val eventStartAt: Instant? = null,
    val eventEndAt: Instant? = null,
    val capacity: Int,
    val currentAttendeeCount: Int,
    val status: EventStatus,
    val rewardsEnabled: Boolean,
    val organizerUserId: UUID? = null,
    val approvedByUserId: UUID? = null,
    val approvedAt: Instant? = null,
    val rejectionReason: String? = null,
)

data class AttendeeEventResponse(
    val eventId: UUID,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val eventLogoUrl: String? = null,
    val category: String? = null,
    val registrationOpenAt: Instant? = null,
    val registrationCloseAt: Instant? = null,
    val eventStartAt: Instant? = null,
    val eventEndAt: Instant? = null,
    val capacity: Int = 0,
    val currentAttendeeCount: Int = 0,
    val status: EventStatus? = null,
    val rewardsEnabled: Boolean = false,
    val organizerUserId: UUID? = null,
    // Backend-derived flag; nullable so reflective Gson (no Kotlin adapter) tolerates
    // a missing/explicit-null field — treat null as "not owned" (default false).
    val isOwnedByCurrentUser: Boolean? = null,
    val targetAudience: String? = null,
)

data class EventAvailabilityResponse(
    val eventId: UUID,
    val capacity: Int,
    val currentAttendeeCount: Int,
    val registrationOpen: Boolean,
    val full: Boolean,
    val available: Boolean,
    val message: String,
    val serverNow: Instant? = null,
    val registrationOpenAt: Instant? = null,
    val registrationCloseAt: Instant? = null,
)