package com.thedavelopers.eventqr.features.dashboard

import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class DashboardStatsTest {

    private val now: Instant = Instant.parse("2026-06-01T00:00:00Z")

    private fun reg(status: RegistrationStatus, start: Instant?, end: Instant?) = RegistrationResponse(
        registrationId = UUID.randomUUID(),
        eventId = UUID.randomUUID(),
        attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "a@b.c",
        attendeeName = "A",
        status = status,
        eventStartAt = start,
        eventEndAt = end,
    )

    @Test
    fun upcomingCount_excludesOngoingAndUndated() {
        val starts = listOf(
            now.plusSeconds(3600),   // upcoming
            now.minusSeconds(3600),  // ongoing
            now,                     // starting exactly now is not upcoming
            null,
        )
        assertEquals(1, DashboardStats.upcomingCount(starts, now))
    }

    @Test
    fun registrationCounts_matchRegisteredTabChips() {
        val regs = listOf(
            reg(RegistrationStatus.REGISTERED, now.plusSeconds(10), now.plusSeconds(100)),
            reg(RegistrationStatus.ENTERED, now.minusSeconds(10), now.plusSeconds(100)),
            reg(RegistrationStatus.REGISTERED, now.minusSeconds(500), now.minusSeconds(100)), // ended, never attended
            reg(RegistrationStatus.EXITED, now.minusSeconds(500), now.minusSeconds(100)),
            reg(RegistrationStatus.CANCELLED, now.plusSeconds(10), now.plusSeconds(100)),
            reg(RegistrationStatus.NO_SHOW, now.minusSeconds(500), now.minusSeconds(100)),
        )
        val counts = DashboardStats.registrationCounts(regs, now)
        assertEquals(2, counts.registered)
        assertEquals(2, counts.completed)
    }

    @Test
    fun registrationCounts_emptyIsZero() {
        val counts = DashboardStats.registrationCounts(emptyList(), now)
        assertEquals(0, counts.registered)
        assertEquals(0, counts.completed)
    }

    @Test
    fun isLive_excludesCancelledAndNoShow() {
        assertFalse(DashboardStats.isLive(RegistrationStatus.CANCELLED))
        assertFalse(DashboardStats.isLive(RegistrationStatus.NO_SHOW))
        assertTrue(DashboardStats.isLive(RegistrationStatus.REGISTERED))
    }
}
