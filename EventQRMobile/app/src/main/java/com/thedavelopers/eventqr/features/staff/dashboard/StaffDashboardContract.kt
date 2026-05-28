package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

interface StaffDashboardContract {
    interface View {
        fun renderEvents(items: List<StaffAssignedEventResponse>)
        fun renderRecentScans(items: List<TransactionResponse>)
        fun updateStats(scans: Int, checkins: Int)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}
