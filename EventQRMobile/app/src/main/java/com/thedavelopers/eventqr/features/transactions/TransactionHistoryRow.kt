package com.thedavelopers.eventqr.features.transactions

import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import java.time.Instant

/**
 * One attendee history row. Scans carry their own result/type; point rows cover activity that has
 * no scan row of its own (manual adjustments, reward redemptions).
 */
sealed interface TransactionHistoryRow {
    val eventId: String
    val eventTitle: String?
    val occurredAt: Instant?
    val pointsDelta: Int

    data class Scan(val transaction: TransactionResponse) : TransactionHistoryRow {
        override val eventId: String get() = transaction.eventId.toString()
        override val eventTitle: String? get() = transaction.eventTitle
        override val occurredAt: Instant? get() = transaction.scannedAt
        override val pointsDelta: Int get() = transaction.pointsDelta
    }

    data class Point(
        override val eventId: String,
        override val eventTitle: String?,
        override val occurredAt: Instant?,
        override val pointsDelta: Int,
        val reason: String?,
    ) : TransactionHistoryRow
}
