package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.features.rewards.model.dto.PointTransactionResponse
import com.thedavelopers.eventqr.features.transactions.TransactionHistoryRow
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** A scan award is mirrored by a point transaction; history must show it once, never as two rows. */
class TransactionHistoryMergeTest {

    private val eventId = UUID.randomUUID()
    private val attendeeId = UUID.randomUUID()

    private fun scan(
        transactionId: UUID,
        at: Instant,
        type: TransactionType = TransactionType.ENTRY,
        pointsDelta: Int = 10,
    ) = TransactionResponse(
        transactionId = transactionId,
        eventId = eventId,
        eventTitle = "DevFest Manila",
        attendeeUserId = attendeeId,
        registrationId = UUID.randomUUID(),
        qrCredentialId = UUID.randomUUID(),
        scanPurposeId = UUID.randomUUID(),
        transactionType = type,
        transactionResult = TransactionResult.APPROVED,
        pointsDelta = pointsDelta,
        scannedAt = at,
    )

    private fun point(sourceTransactionId: UUID, at: Instant, pointsChanged: Int, reason: String) =
        PointTransactionResponse(
            eventId = eventId,
            attendeeUserId = attendeeId,
            sourceTransactionId = sourceTransactionId,
            pointsChanged = pointsChanged,
            occurredAt = at,
            reason = reason,
        )

    @Test
    fun pointRowSourcedFromScan_isDropped() {
        val scanId = UUID.randomUUID()
        val at = Instant.parse("2026-11-01T09:00:00Z")

        val rows = mergeTransactionHistory(
            scans = listOf(scan(scanId, at)),
            pointTransactions = listOf(point(scanId, at, 10, "Manual point assignment")),
        )

        assertEquals(1, rows.size)
        assertEquals(scanId, (rows.single() as TransactionHistoryRow.Scan).transaction.transactionId)
    }

    @Test
    fun manualAdjustment_appearsAsPointRow_withItsOwnReason() {
        val at = Instant.parse("2026-11-02T10:00:00Z")

        val rows = mergeTransactionHistory(
            scans = emptyList(),
            pointTransactions = listOf(point(UUID.randomUUID(), at, -5, "Manual point deduction")),
            eventTitlesById = mapOf(eventId.toString() to "DevFest Manila"),
        )

        val row = rows.single() as TransactionHistoryRow.Point
        assertEquals(eventId.toString(), row.eventId)
        assertEquals("DevFest Manila", row.eventTitle)
        assertEquals(-5, row.pointsDelta)
        assertEquals("Manual point deduction", row.reason)
        assertEquals(at, row.occurredAt)
    }

    @Test
    fun redemption_appearsAsPointRow_andRowsAreNewestFirst() {
        val older = Instant.parse("2026-11-01T09:00:00Z")
        val newer = Instant.parse("2026-11-03T18:00:00Z")
        val scanId = UUID.randomUUID()

        val rows = mergeTransactionHistory(
            scans = listOf(scan(scanId, older)),
            pointTransactions = listOf(point(UUID.randomUUID(), newer, -40, "Reward redemption")),
        )

        assertEquals(2, rows.size)
        val first = rows.first() as TransactionHistoryRow.Point
        assertEquals("Reward redemption", first.reason)
        assertEquals(newer, rows.first().occurredAt)
        assertEquals(older, rows.last().occurredAt)
    }

    @Test
    fun scanBasedRedemption_keepsTrackingAndOutcomeRows_dropsOnlyItsLedgerRow() {
        val at = Instant.parse("2026-11-04T12:00:00Z")
        val scanId = UUID.randomUUID()
        val outcomeId = UUID.randomUUID()
        val redemptionId = UUID.randomUUID()

        val rows = mergeTransactionHistory(
            scans = listOf(
                scan(scanId, at, TransactionType.REWARD_REDEMPTION_SCAN, 0),
                scan(outcomeId, at.plusSeconds(5), TransactionType.REWARD_REDEMPTION, -40),
            ),
            // The backend writes the redemption id as the point row's source.
            pointTransactions = listOf(point(redemptionId, at.plusSeconds(5), -40, "Reward redemption")),
            // The redemption came from the pass scan, so its ledger row duplicates the outcome row.
            redemptionScanLogIds = mapOf(redemptionId to scanId),
        )

        // Both scan rows stay (the tracking row is real history); only the duplicate ledger row goes.
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.pointsDelta < 0 })
        assertEquals(1, rows.count { it.pointsDelta == 0 })
        assertEquals(0, rows.filterIsInstance<TransactionHistoryRow.Point>().size)
        assertEquals(outcomeId, (rows.first() as TransactionHistoryRow.Scan).transaction.transactionId)
    }

    @Test
    fun manualAdjustmentWithTheSameDelta_asAScanRedemption_survives() {
        val at = Instant.parse("2026-11-04T12:00:00Z")
        val redemptionId = UUID.randomUUID()
        val manualId = UUID.randomUUID()

        val rows = mergeTransactionHistory(
            scans = listOf(
                scan(UUID.randomUUID(), at, TransactionType.REWARD_REDEMPTION_SCAN, 0),
                scan(UUID.randomUUID(), at.plusSeconds(5), TransactionType.REWARD_REDEMPTION, -50),
            ),
            pointTransactions = listOf(
                point(redemptionId, at.plusSeconds(5), -50, "Reward redemption"),
                point(manualId, at.plusSeconds(30), -50, "Manual point deduction"),
            ),
            redemptionScanLogIds = mapOf(redemptionId to UUID.randomUUID()),
        )

        // The manual deduction is a real row, not the redemption's ledger duplicate.
        val pointRows = rows.filterIsInstance<TransactionHistoryRow.Point>()
        assertEquals(1, pointRows.size)
        assertEquals("Manual point deduction", pointRows.single().reason)
        assertEquals(2, rows.count { it.pointsDelta == -50 })
    }

    @Test
    fun zeroDeltaPointRow_isNotSwallowedByATrackingScan() {
        val at = Instant.parse("2026-11-04T12:00:00Z")

        val rows = mergeTransactionHistory(
            scans = listOf(scan(UUID.randomUUID(), at, TransactionType.REWARD_REDEMPTION_SCAN, 0)),
            pointTransactions = listOf(point(UUID.randomUUID(), at.plusSeconds(5), 0, "Manual point adjustment")),
        )

        assertEquals(1, rows.filterIsInstance<TransactionHistoryRow.Point>().size)
        assertEquals(0, rows.first { it is TransactionHistoryRow.Point }.pointsDelta)
    }

    @Test
    fun directZeroPointRedemption_isKept_whileTheScanLinkedLedgerRowDrops() {
        val at = Instant.parse("2026-11-04T12:00:00Z")
        val scanRedemptionId = UUID.randomUUID()
        val freeRedemptionId = UUID.randomUUID()

        val rows = mergeTransactionHistory(
            scans = listOf(
                scan(UUID.randomUUID(), at, TransactionType.REWARD_REDEMPTION_SCAN, 0),
                scan(UUID.randomUUID(), at.plusSeconds(5), TransactionType.REWARD_REDEMPTION, -40),
            ),
            pointTransactions = listOf(
                point(scanRedemptionId, at.plusSeconds(5), -40, "Reward redemption"),
                // A scan-less claim writes no outcome row, so this ledger row is its only trace.
                point(freeRedemptionId, at.plusSeconds(60), 0, "Reward redemption"),
            ),
            redemptionScanLogIds = mapOf(
                scanRedemptionId to UUID.randomUUID(),
                freeRedemptionId to null,
            ),
        )

        val pointRows = rows.filterIsInstance<TransactionHistoryRow.Point>()
        assertEquals(1, pointRows.size)
        assertEquals(0, pointRows.single().pointsDelta)
    }

    @Test
    fun mixedEvent_keepsManualAndDirectClaim_dropsOnlyTheScanLinkedLedgerRow() {
        val at = Instant.parse("2026-11-05T12:00:00Z")
        val trackingScanId = UUID.randomUUID()
        val outcomeId = UUID.randomUUID()
        val scanRedemptionId = UUID.randomUUID()
        val directRedemptionId = UUID.randomUUID()
        val manualId = UUID.randomUUID()

        val rows = mergeTransactionHistory(
            scans = listOf(
                scan(trackingScanId, at, TransactionType.REWARD_REDEMPTION_SCAN, 0),
                scan(outcomeId, at.plusSeconds(5), TransactionType.REWARD_REDEMPTION, -50),
            ),
            pointTransactions = listOf(
                point(manualId, at.plusSeconds(30), -50, "Manual point deduction"),
                point(scanRedemptionId, at.plusSeconds(5), -50, "Reward redemption"),
                // A direct claim for another reward: no outcome row, so its ledger row is its only trace.
                point(directRedemptionId, at.plusSeconds(90), 0, "Reward redemption"),
            ),
            redemptionScanLogIds = mapOf(
                scanRedemptionId to trackingScanId,
                directRedemptionId to null,
            ),
        )

        val pointRows = rows.filterIsInstance<TransactionHistoryRow.Point>()
        // Manual deduction plus the direct claim; the scan-linked ledger row is the only drop.
        assertEquals(2, pointRows.size)
        assertEquals(listOf("Manual point deduction"), pointRows.filter { it.pointsDelta == -50 }.map { it.reason })
        assertEquals(listOf(0), pointRows.filter { it.pointsDelta == 0 }.map { it.pointsDelta })
        // Both scan rows of the scan-based redemption stay: 2 scans + manual + direct claim, no duplicates.
        assertEquals(2, rows.filterIsInstance<TransactionHistoryRow.Scan>().size)
        assertEquals(4, rows.size)
    }

    @Test
    fun everyScanIsKeptOnce_evenWithNoPointRows() {
        val at = Instant.parse("2026-11-01T09:00:00Z")
        val scans = listOf(scan(UUID.randomUUID(), at), scan(UUID.randomUUID(), at.plusSeconds(60)))

        val rows = mergeTransactionHistory(scans, emptyList())

        assertEquals(2, rows.size)
        assertEquals(2, rows.filterIsInstance<TransactionHistoryRow.Scan>().size)
    }
}
