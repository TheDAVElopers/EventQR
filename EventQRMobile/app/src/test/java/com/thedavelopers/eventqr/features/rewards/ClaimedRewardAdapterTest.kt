package com.thedavelopers.eventqr.features.rewards

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ClaimedRewardAdapterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>().also { it.setTheme(R.style.Theme_EventQR) }

    private fun claim(status: RedemptionStatus, points: Int) = RewardRedemptionResponse(
        redemptionId = UUID.randomUUID(), eventId = UUID.randomUUID(), attendeeUserId = UUID.randomUUID(),
        rewardId = UUID.randomUUID(), pointsSpent = points, status = status,
    )

    private fun bindRow(adapter: ClaimedRewardAdapter, position: Int, holder: ClaimedRewardAdapter.ViewHolder? = null):
        ClaimedRewardAdapter.ViewHolder {
        val h = holder ?: adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(h, position)
        return h
    }

    @Test
    fun rejectedClaim_showsAQuietNoteNotABigRedAmount_andARecycledRowGetsItsAmountStyleBack() {
        val adapter = ClaimedRewardAdapter()
        adapter.submitItems(
            listOf(claim(RedemptionStatus.REJECTED, 200), claim(RedemptionStatus.REDEEMED, 50)),
            eventTitle = null, rewardNamesById = emptyMap(),
        )

        val holder = bindRow(adapter, 0)
        val points = holder.itemView.findViewById<TextView>(R.id.txtClaimedPoints)
        val amountSize = run { // size of an approved row, bound on a fresh holder
            bindRow(adapter, 1).itemView.findViewById<TextView>(R.id.txtClaimedPoints).textSize
        }

        assertEquals(context.getString(R.string.claimed_reward_no_points_deducted), points.text.toString())
        assertNotEquals("note must be smaller than an amount", amountSize, points.textSize, 0.01f)

        // Recycle the same row for the approved claim: the amount style must come back.
        bindRow(adapter, 1, holder)
        assertEquals("-50 pts", points.text.toString())
        assertEquals(amountSize, points.textSize, 0.01f)
    }
}
