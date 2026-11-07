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

    private fun reg(
        status: RegistrationStatus,
        start: Instant?,
        end: Instant?,
        enteredAt: Instant? = null,
        attendedAt: Instant? = null,
    ) = RegistrationResponse(
        registrationId = UUID.randomUUID(),
        eventId = UUID.randomUUID(),
        attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "a@b.c",
        attendeeName = "A",
        status = status,
        eventStartAt = start,
        eventEndAt = end,
        enteredAt = enteredAt,
        attendedAt = attendedAt,
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

    @Test
    fun attendeeCounts_liveRegistrationsOnlyCountAheadOfMe() {
        val regs = listOf(
            reg(RegistrationStatus.REGISTERED, now.plusSeconds(10), now.plusSeconds(100)),
            reg(RegistrationStatus.ENTERED, now.minusSeconds(10), now.plusSeconds(100), enteredAt = now.minusSeconds(10)),
            reg(RegistrationStatus.REGISTERED, now.minusSeconds(500), now.minusSeconds(100)),
        )
        val counts = DashboardStats.attendeeCounts(regs, now)
        assertEquals(2, counts.registered)
        assertEquals(3, counts.events)
    }

    /** A no-show is a lifetime sign-up but never reads as "ahead of me". */
    @Test
    fun attendeeCounts_noShowIsAnEventButNotRegistered() {
        val regs = listOf(reg(RegistrationStatus.NO_SHOW, now.minusSeconds(500), now.minusSeconds(100)))
        val counts = DashboardStats.attendeeCounts(regs, now)
        assertEquals(0, counts.registered)
        assertEquals(0, counts.checkIns)
        assertEquals(1, counts.events)
    }

    /** A cancelled row is neither an event nor a check-in, even with a legacy entry timestamp. */
    @Test
    fun attendeeCounts_cancelledIsNeverAnEventOrCheckIn() {
        val regs = listOf(
            reg(RegistrationStatus.CANCELLED, now.plusSeconds(10), now.plusSeconds(100)),
            reg(RegistrationStatus.CANCELLED, now.minusSeconds(500), now.minusSeconds(100), enteredAt = now.minusSeconds(600)),
        )
        val counts = DashboardStats.attendeeCounts(regs, now)
        assertEquals(0, counts.registered)
        assertEquals(0, counts.checkIns)
        assertEquals(0, counts.events)
    }

    @Test
    fun attendeeCounts_emptyIsThreeZeros() {
        val counts = DashboardStats.attendeeCounts(emptyList(), now)
        assertEquals(0, counts.registered)
        assertEquals(0, counts.checkIns)
        assertEquals(0, counts.events)
    }

    /** An unknown end means "not ended", so the registration still reads as ahead of me. */
    @Test
    fun attendeeCounts_missingEndStaysRegistered() {
        val counts = DashboardStats.attendeeCounts(
            listOf(reg(RegistrationStatus.REGISTERED, now.minusSeconds(10), null)),
            now,
        )
        assertEquals(1, counts.registered)
        assertEquals(1, counts.events)
    }

    /** attendedAt is authoritative; enteredAt is the legacy fallback; neither = not attended. */
    @Test
    fun attendeeCounts_usesAttendedAtThenEnteredAt() {
        val regs = listOf(
            reg(RegistrationStatus.EXITED, now.minusSeconds(500), now.minusSeconds(100), attendedAt = now.minusSeconds(600)),
            reg(RegistrationStatus.REGISTERED, now.minusSeconds(500), now.minusSeconds(100), enteredAt = now.minusSeconds(600)),
            reg(RegistrationStatus.REGISTERED, now.minusSeconds(500), now.minusSeconds(100)),
        )
        val counts = DashboardStats.attendeeCounts(regs, now)
        assertEquals(2, counts.checkIns)
    }

    /** The stub order is registered / check-ins / events; points no longer reaches a cell. */
    @Test
    fun passHeadStats_orderIsRegisteredCheckInsEvents() {
        val summary = DashboardSummary(
            totalEvents = 28,
            totalRegistrations = 2,
            totalTransactions = 0,
            totalPoints = 120,
            completedEventsCount = 1,
            checkInsCount = 4,
            lifetimeRegistrationsCount = 7,
            totalNotifications = 0,
        )
        val stats = DashboardStats.passHeadStats(
            summary, "Registered", "Check-ins", "Events",
        ) { it?.toString() ?: "--" }
        assertEquals(listOf("2", "4", "7"), stats.map { it.value })
        assertEquals(listOf("Registered", "Check-ins", "Events"), stats.map { it.label })
        assertFalse(stats.any { it.value == "120" })
    }

    /** A failed load keeps every cell unknown, so the stub renders "--" and never a fake 0. */
    @Test
    fun passHeadStats_failedLoadLeavesEveryCellUnknown() {
        val received = mutableListOf<Long?>()
        val stats = DashboardStats.passHeadStats(null, "Registered", "Check-ins", "Events") {
            received += it
            it?.toString() ?: "--"
        }
        assertEquals(listOf<Long?>(null, null, null), received)
        assertEquals(listOf("--", "--", "--"), stats.map { it.value })
    }
}
