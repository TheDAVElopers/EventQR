package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.features.rewards.model.dto.PointTransactionResponse
import com.thedavelopers.eventqr.features.transactions.TransactionHistoryRow
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.util.UUID

class TransactionHistoryPresenter(
    private var view: TransactionHistoryContract.View?,
    private val repository: AttendeeRepository,
) {
    private val scope = MainScope()
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    fun load() {
        view?.showLoading(true)
        job?.cancel()
        job = scope.launch {
            val history = loadHistory()
            view?.showLoading(false)
            // A failed read must not erase the rows that loaded: keep them and report the failure in
            // the in-flow strip. Only a failure with nothing renderable replaces the list.
            if (history.errorMessage == null || history.rows.isNotEmpty()) {
                view?.renderTransactions(history.rows)
            }
            history.errorMessage?.let { view?.showError(it) }
        }
    }

    /**
     * Scans come from the only global endpoint; point transactions are per event, so they need one
     * read per registered (or scanned) event. One failed read no longer fails the screen: its rows
     * are kept and the failure rides the error strip.
     */
    private suspend fun loadHistory(): HistoryLoad {
        val registrationsResult = repository.getMyRegistrations()
        val scansResult = repository.getMyTransactions()
        val registrations = if (registrationsResult is NetworkResult.Success) registrationsResult.data else emptyList()
        val scans = if (scansResult is NetworkResult.Success) scansResult.data else emptyList()
        var failure = mergeFailure(null, registrationsResult)
        failure = mergeFailure(failure, scansResult)

        val eventTitles = mutableMapOf<String, String>()
        registrations.forEach { registration ->
            registration.eventTitle?.takeIf { it.isNotBlank() }?.let { eventTitles[registration.eventId.toString()] = it }
        }
        scans.forEach { scan ->
            val eventId = scan.eventId.toString()
            if (eventId !in eventTitles) scan.eventTitle?.takeIf { it.isNotBlank() }?.let { eventTitles[eventId] = it }
        }

        val eventIds = (registrations.map { it.eventId.toString() } + scans.map { it.eventId.toString() }).distinct()
        // Only an event with an approved redemption outcome row can hold a scan-based redemption
        // ledger row, so only those events need the claimed-rewards read.
        val redemptionEventIds = scans.asSequence()
            .filter { it.isApprovedRedemptionOutcome() }
            .map { it.eventId.toString() }
            .distinct()
            .toList()
        // Bounded fan-out: one round trip per event, six in flight at most. awaitAll keeps the
        // result order aligned with eventIds so the merge stays deterministic.
        val (pointResults, redemptionResults) = coroutineScope {
            val limit = Semaphore(MAX_PARALLEL_EVENT_READS)
            val points = eventIds.map { eventId ->
                async { limit.withPermit { repository.getMyPointTransactions(eventId) } }
            }
            val redemptions = redemptionEventIds.map { eventId ->
                async { limit.withPermit { repository.getMyRewardRedemptions(eventId) } }
            }
            points.awaitAll() to redemptions.awaitAll()
        }
        val pointTransactions = mutableListOf<PointTransactionResponse>()
        for (result in pointResults) {
            when (result) {
                is NetworkResult.Success -> pointTransactions += result.data
                is NetworkResult.Error, NetworkResult.Loading -> failure = mergeFailure(failure, result)
            }
        }
        val redemptionScanLogIds = mutableMapOf<UUID, UUID?>()
        for (result in redemptionResults) {
            when (result) {
                is NetworkResult.Success ->
                    result.data.forEach { redemptionScanLogIds[it.redemptionId] = it.redemptionScanLogId }
                is NetworkResult.Error, NetworkResult.Loading -> failure = mergeFailure(failure, result)
            }
        }

        return HistoryLoad(mergeTransactionHistory(scans, pointTransactions, eventTitles, redemptionScanLogIds), failure)
    }

    private data class HistoryLoad(
        val rows: List<TransactionHistoryRow>,
        /** Null when every read succeeded; [rows] still holds whatever loaded when it is not. */
        val errorMessage: String? = null,
    )

    /** First failure wins; a real message outranks the empty message of a Loading result. */
    private fun mergeFailure(current: String?, result: NetworkResult<*>): String? = when (result) {
        is NetworkResult.Success -> current
        is NetworkResult.Error -> if (current.isNullOrBlank()) result.message.ifBlank { current ?: "" } else current
        NetworkResult.Loading -> current ?: ""
    }
}

/**
 * Newest first. A point row is dropped only when it duplicates a row already in the list:
 * - its `sourceTransactionId` names a scan row (a scan award writes the scan's id), or
 * - its `sourceTransactionId` names a redemption whose `redemptionScanLogId` is non-null (the
 *   redemption came from a scan, so its approved outcome row already shows it).
 *
 * A direct claim has no scan log, writes no outcome row, and its ledger row is the only trace, so
 * it stays. No delta is used as a key, so a manual adjustment that happens to move the same points
 * survives.
 *
 * A scan-based redemption keeps both of its scan rows: the 0-point REWARD_REDEMPTION_SCAN tracking
 * row and the REWARD_REDEMPTION outcome row. Only its duplicate ledger (point) row is dropped; the
 * tracking row is real history (the attendee's pass was scanned) and stays.
 */
internal fun mergeTransactionHistory(
    scans: List<TransactionResponse>,
    pointTransactions: List<PointTransactionResponse>,
    eventTitlesById: Map<String, String> = emptyMap(),
    redemptionScanLogIds: Map<UUID, UUID?> = emptyMap(),
): List<TransactionHistoryRow> {
    val scanIds = scans.mapTo(HashSet()) { it.transactionId }
    val rows = mutableListOf<TransactionHistoryRow>()
    scans.mapTo(rows) { TransactionHistoryRow.Scan(it) }

    pointTransactions.forEach { point ->
        if (point.sourceTransactionId in scanIds) return@forEach
        // Null covers both "not a redemption" and "direct claim": only a scan-backed redemption is
        // already represented by an outcome row.
        if (redemptionScanLogIds[point.sourceTransactionId] != null) return@forEach
        rows += TransactionHistoryRow.Point(
            eventId = point.eventId.toString(),
            eventTitle = eventTitlesById[point.eventId.toString()],
            occurredAt = point.occurredAt,
            pointsDelta = point.pointsChanged,
            reason = point.reason,
        )
    }
    return rows.sortedByDescending { it.occurredAt ?: Instant.EPOCH }
}

private const val MAX_PARALLEL_EVENT_READS = 6

private fun TransactionResponse.isApprovedRedemptionOutcome(): Boolean =
    transactionResult == TransactionResult.APPROVED &&
        transactionType == TransactionType.REWARD_REDEMPTION
