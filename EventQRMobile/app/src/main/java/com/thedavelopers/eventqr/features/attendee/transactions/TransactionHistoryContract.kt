package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.transactions.TransactionHistoryRow

interface TransactionHistoryContract {
    interface View : AttendeeView {
        override fun showLoading(isLoading: Boolean)
        fun showError(message: String)
        fun renderTransactions(items: List<TransactionHistoryRow>)
    }
}
