package com.thedavelopers.eventqr.features.admin.model.dto

import java.time.Instant

/**
 * Response of GET /admin/stats. Server-side counts so the dashboard never derives them from one page.
 *
 * Every field is nullable: a backend that does not answer a field is *unknown*, not zero, and the
 * screen prints `--` (§5.9). `0` is printed only when the server sent it.
 */
data class AdminStatsResponse(
    val totalAccounts: Long? = null,
    val activeEvents: Long? = null,
    val pendingEventRequests: Long? = null,
    /** Up to the three rows the dashboard previews; the full list lives on the Requests screen. */
    val pendingPreview: List<AdminPendingPreview> = emptyList(),
)

/** One row of the dashboard's waiting queue. `id` opens that request's detail screen. */
data class AdminPendingPreview(
    val id: String? = null,
    val requesterName: String? = null,
    val eventName: String? = null,
    val createdAt: Instant? = null,
)
