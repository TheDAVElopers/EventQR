package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.staff.result.ScanResultState
import com.thedavelopers.eventqr.features.staff.result.TransactionResultCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ScanAndResultCopyTest {
    @Test
    fun activeQrAndRegistrationIsActive() {
        assertEquals(ScanResultState.ACTIVE, ScanResultState.from(true, "REGISTERED"))
        assertEquals(ScanResultState.ACTIVE, ScanResultState.from(true, "ENTERED"))
    }

    @Test
    fun inactiveQrIsInactive() {
        assertEquals(ScanResultState.INACTIVE, ScanResultState.from(false, "REGISTERED"))
    }

    @Test
    fun cancelledRegistrationIsInactiveEvenWithActiveQr() {
        assertEquals(ScanResultState.INACTIVE, ScanResultState.from(true, "CANCELLED"))
        assertEquals(ScanResultState.INACTIVE, ScanResultState.from(true, "no_show"))
    }

    @Test
    fun ineligibleActiveScanIsNotEligibleNotInactive() {
        assertEquals(ScanResultState.NOT_ELIGIBLE, ScanResultState.from(true, "REGISTERED", eligible = false))
        assertEquals(ScanResultState.INACTIVE, ScanResultState.from(false, "REGISTERED", eligible = false))
        assertEquals(ScanResultState.ACTIVE, ScanResultState.from(true, "REGISTERED", eligible = true))
    }

    @Test
    fun pointsShownOnlyWhenPositiveAndApproved() {
        assertTrue(TransactionResultCopy.showsPoints(true, 5))
        assertFalse(TransactionResultCopy.showsPoints(true, 0))
        assertFalse(TransactionResultCopy.showsPoints(true, -3))
        assertFalse(TransactionResultCopy.showsPoints(false, 5))
    }

    // The live approved copy comes from approvedStateRes / approvedHintRes (ScannerActivity); hintRes
    // only speaks for a rejected row now.
    @Test
    fun approvedCopyNamesThePurposeInsteadOfACheckIn() {
        assertEquals(R.string.ui_staff_result_checked_out, TransactionResultCopy.approvedStateRes(TransactionType.EXIT))
        assertEquals(R.string.ui_staff_result_checked_out_hint, TransactionResultCopy.approvedHintRes(TransactionType.EXIT, 0))
        assertEquals(R.string.ui_staff_result_booth_visit, TransactionResultCopy.approvedStateRes(TransactionType.BOOTH_VISIT))
        assertEquals(R.string.ui_staff_result_session_visit, TransactionResultCopy.approvedStateRes(TransactionType.SESSION_VISIT))
        assertEquals(
            R.string.staff_transaction_result_points_awarded_to_attendee,
            TransactionResultCopy.approvedHintRes(TransactionType.EXIT, 10),
        )
        assertEquals(R.string.staff_transaction_result_scan_rejected_and_logged, TransactionResultCopy.hintRes(false, 0))
    }

    // Every type is named: only ENTRY may read "Checked in", and the types with no action word of
    // their own keep their label verbatim (Print ID, Registration, Reward redeemed).
    @Test
    fun everyApprovedTypeIsNamed_andOnlyEntryClaimsACheckIn() {
        TransactionType.values().forEach { type ->
            if (type == TransactionType.ENTRY) {
                assertEquals(R.string.ui_staff_result_valid, TransactionResultCopy.approvedStateRes(type))
            } else {
                assertNotEquals(R.string.ui_staff_result_valid, TransactionResultCopy.approvedStateRes(type))
            }
        }
        assertEquals(R.string.ui_staff_result_print_id, TransactionResultCopy.approvedStateRes(TransactionType.ID_PRINT))
        assertEquals(R.string.ui_staff_result_registration, TransactionResultCopy.approvedStateRes(TransactionType.REGISTRATION))
        assertEquals(R.string.ui_staff_redemption_approved, TransactionResultCopy.approvedStateRes(TransactionType.REWARD_REDEMPTION))
    }

    // Only ENTRY may borrow the entry hint: an approved print, registration or redemption names itself.
    @Test
    fun everyApprovedHintIsNamed_andOnlyEntryBorrowsTheEntryHint() {
        TransactionType.values().forEach { type ->
            if (type == TransactionType.ENTRY) {
                assertEquals(R.string.ui_staff_result_checked_in_hint, TransactionResultCopy.approvedHintRes(type, 0))
            } else {
                assertNotEquals(R.string.ui_staff_result_checked_in_hint, TransactionResultCopy.approvedHintRes(type, 0))
            }
        }
        assertEquals(R.string.ui_staff_result_print_id_hint, TransactionResultCopy.approvedHintRes(TransactionType.ID_PRINT, 0))
        assertEquals(R.string.ui_staff_result_registration_hint, TransactionResultCopy.approvedHintRes(TransactionType.REGISTRATION, 0))
        assertEquals(R.string.ui_staff_result_redemption_hint, TransactionResultCopy.approvedHintRes(TransactionType.REWARD_REDEMPTION_SCAN, 0))
    }

    @Test
    fun scanAllowedOnlyWithPermissionAndAScannableStatus() {
        fun event(canScan: Boolean, status: EventStatus) = StaffAssignedEventResponse(
            assignmentId = UUID.randomUUID(),
            eventId = UUID.randomUUID(),
            title = "Event",
            status = status,
            canScan = canScan,
        )

        assertTrue(event(true, EventStatus.ACTIVE).canStartScan)
        assertFalse(event(false, EventStatus.ACTIVE).canStartScan)
        assertFalse(event(true, EventStatus.ENDED).canStartScan)
        assertFalse(event(true, EventStatus.REJECTED).canStartScan)
        assertFalse(event(true, EventStatus.CANCELLED).canStartScan)
    }
}
