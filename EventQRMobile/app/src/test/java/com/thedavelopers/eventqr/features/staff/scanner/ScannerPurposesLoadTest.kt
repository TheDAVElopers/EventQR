package com.thedavelopers.eventqr.features.staff.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A fast event switch must not let the slower first purpose load overwrite the purposes of the
 * event now selected (§5.13). The two loads run on one real thread so the first response lands
 * after the second load has already been requested, which is exactly the race the event-id guard
 * covers when cancellation of the first job has no suspension point left to act on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ScannerPurposesLoadTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val slowEventId = "11111111-1111-1111-1111-111111111111"
    private val fastEventId = "22222222-2222-2222-2222-222222222222"

    private fun purpose(name: String, eventId: String) = ScanPurposeResponse(
        scanPurposeId = UUID.randomUUID(),
        eventId = UUID.fromString(eventId),
        name = name,
        code = ScanPurposeCode.ENTRY,
        active = true,
        trackingOnly = false,
    )

    private class RecordingView : ScannerContract.View {
        val rendered = Collections.synchronizedList(mutableListOf<List<ScanPurposeResponse>>())
        val messages = Collections.synchronizedList(mutableListOf<String>())
        val loading = Collections.synchronizedList(mutableListOf<Boolean>())
        val purposesUnavailable = Collections.synchronizedList(mutableListOf<String>())
        val scanErrors = Collections.synchronizedList(mutableListOf<String>())

        override fun showEvents(items: List<EventSpinnerOption>) = Unit
        override fun showEventsLoadFailed(message: String) {
            messages += message
        }
        override fun showPurposes(items: List<ScanPurposeResponse>) {
            rendered += items
        }
        override fun showPurposesUnavailable(message: String) {
            purposesUnavailable += message
        }
        override fun showRecordedResult(result: TransactionResponse) = Unit
        override fun showVerifiedResult(result: ScanVerificationResponse) = Unit
        override fun showRecordFailed(message: String) = Unit
        override fun showRewardRedemptionScanResult(result: RewardRedemptionScanResponse) = Unit
        override fun showScanError(message: String) {
            scanErrors += message
        }
        override fun showMessage(message: String) {
            messages += message
        }
        override fun showLoading(isLoading: Boolean) {
            loading += isLoading
        }
    }

    /**
     * Blocks inside a suspend call on purpose: no suspension means the cancelled job still reaches
     * the presenter's event-id guard, so the guard itself is what drops the stale response.
     */
    private class BlockingPurposesRepository(context: Context) : StaffRepository(context) {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        @Volatile var blockedEventId: String? = null
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var slowPurposes: List<ScanPurposeResponse> = emptyList()
        var fastPurposes: List<ScanPurposeResponse> = emptyList()

        override suspend fun getScanPurposesByEvent(eventId: String): NetworkResult<List<ScanPurposeResponse>> {
            calls += eventId
            if (eventId == blockedEventId) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                return NetworkResult.Success(slowPurposes)
            }
            return NetworkResult.Success(fastPurposes)
        }
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    @Test
    fun fastEventSwitch_dropsTheSlowerFirstPurposeResponse() {
        val repository = BlockingPurposesRepository(context).apply {
            slowPurposes = listOf(purpose("Slow event", slowEventId))
            fastPurposes = listOf(purpose("Fast event", fastEventId))
            blockedEventId = slowEventId
        }
        val view = RecordingView()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val presenter = ScannerPresenter(view, repository, UiStrings(context), CoroutineScope(dispatcher))
        try {
            presenter.loadPurposes(slowEventId)
            assertTrue("the slow load never started", repository.entered.await(2, TimeUnit.SECONDS))

            presenter.loadPurposes(fastEventId)
            repository.release.countDown()
            awaitUntil { view.rendered.isNotEmpty() }

            assertEquals(listOf(slowEventId, fastEventId), repository.calls.toList())
            assertEquals(listOf("Fast event"), view.rendered.map { it.single().name })
            assertTrue(view.messages.isEmpty())
        } finally {
            presenter.detach()
            dispatcher.close()
        }
    }

    /**
     * A failed load must reach the view as "no purposes", never as silence: the previous event's
     * purposes stay bound otherwise, and the next scan would post the new event under the old
     * event's `scanPurposeId`.
     */
    @Test
    fun failedPurposeLoad_tellsTheViewToDropTheListItStillHolds() {
        val repository = object : StaffRepository(context) {
            override suspend fun getScanPurposesByEvent(eventId: String) =
                NetworkResult.Error("Could not load scan purposes.")
        }
        val view = RecordingView()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val presenter = ScannerPresenter(view, repository, UiStrings(context), CoroutineScope(dispatcher))
        try {
            presenter.loadPurposes(fastEventId)
            awaitUntil { view.purposesUnavailable.isNotEmpty() }

            assertEquals(listOf("Could not load scan purposes."), view.purposesUnavailable.toList())
            assertTrue("a failed load must not bind a purpose list", view.rendered.isEmpty())
        } finally {
            presenter.detach()
            dispatcher.close()
        }
    }
}
