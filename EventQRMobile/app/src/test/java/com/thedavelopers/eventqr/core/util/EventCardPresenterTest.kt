package com.thedavelopers.eventqr.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class EventCardPresenterTest {

    @Test
    fun datePartsFormatsManilaTime() {
        val parts = EventCardPresenter.dateParts(Instant.parse("2026-03-09T02:30:00Z"))

        assertEquals("9", parts.day)
        assertEquals("Mar", parts.month)
        assertEquals("10:30 AM", parts.time)
    }

    @Test
    fun datePartsFallsBackWhenStartMissing() {
        val parts = EventCardPresenter.dateParts(null)

        assertEquals(EventCardPresenter.UNKNOWN_DAY, parts.day)
        assertEquals(EventCardPresenter.UNKNOWN_MONTH, parts.month)
        assertEquals(EventCardPresenter.UNKNOWN_TIME, parts.time)
    }

    @Test
    fun locationFallsBackOnBlank() {
        assertEquals(EventCardPresenter.UNKNOWN_LOCATION, EventCardPresenter.location(null))
        assertEquals(EventCardPresenter.UNKNOWN_LOCATION, EventCardPresenter.location("   "))
    }

    @Test
    fun locationKeepsRealValue() {
        assertEquals("Manila", EventCardPresenter.location("Manila"))
    }

    @Test
    fun capacityZeroMeansUnlimited() {
        assertEquals(0, EventCardPresenter.capacity(0))
        assertEquals(0, EventCardPresenter.capacity(-5))
        assertEquals(true, EventCardPresenter.isUnlimited(0))
        assertEquals(false, EventCardPresenter.isUnlimited(50))
        assertEquals(50, EventCardPresenter.capacity(50))
    }
}
