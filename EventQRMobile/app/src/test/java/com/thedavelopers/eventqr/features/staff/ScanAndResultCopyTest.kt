package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.staff.result.ScanResultState
import com.thedavelopers.eventqr.features.staff.result.TransactionResultCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanAndResultCopyTest {
    @Test
    fun activeQrAndRegistrationIsActive() {
        assertEquals(ScanResultState.ACTIVE, ScanResultState.from(true, true, "REGISTERED"))
        assertEquals(ScanResultState.ACTIVE, ScanResultState.from(true, true, "ENTERED"))
    }

    @Test
    fun inactiveQrIsInactive() {
        assertEquals(ScanResultState.INACTIVE, ScanResultState.from(true, false, "REGISTERED"))
    }

    @Test
    fun cancelledRegistrationIsInactiveEvenWithActiveQr() {
        assertEquals(ScanResultState.INACTIVE, ScanResultState.from(true, true, "CANCELLED"))
        assertEquals(ScanResultState.INACTIVE, ScanResultState.from(true, true, "no_show"))
    }

    @Test
    fun invalidVerificationIsRejected() {
        assertEquals(ScanResultState.REJECTED, ScanResultState.from(false, true, null))
    }

    @Test
    fun pointsShownOnlyWhenPositiveAndApproved() {
        assertTrue(TransactionResultCopy.showsPoints(true, 5))
        assertFalse(TransactionResultCopy.showsPoints(true, 0))
        assertFalse(TransactionResultCopy.showsPoints(true, -3))
        assertFalse(TransactionResultCopy.showsPoints(false, 5))
    }

    @Test
    fun hintCopyMatchesOutcome() {
        assertEquals(R.string.staff_transaction_result_points_awarded_to_attendee, TransactionResultCopy.hintRes(true, 10))
        assertEquals(R.string.staff_transaction_result_scan_recorded, TransactionResultCopy.hintRes(true, 0))
        assertEquals(R.string.staff_transaction_result_scan_rejected_and_logged, TransactionResultCopy.hintRes(false, 0))
    }
}
