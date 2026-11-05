package com.thedavelopers.eventqr.features.dashboard

import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary
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

    /** The middle cell is the attendee's own registrations, not the catalogue size in Discover. */
    @Test
    fun passHeadStats_middleCellIsRegisteredNotCatalogue() {
        val summary = DashboardSummary(
            totalEvents = 28,
            totalRegistrations = 2,
            totalTransactions = 0,
            totalPoints = 120,
            completedEventsCount = 1,
            totalNotifications = 0,
        )
        val stats = DashboardStats.passHeadStats(
            summary, "Points", "Registered", "Check-ins",
        ) { it?.toString() ?: "--" }
        assertEquals(listOf("120", "2", "1"), stats.map { it.value })
        assertEquals("Registered", stats[1].label)
    }

    /** A failed load keeps every cell unknown, so the stub renders "--" and never a fake 0. */
    @Test
    fun passHeadStats_failedLoadLeavesEveryCellUnknown() {
        val received = mutableListOf<Long?>()
        val stats = DashboardStats.passHeadStats(null, "Points", "Registered", "Check-ins") {
            received += it
            it?.toString() ?: "--"
        }
        assertEquals(listOf<Long?>(null, null, null), received)
        assertEquals(listOf("--", "--", "--"), stats.map { it.value })
    }
}
