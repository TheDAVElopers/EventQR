package com.thedavelopers.eventqr.features.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.UUID

class EventRequestDetailLogicTest {

    private val unknown = "Unknown requester"

    private fun display(
        name: String?,
        email: String?,
        userId: UUID?,
    ) = requesterDisplay(name, email, userId, unknown)

    @Test
    fun requesterPrefersName() {
        val result = display("Ada", "ada@example.com", UUID.randomUUID())

        assertEquals("Ada", result)
    }

    @Test
    fun requesterFallsBackToEmail() {
        val result = display("   ", "ada@example.com", UUID.randomUUID())

        assertEquals("ada@example.com", result)
    }

    @Test
    fun requesterFallsBackToUserId() {
        val id = UUID.randomUUID()

        assertEquals(unknown, display(null, null, id))
        assertEquals(unknown, display("  ", "  ", id))
    }

    @Test
    fun requesterNeverLeaksUserIdToCopy() {
        val id = UUID.randomUUID()

        listOf(
            display(null, null, id),
            display(null, "  ", id),
            display("   ", null, id),
        ).forEach { rendered ->
            assertEquals(unknown, rendered)
            assertFalse(rendered.contains(id.toString()))
        }
    }

    @Test
    fun requesterReportsUnknownWhenNothingAvailable() {
        assertEquals(unknown, display(null, null, null))
        assertEquals(unknown, display("", "  ", null))
    }
}
