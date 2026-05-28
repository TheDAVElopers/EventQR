package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

interface TransactionHistoryContract {
    interface View : AttendeeView {
        fun renderTransactions(items: List<TransactionResponse>)
    }
}
