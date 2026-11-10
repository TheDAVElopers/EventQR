package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RewardStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The details screen keeps the claim outcome honest: a stale intent must never leave it reading
 * Available when the fresh read says the reward is gone or the claim state is unknown.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RewardDetailsActivityTest {

    private val rewardId = UUID.randomUUID()

    @Before
    fun clearSession() {
        // No user id: onCreate skips the network load, so each test drives the render callbacks itself.
        SessionManager(ApplicationProvider.getApplicationContext()).clearSession()
    }

    private fun build(): RewardDetailsActivity {
        val intent = Intent(ApplicationProvider.getApplicationContext(), RewardDetailsActivity::class.java)
            .putExtra(EXTRA_EVENT_ID, UUID.randomUUID().toString())
            .putExtra(EXTRA_REWARD_ID, rewardId.toString())
            .putExtra(EXTRA_REWARD_NAME, "Shirt")
            .putExtra(EXTRA_REWARD_POINTS, 50)
            .putExtra(EXTRA_REWARD_STATUS, RewardStatus.ACTIVE.name)
            .putExtra(EXTRA_REWARD_STOCK, 5)
        return Robolectric.buildActivity(RewardDetailsActivity::class.java, intent).create().get()
    }

    @Test
    fun missingReward_replacesStaleIntentAvailabilityWithUnavailable() {
        val activity = build()

        // The intent said ACTIVE with 5 left; the fresh unfiltered read no longer holds the reward.
        activity.renderRewards(emptyList(), emptySet())

        assertEquals(activity.getString(R.string.reward_details_unavailable), status(activity).text.toString())
        assertNotEquals(activity.getString(R.string.user_reward_details_available), status(activity).text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.warningBox).visibility)
    }

    @Test
    fun failedClaimRead_isTerminalClaimError_neverAvailable() {
        val activity = build()

        activity.onClaimStateCheckFailed()

        assertEquals(activity.getString(R.string.ui_attendee_claim_state_unknown), status(activity).text.toString())
        assertNotEquals(activity.getString(R.string.user_reward_details_available), status(activity).text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.warningBox).visibility)
    }

    @Test
    fun claimedRewardMissingFromClaimableList_readsClaimed() {
        val activity = build()

        activity.renderRewards(emptyList(), setOf(rewardId.toString()))

        assertEquals(activity.getString(R.string.ui_attendee_claimed), status(activity).text.toString())
    }

    private fun status(activity: RewardDetailsActivity) =
        activity.findViewById<TextView>(R.id.txtRewardStatus)
}
