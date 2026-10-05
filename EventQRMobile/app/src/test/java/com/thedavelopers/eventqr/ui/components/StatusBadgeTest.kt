package com.thedavelopers.eventqr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBadgeTest {

    @Test
    fun parseBadgeStatus_mapsPending() {
        assertEquals(EventBadgeStatus.PENDING, parseBadgeStatus("PENDING"))
        assertEquals(EventBadgeStatus.PENDING, parseBadgeStatus("pending"))
        assertEquals(EventBadgeStatus.PENDING, parseBadgeStatus("PENDING_REVIEW"))
    }

    @Test
    fun parseBadgeStatus_mapsApproved() {
        assertEquals(EventBadgeStatus.APPROVED, parseBadgeStatus("APPROVED"))
        assertEquals(EventBadgeStatus.APPROVED, parseBadgeStatus("approved"))
    }

    @Test
    fun parseBadgeStatus_mapsRejected() {
        assertEquals(EventBadgeStatus.REJECTED, parseBadgeStatus("REJECTED"))
        assertEquals(EventBadgeStatus.REJECTED, parseBadgeStatus("rejected"))
    }

    @Test
    fun parseBadgeStatus_mapsActiveVariants() {
        assertEquals(EventBadgeStatus.ACTIVE, parseBadgeStatus("ACTIVE"))
        assertEquals(EventBadgeStatus.ACTIVE, parseBadgeStatus("LIVE"))
        assertEquals(EventBadgeStatus.ACTIVE, parseBadgeStatus("ONGOING"))
        assertEquals(EventBadgeStatus.ACTIVE, parseBadgeStatus("ongoing"))
    }

    @Test
    fun parseBadgeStatus_mapsCompletedVariants() {
        assertEquals(EventBadgeStatus.COMPLETED, parseBadgeStatus("COMPLETED"))
        assertEquals(EventBadgeStatus.COMPLETED, parseBadgeStatus("ENDED"))
        assertEquals(EventBadgeStatus.COMPLETED, parseBadgeStatus("PAST"))
    }

    @Test
    fun parseBadgeStatus_mapsRegisteredUpcomingCancelled() {
        assertEquals(EventBadgeStatus.REGISTERED, parseBadgeStatus("REGISTERED"))
        assertEquals(EventBadgeStatus.UPCOMING, parseBadgeStatus("UPCOMING"))
        assertEquals(EventBadgeStatus.CANCELLED, parseBadgeStatus("CANCELLED"))
        assertEquals(EventBadgeStatus.CANCELLED, parseBadgeStatus("CANCELED"))
    }

    @Test
    fun parseBadgeStatus_nullBlankOrUnknown_defaultsToPending() {
        assertEquals(EventBadgeStatus.PENDING, parseBadgeStatus(null))
        assertEquals(EventBadgeStatus.PENDING, parseBadgeStatus(""))
        assertEquals(EventBadgeStatus.PENDING, parseBadgeStatus("UNKNOWN_XYZ"))
    }
}
