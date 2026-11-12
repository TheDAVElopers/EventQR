package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

interface StaffDashboardContract {
    interface View {
        fun renderRecentScans(items: List<TransactionResponse>)
        /** A null value could not be loaded and is shown as "--". */
        fun updateStats(scans: Int?, checkins: Int?)
        fun showMessage(message: String)
        /** First-load failure: the shared error state with Retry, never an empty list with an error body. */
        fun showError(message: String)
        /** Recent-scans load failure with the rest of the dashboard still readable. */
        fun showRecentScansError(message: String)
        fun showLoading(isLoading: Boolean)
        fun showNotificationBadge(unreadCount: Int)
    }
}
