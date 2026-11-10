package com.thedavelopers.eventqr.features.attendee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewardsEventSelectionTest {

    private val eventIds = listOf("event-a", "event-b")

    @Test
    fun requestedEventAbsentFromRegistrations_doesNotFallBackToTheFirstOption() {
        assertNull(resolveRewardsEventIndex(eventIds, preferredEventId = "event-c", requestedEventId = "event-c"))
    }

    @Test
    fun requestedEventAbsent_andNoRegistrations_staysEmpty() {
        assertNull(resolveRewardsEventIndex(emptyList(), preferredEventId = "event-c", requestedEventId = "event-c"))
    }

    @Test
    fun requestedEventPresent_selectsIt() {
        assertEquals(1, resolveRewardsEventIndex(eventIds, preferredEventId = "event-b", requestedEventId = "event-b"))
    }

    @Test
    fun noExplicitEvent_stillOpensTheFirstOption() {
        assertEquals(0, resolveRewardsEventIndex(eventIds, preferredEventId = null, requestedEventId = null))
    }

    @Test
    fun refreshKeepsTheSelectedEvent() {
        assertEquals(1, resolveRewardsEventIndex(eventIds, preferredEventId = "event-b", requestedEventId = "event-a"))
    }
}
