package com.thedavelopers.eventqr.features.rewards

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.core.api.dto.RewardStatus
import com.thedavelopers.eventqr.features.attendee.AttendeeBadges
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
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
    fun reasonNoteIsNeverTheTitle() {
        val adapter = ClaimedRewardAdapter()
        val claim = claim(RedemptionStatus.PENDING, 0).copy(reason = "event: DevFest Manila")
        adapter.submitItems(listOf(claim), eventTitle = null, rewardNamesById = emptyMap())

        val holder = bindRow(adapter, 0)
        val title = holder.itemView.findViewById<TextView>(R.id.txtClaimedTitle)

        // An unresolved reward id falls back to the generic title; the note never stands in for it.
        assertEquals(context.getString(R.string.ui_attendee_reward), title.text.toString())
        assertEquals(View.VISIBLE, title.visibility)
    }

    @Test
    fun rejectionReasonIsShownUnderTheDateOnlyForARejectedClaim() {
        val adapter = ClaimedRewardAdapter()
        adapter.submitItems(
            listOf(
                claim(RedemptionStatus.REJECTED, 0).copy(reason = "Already claimed this reward"),
                claim(RedemptionStatus.REJECTED, 0).copy(reason = "event: DevFest Manila"),
                claim(RedemptionStatus.PENDING, 0).copy(reason = "Still being reviewed"),
            ),
            eventTitle = null, rewardNamesById = emptyMap(),
        )

        val reason = bindRow(adapter, 0).itemView.findViewById<TextView>(R.id.txtClaimedReason)
        assertEquals("Already claimed this reward", reason.text.toString())
        assertEquals(View.VISIBLE, reason.visibility)

        // A legacy event: note, a blank reason, or any other status shows no rejection line.
        val legacy = bindRow(adapter, 1).itemView.findViewById<TextView>(R.id.txtClaimedReason)
        assertEquals(View.GONE, legacy.visibility)
        assertEquals("", legacy.text.toString())

        val pending = bindRow(adapter, 2).itemView.findViewById<TextView>(R.id.txtClaimedReason)
        assertEquals(View.GONE, pending.visibility)
        assertEquals("", pending.text.toString())
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

    @Test
    fun claimedAmountUsesTheRowValueTone_andRecyclingRestoresItAfterARejection() {
        val adapter = ClaimedRewardAdapter()
        adapter.submitItems(
            listOf(claim(RedemptionStatus.REJECTED, 200), claim(RedemptionStatus.REDEEMED, 50)),
            eventTitle = null, rewardNamesById = emptyMap(),
        )

        val holder = bindRow(adapter, 0)
        val points = holder.itemView.findViewById<TextView>(R.id.txtClaimedPoints)
        assertEquals(
            "a rejected claim's note is quiet",
            ContextCompat.getColor(context, R.color.ui_text_secondary),
            points.currentTextColor,
        )

        // A recycled row must not keep the note's tone: the amount is the row-value default.
        bindRow(adapter, 1, holder)
        assertEquals(
            ContextCompat.getColor(context, R.color.ui_text_primary),
            points.currentTextColor,
        )
    }

    /** REDEEMED keeps the agreed `Approved` label; only the tone changes by status. */
    @Test
    fun redeemedClaim_readsApproved() {
        val adapter = ClaimedRewardAdapter()
        adapter.submitItems(
            listOf(claim(RedemptionStatus.REDEEMED, 50)),
            eventTitle = null, rewardNamesById = emptyMap(),
        )

        val badge = bindRow(adapter, 0).itemView.findViewById<TextView>(R.id.txtClaimedStatus)
        assertEquals(context.getString(R.string.ui_attendee_redeemed), badge.text.toString())
    }

    /** `AVAILABLE` is inventory, not a confirmation: lime is credential-only (§5.4 line 597). */
    @Test
    fun availableRewardTone_isInfo_neverLime() {
        val tone = AttendeeBadges.tone(RewardStatus.ACTIVE)
        assertEquals(R.color.ui_info_container, tone.container)
        assertEquals(R.color.ui_on_info_container, tone.content)
    }

    /** Both maps are populated: the coupon name and the event title each come from their own map. */
    @Test
    fun rewardNameAndEventTitleComeFromTheirMaps() {
        val adapter = ClaimedRewardAdapter()
        val claim = claim(RedemptionStatus.REDEEMED, 50)
        adapter.submitItems(
            listOf(claim),
            eventTitle = "Fallback event",
            rewardNamesById = mapOf(claim.rewardId.toString() to "Event shirt"),
            eventTitlesById = mapOf(claim.eventId.toString() to "DevFest Manila"),
        )

        val row = bindRow(adapter, 0).itemView
        assertEquals("Event shirt", row.findViewById<TextView>(R.id.txtClaimedTitle).text.toString())
        assertEquals(
            "DevFest Manila",
            row.findViewById<TextView>(R.id.txtClaimedEvent).text.toString(),
        )
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.txtClaimedEvent).visibility)
    }

    /** Hairlines separate rows; the last row must not leave a trailing rule on the page. */
    @Test
    fun lastRow_hidesItsDivider() {
        val adapter = ClaimedRewardAdapter()
        adapter.submitItems(
            listOf(claim(RedemptionStatus.REDEEMED, 50), claim(RedemptionStatus.PENDING, 0)),
            eventTitle = null, rewardNamesById = emptyMap(),
        )

        val first = bindRow(adapter, 0).itemView.findViewById<View>(R.id.claimedRewardDivider)
        val last = bindRow(adapter, 1).itemView.findViewById<View>(R.id.claimedRewardDivider)
        assertEquals(View.VISIBLE, first.visibility)
        assertEquals(View.GONE, last.visibility)
    }

    @Test
    fun standardFont_keepsTheTrailingStackAtTheRowEnd() {
        val adapter = ClaimedRewardAdapter()
        adapter.submitItems(
            listOf(claim(RedemptionStatus.REDEEMED, 50)),
            eventTitle = null, rewardNamesById = emptyMap(),
        )

        val holder = bindRow(adapter, 0)
        val row = holder.itemView.findViewById<ViewGroup>(R.id.claimedRewardRowContent)
        val stack = holder.itemView.findViewById<ViewGroup>(R.id.claimedTrailingStack)
        val host = holder.itemView.findViewById<ViewGroup>(R.id.trailingHostLarge)

        assertSame(row, stack.parent)
        assertEquals(View.GONE, host.visibility)
        assertEquals(0, host.childCount)
        assertSame(
            holder.itemView.findViewById<View>(R.id.txtClaimedStatus),
            stack.getChildAt(stack.childCount - 1),
        )
    }

    @Test
    @Config(fontScale = 2.0f)
    fun largeFont_movesTheTrailingStackUnderTheDate_andRepeatedBindsNeverDuplicateIt() {
        val adapter = ClaimedRewardAdapter()
        adapter.submitItems(
            listOf(claim(RedemptionStatus.REDEEMED, 50)),
            eventTitle = null, rewardNamesById = emptyMap(),
        )

        val holder = bindRow(adapter, 0)
        val row = holder.itemView.findViewById<ViewGroup>(R.id.claimedRewardRowContent)
        val stack = holder.itemView.findViewById<ViewGroup>(R.id.claimedTrailingStack)
        val host = holder.itemView.findViewById<ViewGroup>(R.id.trailingHostLarge)

        assertSame(host, stack.parent)
        assertEquals(View.VISIBLE, host.visibility)
        assertEquals(1, host.childCount)
        assertEquals(-1, row.indexOfChild(stack))
        assertSame(
            holder.itemView.findViewById<View>(R.id.txtClaimedStatus),
            stack.getChildAt(stack.childCount - 1),
        )

        // Recycled / rebound rows hit the same parent check: no second stack, no lost stack.
        bindRow(adapter, 0, holder)
        assertSame(host, stack.parent)
        assertEquals(1, host.childCount)
        assertEquals(2, stack.childCount)
    }
}
