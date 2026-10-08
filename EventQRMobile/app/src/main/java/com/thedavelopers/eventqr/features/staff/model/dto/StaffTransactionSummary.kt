package com.thedavelopers.eventqr.features.staff.model.dto

/** Response of GET /staff/transactions/summary: counts of the caller's own scans. */
data class StaffTransactionSummary(
    val total: Long = 0,
    val approved: Long = 0,
    val rejected: Long = 0,
)

/** Response of GET /staff/transactions/today/summary: the caller's own figures since local midnight. */
data class StaffTodaySummary(
    val scannedToday: Long = 0,
    /** Distinct attendees with an APPROVED ENTRY / ATTENDANCE scan. */
    val successfulCheckIns: Long = 0,
)
