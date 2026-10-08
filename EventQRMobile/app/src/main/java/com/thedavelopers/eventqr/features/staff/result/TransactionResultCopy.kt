package com.thedavelopers.eventqr.features.staff.result

import androidx.annotation.StringRes
import com.thedavelopers.eventqr.R

/** Copy rules for the transaction-result screen: never claim points that were not awarded. */
object TransactionResultCopy {
    fun showsPoints(approved: Boolean, pointsDelta: Int): Boolean = approved && pointsDelta > 0

    @StringRes
    fun hintRes(approved: Boolean, pointsDelta: Int): Int = when {
        !approved -> R.string.staff_transaction_result_scan_rejected_and_logged
        pointsDelta > 0 -> R.string.staff_transaction_result_points_awarded_to_attendee
        else -> R.string.staff_transaction_result_scan_recorded
    }
}
