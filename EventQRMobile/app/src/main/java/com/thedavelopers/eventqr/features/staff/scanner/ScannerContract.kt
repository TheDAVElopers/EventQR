package com.thedavelopers.eventqr.features.staff.scanner

import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse

/** The event and purpose a scan was submitted for, captured at submit time (§6.3). */
data class ScanScope(val eventLabel: String, val purposeLabel: String)

// Saved-state keys for the captured scan scope (§6.3): a rotation restates the finished scan's
// labels instead of reading the live selection.
const val STATE_SCOPE_EVENT = "scanner_scope_event"
const val STATE_SCOPE_PURPOSE = "scanner_scope_purpose"

interface ScannerContract {
    interface View {
        fun showEvents(items: List<EventSpinnerOption>)

        /** The assigned-event list could not be read: an in-flow error state with one retry. */
        fun showEventsLoadFailed(message: String)
        fun showPurposes(items: List<ScanPurposeResponse>)

        /**
         * The purpose load failed. The view must drop any list it still holds for the previous
         * event: a stale purpose would let the next scan post event B under event A's purpose.
         */
        fun showPurposesUnavailable(message: String)
        fun showRecordedResult(result: TransactionResponse)
        fun showVerifiedResult(result: ScanVerificationResponse)
        fun showRecordFailed(message: String)
        fun showRewardRedemptionScanResult(result: RewardRedemptionScanResponse)
        fun showScanError(message: String)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}
