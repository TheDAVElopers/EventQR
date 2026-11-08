package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ClaimedRewardsActivityTest {

    private val eventId: UUID = UUID.randomUUID()

    private fun registration() = RegistrationResponse(
        registrationId = UUID.randomUUID(), eventId = eventId, attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "a@test.com", attendeeName = "A", status = RegistrationStatus.REGISTERED,
        eventTitle = "DevFest Manila",
    )

    private fun claim() = RewardRedemptionResponse(
        redemptionId = UUID.randomUUID(), eventId = eventId, attendeeUserId = UUID.randomUUID(),
        rewardId = UUID.randomUUID(), pointsSpent = 100, status = RedemptionStatus.REDEEMED,
    )

    /** Only the three calls this screen makes are faked; the rest of the repository is never reached. */
    private class FakeRepository(context: Context) : AttendeeRepository(context) {
        var registrations: NetworkResult<List<RegistrationResponse>> = NetworkResult.Success(emptyList())
        val claims = mutableMapOf<String, NetworkResult<List<RewardRedemptionResponse>>>()

        override suspend fun getMyRegistrations(): NetworkResult<List<RegistrationResponse>> = registrations

        override suspend fun getMyRewardRedemptions(eventId: String): NetworkResult<List<RewardRedemptionResponse>> =
            claims[eventId] ?: NetworkResult.Success(emptyList())

        override suspend fun getRewardsByEvent(eventId: String, includeUnavailable: Boolean): NetworkResult<List<RewardResponse>> =
            NetworkResult.Success(emptyList())
    }

    private fun build(repository: FakeRepository): ClaimedRewardsActivity {
        val controller = Robolectric.buildActivity(ClaimedRewardsActivity::class.java)
        controller.get().repositoryOverride = repository
        controller.create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    private fun ClaimedRewardsActivity.recycler() =
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerClaimedRewards)

    private fun ClaimedRewardsActivity.empty() =
        findViewById<EventQrEmptyState>(R.id.txtClaimedRewardsEmpty)

    private fun View.textViews(): List<TextView> = when (this) {
        is TextView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).textViews() }
        else -> emptyList()
    }

    /** The Material Snackbar root, added to the content view when one is shown. */
    private fun View.snackbar(): ViewGroup? {
        if (this::class.java.name.contains("Snackbar") && this is ViewGroup) return this
        if (this is ViewGroup) return (0 until childCount).firstNotNullOfOrNull { getChildAt(it).snackbar() }
        return null
    }

    private fun ClaimedRewardsActivity.clickText(label: String) {
        val target = window.decorView.textViews()
            .firstOrNull { it.isShown && it.isClickable && it.text == label }
        assertNotNull("no clickable \"$label\" on screen", target)
        target!!.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun firstLoadSuccess_rendersTheRows() {
        val repository = FakeRepository(ApplicationProvider.getApplicationContext()).apply {
            registrations = NetworkResult.Success(listOf(registration()))
            claims[eventId.toString()] = NetworkResult.Success(listOf(claim()))
        }

        val activity = build(repository)

        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.VISIBLE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.empty().visibility)
    }

    @Test
    fun firstLoadFailure_showsTheInFlowErrorWithRetry_thenRetryLoads() {
        val repository = FakeRepository(ApplicationProvider.getApplicationContext()).apply {
            registrations = NetworkResult.Error("offline")
        }

        val activity = build(repository)

        assertEquals(View.VISIBLE, activity.empty().visibility)
        assertEquals(activity.getString(R.string.ui_attendee_claims_error_title), activity.empty().text.toString())
        assertEquals("offline", activity.empty().subtext.toString())
        assertEquals(View.GONE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.errorClaimedRewards).visibility)

        repository.registrations = NetworkResult.Success(listOf(registration()))
        repository.claims[eventId.toString()] = NetworkResult.Success(listOf(claim()))
        activity.clickText(activity.getString(R.string.ui_attendee_retry))

        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.VISIBLE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.empty().visibility)
    }

    @Test
    fun refreshFailureWithRows_keepsTheRowsAndShowsTheInFlowErrorStrip() {
        val repository = FakeRepository(ApplicationProvider.getApplicationContext()).apply {
            registrations = NetworkResult.Success(listOf(registration()))
            claims[eventId.toString()] = NetworkResult.Success(listOf(claim()))
        }
        val activity = build(repository)
        assertEquals(1, activity.recycler().adapter!!.itemCount)
        val strip = activity.findViewById<View>(R.id.errorClaimedRewards)
        assertEquals(View.GONE, strip.visibility)

        activity.showError("offline")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.empty().visibility)
        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.VISIBLE, strip.visibility)
        assertEquals(
            activity.getString(R.string.ui_attendee_claims_refresh_failed),
            activity.findViewById<TextView>(R.id.txtClaimedRewardsError).text.toString(),
        )
        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.btnClaimedRewardsRetry).visibility,
        )
        assertNull("an in-flow error block must not raise a Snackbar", activity.window.decorView.snackbar())

        // The inline Retry re-runs the load and the new claims replace the rows.
        repository.claims[eventId.toString()] = NetworkResult.Success(listOf(claim(), claim()))
        activity.findViewById<View>(R.id.btnClaimedRewardsRetry).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, activity.recycler().adapter!!.itemCount)
        assertEquals(View.GONE, strip.visibility)
    }

    @Test
    fun emptyThenErrorThenPopulated_movesThroughAllThreeStates() {
        val repository = FakeRepository(ApplicationProvider.getApplicationContext()).apply {
            registrations = NetworkResult.Success(emptyList())
        }
        val activity = build(repository)
        assertEquals(View.VISIBLE, activity.empty().visibility)
        assertEquals(activity.getString(R.string.ui_attendee_no_claimed_title), activity.empty().text.toString())

        activity.showError("offline")
        assertEquals(activity.getString(R.string.ui_attendee_claims_error_title), activity.empty().text.toString())

        repository.registrations = NetworkResult.Success(listOf(registration()))
        repository.claims[eventId.toString()] = NetworkResult.Success(listOf(claim()))
        activity.clickText(activity.getString(R.string.ui_attendee_retry))

        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.VISIBLE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.empty().visibility)
    }
}
