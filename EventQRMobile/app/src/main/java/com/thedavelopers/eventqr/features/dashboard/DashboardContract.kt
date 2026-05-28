package com.thedavelopers.eventqr.features.dashboard

import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary

interface DashboardContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showSummary(summary: DashboardSummary)
        fun showError(message: String)
        fun showMessage(message: String)
        fun openSection(title: String, message: String)
        fun updateHeader(role: String?, name: String?)
    }
}