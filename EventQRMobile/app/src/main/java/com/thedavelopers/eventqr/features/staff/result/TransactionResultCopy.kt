package com.thedavelopers.eventqr.features.staff.result

import androidx.annotation.StringRes
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.TransactionType

/**
 * Copy rules for the scanned result: never claim points that were not awarded, and never claim an
 * entry when the staff logged an exit, a booth visit or a claim. The transaction type comes off the
 * server row, so the vocabulary follows the scan purpose the staff actually used.
 */
object TransactionResultCopy {
    fun showsPoints(approved: Boolean, pointsDelta: Int): Boolean = approved && pointsDelta > 0

    /**
     * The action word for an approved scan: one term per purpose, not "Checked in" for all of them.
     * Every [TransactionType] is named, so no type can fall back to the entry copy: a type with no
     * action word of its own keeps its label verbatim (Print ID, Registration). A new enum value
     * fails compilation here instead of shipping the wrong word silently.
     */
    @StringRes
    fun approvedStateRes(type: TransactionType): Int = when (type) {
        TransactionType.ENTRY -> R.string.ui_staff_result_valid
        TransactionType.ATTENDANCE -> R.string.ui_staff_result_attendance_logged
        TransactionType.BENEFIT_CLAIM -> R.string.ui_staff_result_benefit_claimed
        TransactionType.BOOTH_VISIT -> R.string.ui_staff_result_booth_visit
        TransactionType.SESSION_VISIT -> R.string.ui_staff_result_session_visit
        TransactionType.REWARD_REDEMPTION, TransactionType.REWARD_REDEMPTION_SCAN ->
            R.string.ui_staff_redemption_approved
        TransactionType.EXIT -> R.string.ui_staff_result_checked_out
        TransactionType.ID_PRINT -> R.string.ui_staff_result_print_id
        TransactionType.REGISTRATION -> R.string.ui_staff_result_registration
    }

    /**
     * Points win the hint when they were credited; the action hint speaks for the purpose otherwise.
     * Every [TransactionType] is named here too, so an approved print, registration or redemption
     * never borrows the entry hint, and a new enum value fails compilation.
     */
    @StringRes
    fun approvedHintRes(type: TransactionType, pointsDelta: Int): Int = when {
        pointsDelta > 0 -> R.string.staff_transaction_result_points_awarded_to_attendee
        else -> when (type) {
            TransactionType.ENTRY -> R.string.ui_staff_result_checked_in_hint
            TransactionType.ATTENDANCE -> R.string.ui_staff_result_attendance_logged_hint
            TransactionType.BENEFIT_CLAIM -> R.string.ui_staff_result_benefit_claimed_hint
            TransactionType.BOOTH_VISIT, TransactionType.SESSION_VISIT ->
                R.string.ui_staff_result_booth_visit_hint
            TransactionType.REWARD_REDEMPTION, TransactionType.REWARD_REDEMPTION_SCAN ->
                R.string.ui_staff_result_redemption_hint
            TransactionType.EXIT -> R.string.ui_staff_result_checked_out_hint
            TransactionType.ID_PRINT -> R.string.ui_staff_result_print_id_hint
            TransactionType.REGISTRATION -> R.string.ui_staff_result_registration_hint
        }
    }

    @StringRes
    fun hintRes(approved: Boolean, pointsDelta: Int): Int = when {
        !approved -> R.string.staff_transaction_result_scan_rejected_and_logged
        pointsDelta > 0 -> R.string.staff_transaction_result_points_awarded_to_attendee
        else -> R.string.ui_staff_tx_approved_hint
    }
}
