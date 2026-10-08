package com.thedavelopers.eventqr.features.dashboard.model.dto

import java.time.Instant
import java.util.UUID

data class DashboardSummary(
    val totalEvents: Long,
    /** Null when it could not be determined; the tile then renders "--". */
    val totalRegistrations: Long?,
    val totalTransactions: Long,
    val totalPoints: Long,
    /** Server-provided completed count; null when the backend did not send it (older servers). */
    val completedEventsCount: Long? = null,
    val totalNotifications: Long,
    val fullName: String? = null,
    val upcomingEvents: List<DashboardUpcomingEvent>? = emptyList(),
    val discoverEvents: List<DashboardUpcomingEvent>? = emptyList(),
)

data class DashboardUpcomingEvent(
    val eventId: UUID,
    val registrationId: UUID? = null,
    val title: String,
    val location: String? = null,
    val category: String? = null,
    val eventStartAt: Instant? = null,
    val status: String? = null,
    val description: String? = null,
    val eventEndAt: Instant? = null,
    val capacity: Int = 0,
    val currentAttendeeCount: Int = 0,
    val isRegistered: Boolean = false,
    /** True when capacity/count were not provided (registration-only rows); the card hides them. */
    val capacityUnknown: Boolean = false,
)
