package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.staff.result.ScanResultState
import com.thedavelopers.eventqr.features.staff.result.TransactionResultCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

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
    fun ineligibleActiveScanIsNotEligibleNotInactive() {
        assertEquals(ScanResultState.NOT_ELIGIBLE, ScanResultState.from(true, true, "REGISTERED", eligible = false))
        assertEquals(ScanResultState.INACTIVE, ScanResultState.from(true, false, "REGISTERED", eligible = false))
        assertEquals(ScanResultState.ACTIVE, ScanResultState.from(true, true, "REGISTERED", eligible = true))
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
        assertEquals(R.string.ui_staff_tx_approved_hint, TransactionResultCopy.hintRes(true, 0))
        assertEquals(R.string.staff_transaction_result_scan_rejected_and_logged, TransactionResultCopy.hintRes(false, 0))
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
