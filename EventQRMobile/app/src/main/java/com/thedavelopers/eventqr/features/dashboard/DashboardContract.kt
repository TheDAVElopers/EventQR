package com.thedavelopers.eventqr.features.dashboard

import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary

interface DashboardContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showSummary(summary: DashboardSummary)
        fun showError(message: String)
        fun showMessage(message: String)
        /**
         * A source inside the dashboard failed while the rest of the screen loaded. §5.16 collapses
         * every such failure to one sentence in the in-flow strip; [source] is for the log only.
         */
        fun showPartialError(source: String, message: String)
        fun openSection(title: String, message: String)
        fun updateHeader(role: String?, name: String?)
    }
}
