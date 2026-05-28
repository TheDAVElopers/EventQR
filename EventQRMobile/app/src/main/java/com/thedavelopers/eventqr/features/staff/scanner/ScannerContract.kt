package com.thedavelopers.eventqr.features.staff.scanner

import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse

interface ScannerContract {
    interface View {
        fun showEvents(items: List<EventSpinnerOption>)
        fun showPurposes(items: List<ScanPurposeResponse>)
        fun appendScanResult(result: TransactionResponse)
        fun showVerificationResult(result: ScanVerificationResponse)
        fun showScanError(message: String)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}