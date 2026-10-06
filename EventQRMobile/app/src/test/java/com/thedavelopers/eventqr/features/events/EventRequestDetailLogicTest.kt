package com.thedavelopers.eventqr.features.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.UUID

class EventRequestDetailLogicTest {

    @Test
    fun requesterPrefersName() {
        val result = requesterDisplay("Ada", "ada@example.com", UUID.randomUUID())

        assertEquals("Ada", result)
    }

    @Test
    fun requesterFallsBackToEmail() {
        val result = requesterDisplay("   ", "ada@example.com", UUID.randomUUID())

        assertEquals("ada@example.com", result)
    }

    @Test
    fun requesterFallsBackToUserId() {
        val id = UUID.randomUUID()

        assertEquals("Unknown requester", requesterDisplay(null, null, id))
        assertEquals("Unknown requester", requesterDisplay("  ", "  ", id))
    }

    @Test
    fun requesterNeverLeaksUserIdToCopy() {
        val id = UUID.randomUUID()

        listOf(
            requesterDisplay(null, null, id),
            requesterDisplay(null, "  ", id),
            requesterDisplay("   ", null, id),
        ).forEach { rendered ->
            assertEquals("Unknown requester", rendered)
            assertFalse(rendered.contains(id.toString()))
        }
    }

    @Test
    fun requesterReportsUnknownWhenNothingAvailable() {
        assertEquals("Unknown requester", requesterDisplay(null, null, null))
        assertEquals("Unknown requester", requesterDisplay("", "  ", null))
    }
}
