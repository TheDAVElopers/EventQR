package com.thedavelopers.eventqr.features.staff.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A submission that never leaves the presenter still has to end: the scanner holds one
 * submit-in-flight flag, and only a terminal view callback clears it. A guard that only toasted
 * would leave the camera dead until the screen was reopened.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ScannerSubmitGuardTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val eventId = "11111111-1111-1111-1111-111111111111"

    private fun purpose() = ScanPurposeResponse(
        scanPurposeId = UUID.randomUUID(),
        eventId = UUID.fromString(eventId),
        name = "Entry",
        code = ScanPurposeCode.ENTRY,
        active = true,
        trackingOnly = false,
    )

    private class RecordingView : ScannerContract.View {
        val scanErrors = Collections.synchronizedList(mutableListOf<String>())
        val messages = Collections.synchronizedList(mutableListOf<String>())
        val recorded = Collections.synchronizedList(mutableListOf<TransactionResponse>())
        val recordFailures = Collections.synchronizedList(mutableListOf<String>())

        override fun showEvents(items: List<EventSpinnerOption>) = Unit
        override fun showEventsLoadFailed(message: String) {
            messages += message
        }
        override fun showPurposes(items: List<ScanPurposeResponse>) = Unit
        override fun showPurposesUnavailable(message: String) = Unit
        override fun showRecordedResult(result: TransactionResponse) {
            recorded += result
        }
        override fun showVerifiedResult(result: ScanVerificationResponse) = Unit
        override fun showRecordFailed(message: String) {
            recordFailures += message
        }
        override fun showRewardRedemptionScanResult(result: RewardRedemptionScanResponse) = Unit
        override fun showScanError(message: String) {
            scanErrors += message
        }
        override fun showMessage(message: String) {
            messages += message
        }
        override fun showLoading(isLoading: Boolean) = Unit
    }

    /** Any request at all fails the test: these paths must be decided before the network. */
    private class NoRequestRepository(context: Context) : StaffRepository(context) {
        override suspend fun verifyScan(request: TransactionRequest): NetworkResult<ScanVerificationResponse> =
            error("verify must not run for a rejected submission")
    }

    @Test
    fun rejectedSubmissions_endInAScanErrorAndNeverOnlyAToast() {
        val view = RecordingView()
        val presenter = ScannerPresenter(
            view,
            NoRequestRepository(context),
            UiStrings(context),
            CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher()),
        )
        try {
            presenter.recordScan("", purpose(), "abc", null, UUID.randomUUID())
            presenter.verifyScan(eventId, purpose(), "   ", null)
            presenter.submitRewardRedemptionScan("", purpose(), "abc", null)

            assertEquals(3, view.scanErrors.size)
            assertTrue(view.messages.isEmpty())
        } finally {
            presenter.detach()
        }
    }

    // ---- a short-ID lookup reports the server's own failure, not a missing pass

    private class LookupRepository(context: Context, private val error: NetworkResult.Error) : StaffRepository(context) {
        var createCalls = 0

        override suspend fun verifyScan(request: TransactionRequest): NetworkResult<ScanVerificationResponse> = error

        override suspend fun createTransaction(
            request: TransactionRequest,
            purposeCode: ScanPurposeCode,
        ): NetworkResult<TransactionResponse> {
            createCalls++
            error("a failed lookup must never be recorded")
        }
    }

    private fun httpError(code: Int, message: String): NetworkResult.Error {
        val body = """{"success":false,"message":"$message"}""".toResponseBody("application/json".toMediaType())
        return NetworkResult.Error(message, HttpException(Response.error<Any>(code, body)), message)
    }

    private fun recordLookupFailure(view: RecordingView, repository: LookupRepository, dispatcher: kotlinx.coroutines.CoroutineDispatcher) {
        val presenter = ScannerPresenter(view, repository, UiStrings(context), CoroutineScope(dispatcher))
        try {
            presenter.recordScan(eventId, purpose(), "12345", null, UUID.randomUUID())
            val deadline = System.currentTimeMillis() + 5_000
            while (view.scanErrors.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        } finally {
            presenter.detach()
        }
    }

    @Test
    fun serverFailureDuringLookup_keepsTheServerReasonInsteadOfClaimingNoMatch() {
        val view = RecordingView()
        val repository = LookupRepository(context, httpError(500, "Server exploded"))
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            recordLookupFailure(view, repository, dispatcher)
        } finally {
            dispatcher.close()
        }

        assertEquals(listOf("Server exploded"), view.scanErrors.toList())
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun unknownShortId_stillSaysNoPassMatchesThatId() {
        val view = RecordingView()
        val repository = LookupRepository(context, httpError(404, "Pass not found"))
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            recordLookupFailure(view, repository, dispatcher)
        } finally {
            dispatcher.close()
        }

        assertEquals(listOf(UiStrings(context).get(R.string.ui_staff_scan_no_matching_pass)), view.scanErrors.toList())
        assertEquals(0, repository.createCalls)
    }

    // ---- an event switch cancels the submit that belonged to the event just left

    /** Parks inside the save so the event switch can cancel it before its response is produced. */
    private class GatedRecordRepository(context: Context) : StaffRepository(context) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        override suspend fun createTransaction(
            request: TransactionRequest,
            purposeCode: ScanPurposeCode,
        ): NetworkResult<TransactionResponse> {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            return NetworkResult.Error("late failure")
        }
    }

    @Test
    fun aSubmitCancelledByAnEventSwitchNeverReachesTheScreen() {
        val repository = GatedRecordRepository(context)
        val view = RecordingView()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val presenter = ScannerPresenter(view, repository, UiStrings(context), CoroutineScope(dispatcher))
        try {
            presenter.recordScan(
                eventId,
                purpose(),
                "44444444-4444-4444-4444-444444444444",
                null,
                UUID.randomUUID(),
            )
            assertTrue("the save never started", repository.entered.await(2, TimeUnit.SECONDS))

            presenter.cancelScan()
            repository.release.countDown()
            Thread.sleep(300)

            // The response belongs to the event just left: neither its outcome nor its failure may
            // paint under the event now selected.
            assertTrue(view.recorded.isEmpty())
            assertTrue(view.recordFailures.isEmpty())
        } finally {
            presenter.detach()
            dispatcher.close()
        }
    }
}
