package com.thedavelopers.eventqr.features.attendee

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointTransactionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.transactions.TransactionHistoryRow
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/** One failed per-event read keeps the rows that loaded; only a failure with no rows replaces them. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class TransactionHistoryPresenterLoadTest {

    private val eventA = UUID.randomUUID()
    private val eventB = UUID.randomUUID()

    private fun registration(eventId: UUID) = RegistrationResponse(
        registrationId = UUID.randomUUID(),
        eventId = eventId,
        attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "a@test.com",
        attendeeName = "A",
        status = RegistrationStatus.REGISTERED,
        eventTitle = "Event ${eventId.toString().take(4)}",
    )

    private fun scan(
        eventId: UUID,
        type: TransactionType = TransactionType.ENTRY,
        points: Int = 10,
    ) = TransactionResponse(
        transactionId = UUID.randomUUID(),
        eventId = eventId,
        eventTitle = "Event ${eventId.toString().take(4)}",
        attendeeUserId = UUID.randomUUID(),
        registrationId = UUID.randomUUID(),
        qrCredentialId = UUID.randomUUID(),
        scanPurposeId = UUID.randomUUID(),
        transactionType = type,
        transactionResult = TransactionResult.APPROVED,
        pointsDelta = points,
        scannedAt = Instant.parse("2026-11-01T09:00:00Z"),
    )

    private fun point(
        eventId: UUID,
        source: UUID = UUID.randomUUID(),
        points: Int = -5,
        reason: String = "Manual point deduction",
    ) = PointTransactionResponse(
        eventId = eventId,
        attendeeUserId = UUID.randomUUID(),
        sourceTransactionId = source,
        pointsChanged = points,
        occurredAt = Instant.parse("2026-11-02T09:00:00Z"),
        reason = reason,
    )

    private fun redemption(eventId: UUID, redemptionId: UUID, scanLogId: UUID? = null) = RewardRedemptionResponse(
        redemptionId = redemptionId,
        eventId = eventId,
        attendeeUserId = UUID.randomUUID(),
        rewardId = UUID.randomUUID(),
        pointsSpent = 40,
        status = RedemptionStatus.REDEEMED,
        redemptionScanLogId = scanLogId,
    )

    @Test
    fun oneFailedEventRead_keepsTheLoadedRowsAndReportsTheError() {
        val repo = FakeRepo().apply {
            registrations = NetworkResult.Success(listOf(registration(eventA), registration(eventB)))
            transactions = NetworkResult.Success(listOf(scan(eventA)))
            points[eventA.toString()] = NetworkResult.Success(listOf(point(eventA)))
            points[eventB.toString()] = NetworkResult.Error("offline")
        }
        val view = RecordingView()

        TransactionHistoryPresenter(view, repo).load()
        shadowOf(Looper.getMainLooper()).idle()

        // eventA: its scan row plus the manual deduction; eventB's failed point read is a warning.
        assertEquals(2, view.rendered.single().size)
        assertEquals(1, view.rendered.single().count { it is TransactionHistoryRow.Point })
        assertEquals(listOf("offline"), view.errors)
        assertEquals(listOf(true, false), view.loading)
    }

    @Test
    fun failedRegistrationsRead_stillRendersTheScans() {
        val repo = FakeRepo().apply {
            registrations = NetworkResult.Error("offline")
            transactions = NetworkResult.Success(listOf(scan(eventA)))
            points[eventA.toString()] = NetworkResult.Success(emptyList())
        }
        val view = RecordingView()

        TransactionHistoryPresenter(view, repo).load()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, view.rendered.single().size)
        assertEquals(listOf("offline"), view.errors)
    }

    @Test
    fun everyReadFailed_showsOnlyTheFullError() {
        val repo = FakeRepo().apply {
            registrations = NetworkResult.Error("offline")
            transactions = NetworkResult.Error("offline")
        }
        val view = RecordingView()

        TransactionHistoryPresenter(view, repo).load()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<List<TransactionHistoryRow>>(), view.rendered)
        assertEquals(listOf("offline"), view.errors)
        assertEquals(listOf(true, false), view.loading)
    }

    @Test
    fun claimedRedemption_ledgerRowIsDroppedByItsRedemptionId() {
        val redemptionId = UUID.randomUUID()
        val repo = FakeRepo().apply {
            registrations = NetworkResult.Success(listOf(registration(eventA)))
            transactions = NetworkResult.Success(
                listOf(
                    scan(eventA, type = TransactionType.REWARD_REDEMPTION_SCAN, points = 0),
                    scan(eventA, type = TransactionType.REWARD_REDEMPTION, points = -40),
                ),
            )
            points[eventA.toString()] = NetworkResult.Success(
                listOf(point(eventA, source = redemptionId, points = -40, reason = "Reward redemption")),
            )
            claims[eventA.toString()] = NetworkResult.Success(
                listOf(redemption(eventA, redemptionId, scanLogId = UUID.randomUUID())),
            )
        }
        val view = RecordingView()

        TransactionHistoryPresenter(view, repo).load()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, view.rendered.single().size)
        assertEquals(0, view.rendered.single().count { it is TransactionHistoryRow.Point })
        assertEquals(emptyList<String>(), view.errors)
    }

    @Test
    fun failedClaimRead_keepsTheLedgerRowAndReportsTheError() {
        val repo = FakeRepo().apply {
            registrations = NetworkResult.Success(listOf(registration(eventA)))
            transactions = NetworkResult.Success(
                listOf(
                    scan(eventA, type = TransactionType.REWARD_REDEMPTION_SCAN, points = 0),
                    scan(eventA, type = TransactionType.REWARD_REDEMPTION, points = -40),
                ),
            )
            points[eventA.toString()] = NetworkResult.Success(
                listOf(point(eventA, points = -40, reason = "Reward redemption")),
            )
            claims[eventA.toString()] = NetworkResult.Error("offline")
        }
        val view = RecordingView()

        TransactionHistoryPresenter(view, repo).load()
        shadowOf(Looper.getMainLooper()).idle()

        // The link is unknown, so the ledger row is kept rather than guessed away.
        assertEquals(1, view.rendered.single().count { it is TransactionHistoryRow.Point })
        assertEquals(listOf("offline"), view.errors)
    }

    private class FakeRepo : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        var registrations: NetworkResult<List<RegistrationResponse>> = NetworkResult.Success(emptyList())
        var transactions: NetworkResult<List<TransactionResponse>> = NetworkResult.Success(emptyList())
        val points = mutableMapOf<String, NetworkResult<List<PointTransactionResponse>>>()
        val claims = mutableMapOf<String, NetworkResult<List<RewardRedemptionResponse>>>()

        override suspend fun getMyRegistrations() = registrations
        override suspend fun getMyTransactions() = transactions
        override suspend fun getMyPointTransactions(eventId: String) =
            points[eventId] ?: NetworkResult.Success(emptyList())
        override suspend fun getMyRewardRedemptions(eventId: String) =
            claims[eventId] ?: NetworkResult.Success(emptyList())
    }

    private class RecordingView : TransactionHistoryContract.View {
        val rendered = mutableListOf<List<TransactionHistoryRow>>()
        val errors = mutableListOf<String>()
        val loading = mutableListOf<Boolean>()
        override fun showLoading(isLoading: Boolean) { loading += isLoading }
        override fun showError(message: String) { errors += message }
        override fun renderTransactions(items: List<TransactionHistoryRow>) { rendered += items }
        override fun showMessage(message: String) = Unit
    }
}
