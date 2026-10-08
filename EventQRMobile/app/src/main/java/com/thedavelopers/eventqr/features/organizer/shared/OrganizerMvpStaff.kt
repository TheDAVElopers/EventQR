package com.thedavelopers.eventqr.features.organizer

data class OrganizerMvpStaff(
    val id: String,
    val name: String,
    val email: String,
    val assignedEventId: String,
    val assignedEvent: String,
    val roleLabel: String,
    val accessStatus: String,
    val addedDate: String,
    val permissions: List<String>,
    /** True only on the add response, when the backend promoted an ATTENDEE account to STAFF. */
    val promotedToStaff: Boolean = false,
    val canScan: Boolean = true,
    val canPrintId: Boolean = false,
    val canViewLogs: Boolean = false,
    val canManageRewards: Boolean = false,
    /** Backend account role of a searched user (e.g. ATTENDEE, STAFF); blank for assigned rows. */
    val accountRole: String = "",
) {
    /** A plain attendee account becomes STAFF once assigned (they keep their attendee access). */
    val willBePromotedToStaff: Boolean get() = accountRole.equals("ATTENDEE", ignoreCase = true)

    fun withFlags(printId: Boolean, viewLogs: Boolean, manageRewards: Boolean): OrganizerMvpStaff = copy(
        canScan = true,
        canPrintId = printId,
        canViewLogs = viewLogs,
        canManageRewards = manageRewards,
        permissions = StaffPermissions.labels(true, printId, viewLogs, manageRewards),
    )
}

/** Permission labels shared by the assignment flow; display text for the summary lives in strings.xml. */
object StaffPermissions {
    const val SCAN = "Scan QR"
    const val PRINT_ID = "Print ID"
    const val VIEW_LOGS = "View Logs"
    const val MANAGE_REWARDS = "Manage Rewards"

    fun labels(scan: Boolean, printId: Boolean, viewLogs: Boolean, manageRewards: Boolean): List<String> = buildList {
        if (scan) add(SCAN)
        if (printId) add(PRINT_ID)
        if (viewLogs) add(VIEW_LOGS)
        if (manageRewards) add(MANAGE_REWARDS)
    }
}
