package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.features.staff.model.dto.StaffTransactionSummary
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

interface StaffTransactionsContract {
    interface View {
        fun renderTransactions(items: List<TransactionResponse>)
        fun renderSummary(summary: StaffTransactionSummary?)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}
