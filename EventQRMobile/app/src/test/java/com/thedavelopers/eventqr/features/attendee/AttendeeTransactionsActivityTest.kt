package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointTransactionResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/** The in-flow error strip and the zero-row empty-state error on transaction history (§5.7 line 642). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AttendeeTransactionsActivityTest {

    private fun transaction() = TransactionResponse(
        transactionId = UUID.randomUUID(),
        eventId = UUID.randomUUID(),
        eventTitle = "DevFest Manila",
        attendeeUserId = UUID.randomUUID(),
        registrationId = UUID.randomUUID(),
        qrCredentialId = UUID.randomUUID(),
        scanPurposeId = UUID.randomUUID(),
        transactionType = TransactionType.ENTRY,
        transactionResult = TransactionResult.APPROVED,
        pointsDelta = 10,
        scannedAt = Instant.now(),
    )

    private class FakeRepo(context: Context) : AttendeeRepository(context) {
        var transactions: NetworkResult<List<TransactionResponse>> = NetworkResult.Success(emptyList())
        override suspend fun getMyTransactions() = transactions
        override suspend fun getMyRegistrations(): NetworkResult<List<RegistrationResponse>> =
            NetworkResult.Success(emptyList())
        override suspend fun getMyPointTransactions(eventId: String): NetworkResult<List<PointTransactionResponse>> =
            NetworkResult.Success(emptyList())
    }

    private fun build(repo: FakeRepo): AttendeeTransactionsActivity {
        val controller = Robolectric.buildActivity(AttendeeTransactionsActivity::class.java)
        controller.get().repositoryOverride = repo
        controller.create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    private fun AttendeeTransactionsActivity.recycler() =
        findViewById<RecyclerView>(R.id.recyclerTransactions)

    private fun AttendeeTransactionsActivity.empty() =
        findViewById<EventQrEmptyState>(R.id.txtTransactionsEmpty)

    @Test
    fun refreshFailureWithRows_keepsTheRowsAndShowsTheStrip_thenSuccessHidesIt() {
        val repo = FakeRepo(ApplicationProvider.getApplicationContext()).apply {
            transactions = NetworkResult.Success(listOf(transaction()))
        }
        val activity = build(repo)
        val strip = activity.findViewById<View>(R.id.errorTransactions)
        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.GONE, strip.visibility)

        activity.showError("offline")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.VISIBLE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.empty().visibility)
        assertEquals(View.VISIBLE, strip.visibility)
        assertEquals(
            activity.getString(R.string.user_transaction_history_unable_to_load_transactions),
            activity.findViewById<TextView>(R.id.txtTransactionsError).text.toString(),
        )

        repo.transactions = NetworkResult.Success(listOf(transaction(), transaction()))
        activity.findViewById<View>(R.id.btnTransactionsRetry).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, activity.recycler().adapter!!.itemCount)
        assertEquals(View.GONE, strip.visibility)
    }

    @Test
    fun firstLoadFailureWithNoRows_showsTheFixedTitleInTheEmptyStateWithRetry_thenSuccessRenders() {
        val repo = FakeRepo(ApplicationProvider.getApplicationContext()).apply {
            transactions = NetworkResult.Error("offline")
        }
        val activity = build(repo)

        assertEquals(View.VISIBLE, activity.empty().visibility)
        assertEquals(
            activity.getString(R.string.user_transaction_history_unable_to_load_transactions),
            activity.empty().text.toString(),
        )
        assertEquals("offline", activity.empty().subtext.toString())
        val retry = activity.empty().findViewById<TextView>(R.id.emptyAction)
        assertEquals(View.VISIBLE, retry.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_retry), retry.text.toString())
        assertEquals(View.GONE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.errorTransactions).visibility)

        repo.transactions = NetworkResult.Success(listOf(transaction()))
        retry.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.VISIBLE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.empty().visibility)
    }
}
