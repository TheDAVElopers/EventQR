package com.thedavelopers.eventqr.features.admin.model.dto

/** Response of GET /admin/stats. Server-side counts so the dashboard never derives them from one page. */
data class AdminStatsResponse(
    val totalAccounts: Long = 0,
    val activeEvents: Long = 0,
    val auditLogCount: Long = 0,
)
