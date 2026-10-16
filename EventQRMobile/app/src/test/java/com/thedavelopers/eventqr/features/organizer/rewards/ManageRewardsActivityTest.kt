package com.thedavelopers.eventqr.features.organizer.rewards

import android.app.AlertDialog
import android.os.Looper
import com.thedavelopers.eventqr.core.api.dto.ApiResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.allTexts
import com.thedavelopers.eventqr.features.organizer.findButton
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.robolectric.Shadows.shadowOf
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ManageRewardsActivityTest {

    private fun build(): ManageRewardsActivity =
        Robolectric.buildActivity(ManageRewardsActivity::class.java).create().get()

    private fun View.findSwipeRefresh(): SwipeRefreshLayout? {
        if (this is SwipeRefreshLayout) return this
        if (this is ViewGroup) {
            for (i in 0 until childCount) getChildAt(i).findSwipeRefresh()?.let { return it }
        }
        return null
    }

    // ---- N1 / O-8: pull-to-refresh before the event list has loaded

    @Test
    fun pullToRefreshIsDisabledUntilAnEventIsResolved() {
        val activity = build()
        val refresh = activity.window.decorView.findSwipeRefresh()

        assertNotNull(refresh)
        assertFalse(refresh!!.isEnabled)
    }

    @Test
    fun refreshBeforeAnEventIsSelected_doesNotCrashAndStopsTheSpinner() {
        val activity = build()
        val refresh = activity.window.decorView.findSwipeRefresh()!!
        refresh.isRefreshing = true

        // Used to throw UninitializedPropertyAccessException on selectedEvent.
        activity.loadRewards(showInitialLoading = false)

        assertFalse(refresh.isRefreshing)
    }

    // ---- O-9: invalid reward input keeps the dialog open with inline errors

    private fun openDialog(activity: ManageRewardsActivity): AlertDialog {
        activity.showRewardDialog(null)
        return ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
    }

    @Test
    fun emptyRewardForm_staysOpenWithInlineErrorsOnTitleAndPoints() {
        val activity = build()
        val dialog = openDialog(activity)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        assertTrue(dialog.isShowing)
        assertEquals(
            activity.getString(R.string.manage_rewards_title_required),
            dialog.findViewById<EditText>(R.id.mrw_reward_title_input).error?.toString(),
        )
        assertEquals(
            activity.getString(R.string.manage_rewards_points_invalid),
            dialog.findViewById<EditText>(R.id.mrw_reward_points_input).error?.toString(),
        )
        assertNull(dialog.findViewById<EditText>(R.id.mrw_reward_quantity_input).error)
    }

    @Test
    fun zeroPointsAndBadQuantity_keepTheTypedTitleAndFlagOnlyTheBadFields() {
        val activity = build()
        val dialog = openDialog(activity)
        val title = dialog.findViewById<EditText>(R.id.mrw_reward_title_input)
        title.setText("Free T-shirt")
        dialog.findViewById<EditText>(R.id.mrw_reward_points_input).setText("0")
        dialog.findViewById<EditText>(R.id.mrw_reward_quantity_input).setText("-3")

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        assertTrue(dialog.isShowing)
        assertEquals("Free T-shirt", title.text.toString())
        assertNull(title.error)
        assertEquals(
            activity.getString(R.string.manage_rewards_points_invalid),
            dialog.findViewById<EditText>(R.id.mrw_reward_points_input).error?.toString(),
        )
        assertEquals(
            activity.getString(R.string.manage_rewards_quantity_invalid),
            dialog.findViewById<EditText>(R.id.mrw_reward_quantity_input).error?.toString(),
        )
    }

    // ---- review: in-flight save keeps the dialog locked, failure unlocks it, success closes it

    private class FakeRewardsService : OrganizerRewardsService {
        val createResult = CompletableDeferred<ApiResponse<RewardResponse>>()
        var createCalls = 0
        override suspend fun getRewards(eventId: String) = ApiResponse<List<RewardResponse>>(success = true, data = emptyList())
        override suspend fun getRewardSettings(eventId: String) = ApiResponse(success = true, data = true)
        override suspend fun updateRewardSettings(eventId: String, request: RewardSettingsRequest) =
            ApiResponse<EventResponse>(success = false)
        override suspend fun createReward(eventId: String, request: RewardRequest): ApiResponse<RewardResponse> {
            createCalls++
            return createResult.await()
        }
        override suspend fun updateReward(eventId: String, rewardId: String, request: RewardRequest) =
            ApiResponse<RewardResponse>(success = false)
        override suspend fun deleteReward(eventId: String, rewardId: String) = ApiResponse<Unit>(success = false)
        override suspend fun getClaimedRewards(eventId: String) =
            ApiResponse<List<RewardRedemptionResponse>>(success = true, data = emptyList())
    }

    private fun mvpEvent() = OrganizerMvpEvent(
        id = UUID.randomUUID().toString(), title = "Fest", organizerName = "Org", dateTime = "-", shortDate = "-",
        venue = "Hall", status = "APPROVED", submittedDate = "-", adminRemarks = "", additionalOrganizers = emptyList(),
        registeredCount = 0, enteredCount = 0, attendedCount = 0, exitedCount = 0, noShowCount = 0,
        totalTransactions = 0, successfulScans = 0, rejectedScans = 0, benefitClaims = 0, boothSessionVisits = 0,
        rewardRedemptions = 0, totalPointsAwarded = 0, idTemplateStatus = "-", rewardsStatus = "Enabled",
        staffCount = 0, scanPurposesCount = 0,
    )

    /** Screen bound to an event with the fake service, a valid form filled in and Create tapped. */
    private fun submitValidReward(service: FakeRewardsService): Pair<ManageRewardsActivity, AlertDialog> {
        val activity = build()
        activity.rewardsServiceOverride = service
        activity.selectedEvent = mvpEvent()
        activity.buildScreen()
        val dialog = openDialog(activity)
        dialog.findViewById<EditText>(R.id.mrw_reward_title_input).setText("Free T-shirt")
        dialog.findViewById<EditText>(R.id.mrw_reward_points_input).setText("50")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to dialog
    }

    @Test
    fun whileSaving_dialogCannotBeSavedAgainCancelledOrDismissed() {
        val service = FakeRewardsService()
        val (_, dialog) = submitValidReward(service)

        assertEquals(1, service.createCalls)
        assertTrue(dialog.isShowing)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        assertFalse(shadowOf(dialog).isCancelable)
    }

    @Test
    fun saveRejected_unlocksTheDialogAndKeepsTheTypedValues() {
        val service = FakeRewardsService()
        val (_, dialog) = submitValidReward(service)

        service.createResult.complete(ApiResponse(success = false, message = "Name already used"))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(dialog.isShowing)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        assertTrue(shadowOf(dialog).isCancelable)
        assertEquals("Free T-shirt", dialog.findViewById<EditText>(R.id.mrw_reward_title_input).text.toString())
    }

    @Test
    fun saveAccepted_closesTheDialog() {
        val service = FakeRewardsService()
        val (_, dialog) = submitValidReward(service)

        service.createResult.complete(ApiResponse(success = true, message = "Saved"))
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(dialog.isShowing)
    }

    @Test
    fun destroyingTheScreen_dismissesAnOpenRewardDialog() {
        val controller = Robolectric.buildActivity(ManageRewardsActivity::class.java).create()
        val dialog = openDialog(controller.get())

        controller.destroy()

        assertFalse(dialog.isShowing)
    }

    // ---- N4: a failed organizer-events fetch is an error with Retry, not "No Events Available"

    private fun failedEvents(message: String?) =
        OrganizerMvpLoad(emptyList<OrganizerMvpEvent>(), OrganizerMvpDataSource.ERROR, message)

    @Test
    fun failedEventsLoad_showsErrorWithRetryAndEnablesPullToRefresh() {
        val activity = build()

        activity.onEventsLoaded(failedEvents("Network problem. Check your connection and try again."))

        val texts = activity.window.decorView.allTexts()
        assertTrue(texts.contains("Network problem. Check your connection and try again."))
        assertFalse(texts.contains(activity.getString(R.string.manage_rewards_no_events_title)))
        assertNotNull(activity.window.decorView.findButton(activity.getString(R.string.organizer_retry)))
        assertTrue(activity.window.decorView.findSwipeRefresh()!!.isEnabled)
    }

    @Test
    fun failedEventsLoadWithoutMessage_usesTheGenericEventsLoadError() {
        val activity = build()

        activity.onEventsLoaded(failedEvents(null))

        assertTrue(activity.window.decorView.allTexts().contains(activity.getString(R.string.organizer_dashboard_events_load_failed)))
    }

    @Test
    fun retryAfterFailedEventsLoad_clearsTheError() {
        val activity = build()
        activity.onEventsLoaded(failedEvents("Timeout"))

        activity.window.decorView.findButton(activity.getString(R.string.organizer_retry))!!.performClick()

        assertFalse(activity.window.decorView.allTexts().contains("Timeout"))
        // The spinner shows the retry is running (and SwipeRefreshLayout ignores pulls meanwhile).
        assertTrue(activity.window.decorView.findSwipeRefresh()!!.isRefreshing)
    }

    @Test
    fun pullToRefreshAfterFailedEventsLoad_retriesInsteadOfCrashing() {
        val activity = build()
        activity.onEventsLoaded(failedEvents("Timeout"))
        val refresh = activity.window.decorView.findSwipeRefresh()!!

        // A pull with no event selected yet retries the events load (it used to reach loadRewards and crash).
        refresh.isRefreshing = true
        val listener = SwipeRefreshLayout::class.java.getDeclaredField("mListener")
            .apply { isAccessible = true }.get(refresh) as SwipeRefreshLayout.OnRefreshListener
        listener.onRefresh()
        // ...and the retried load's (still failing) result stops the spinner and keeps the error on screen.
        activity.onEventsLoaded(failedEvents("Still offline"))

        assertFalse(refresh.isRefreshing)
        assertTrue(activity.window.decorView.allTexts().contains("Still offline"))
    }

    @Test
    fun successfulEmptyEventsLoad_stillShowsNoEventsAvailable() {
        val activity = build()

        activity.onEventsLoaded(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.BACKEND))

        assertTrue(activity.window.decorView.allTexts().contains(activity.getString(R.string.manage_rewards_no_events_title)))
        assertFalse(activity.window.decorView.findSwipeRefresh()!!.isEnabled)
    }
}
