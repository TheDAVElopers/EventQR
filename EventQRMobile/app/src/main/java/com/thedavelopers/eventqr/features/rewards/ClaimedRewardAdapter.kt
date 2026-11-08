package com.thedavelopers.eventqr.features.rewards

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.attendee.AttendeeBadges
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse

/**
 * Claimed-reward rows (§6.2). Status is the one badge; the amount is a tabular `numeric_s` value in
 * the row-value tone. A rejected claim carries a note instead of an amount, so it is never read as
 * a charge.
 *
 * Reflow (§4.6): at `fontScale > 1.3` the trailing amount and badge move out of the row end into a
 * host under the date, so the text column keeps its width instead of collapsing behind a fixed
 * trailing stack.
 */
class ClaimedRewardAdapter : RecyclerView.Adapter<ClaimedRewardAdapter.ViewHolder>() {

    private companion object {
        /** Above this font scale a trailing stack cannot share the row with a readable title. */
        const val TRAILING_REFLOW_FONT_SCALE = 1.3f
    }

    private val items = mutableListOf<RewardRedemptionResponse>()
    private var eventTitle: String? = null
    private var rewardNamesById: Map<String, String> = emptyMap()
    private var eventTitlesById: Map<String, String> = emptyMap()

    fun submitItems(
        newItems: List<RewardRedemptionResponse>,
        eventTitle: String?,
        rewardNamesById: Map<String, String>,
        eventTitlesById: Map<String, String> = emptyMap(),
    ) {
        items.clear()
        items.addAll(newItems)
        this.eventTitle = eventTitle
        this.rewardNamesById = rewardNamesById
        this.eventTitlesById = eventTitlesById
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_claimed_reward, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position == itemCount - 1)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val statusText: TextView = itemView.findViewById(R.id.txtClaimedStatus)
        private val titleText: TextView = itemView.findViewById(R.id.txtClaimedTitle)
        private val eventText: TextView = itemView.findViewById(R.id.txtClaimedEvent)
        private val dateText: TextView = itemView.findViewById(R.id.txtClaimedDate)
        private val reasonText: TextView = itemView.findViewById(R.id.txtClaimedReason)
        private val pointsText: TextView = itemView.findViewById(R.id.txtClaimedPoints)
        private val divider: View = itemView.findViewById(R.id.claimedRewardDivider)
        private val rowContent: ViewGroup = itemView.findViewById(R.id.claimedRewardRowContent)
        private val trailingStack: ViewGroup = itemView.findViewById(R.id.claimedTrailingStack)
        private val trailingHostLarge: ViewGroup = itemView.findViewById(R.id.trailingHostLarge)

        fun bind(item: RewardRedemptionResponse, isLast: Boolean) {
            val context = itemView.context
            // The redeemed reward's name is the row title. A rejection note is not a title.
            // The names map can fail while the claims load: the title still says "Reward".
            val rewardName = rewardNamesById[item.rewardId.toString()]?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.ui_attendee_reward)
            val eventName = (eventTitlesById[item.eventId.toString()] ?: eventTitle).orEmpty()

            titleText.text = rewardName
            titleText.visibility = View.VISIBLE
            eventText.text = eventName
            eventText.visibility = if (eventName.isBlank()) View.GONE else View.VISIBLE
            if (item.redeemedAt != null) {
                dateText.text = DateFormatters.formatInstant(item.redeemedAt)
                dateText.visibility = View.VISIBLE
            } else {
                dateText.text = null
                dateText.visibility = View.GONE
            }

            // Only a rejection carries a note a reader needs; an `event:` value is legacy title data.
            val rejectionReason = item.reason
                ?.takeIf { item.status == RedemptionStatus.REJECTED && it.isNotBlank() && !it.trimStart().startsWith("event:", ignoreCase = true) }
            reasonText.text = rejectionReason
            reasonText.visibility = if (rejectionReason != null) View.VISIBLE else View.GONE

            if (item.status == RedemptionStatus.REJECTED) {
                // A note, not an amount: no points left the balance, so nothing is charged.
                pointsText.text = context.getString(R.string.claimed_reward_no_points_deducted)
                TextViewCompat.setTextAppearance(pointsText, R.style.TextAppearance_EventQR_Type_Micro)
                pointsText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_secondary))
            } else {
                pointsText.text = context.getString(
                    R.string.ui_attendee_points_spent,
                    kotlin.math.abs(item.pointsSpent),
                )
                TextViewCompat.setTextAppearance(pointsText, R.style.TextAppearance_EventQR_Type_NumericS)
                // A recycled row may still carry the rejection note's secondary tone; restore the
                // Widget.EventQR.Ui.ListRow.Value default. Red is reserved for the rejection note.
                pointsText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_primary))
            }

            arrangeTrailingStack(context.resources.configuration.fontScale > TRAILING_REFLOW_FONT_SCALE)

            divider.visibility = if (isLast) View.GONE else View.VISIBLE
            AttendeeBadges.bind(statusText, item.status)
        }

        /**
         * Moves the trailing value + badge between the row end and the host under the date.
         * Idempotent: a recycled row checks the current parent before moving, so it never stacks a
         * second copy or loses the stack.
         */
        private fun arrangeTrailingStack(reflow: Boolean) {
            val into = if (reflow) trailingHostLarge else rowContent
            if (trailingStack.parent !== into) {
                (trailingStack.parent as? ViewGroup)?.removeView(trailingStack)
                into.addView(
                    trailingStack,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        // CENTER_VERTICAL matters on a reflow -> standard move: the row is taller
                        // than the stack, so END alone would pin it to the top.
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        if (!reflow) {
                            marginStart = itemView.resources.getDimensionPixelSize(R.dimen.space_sm)
                        }
                    },
                )
            }
            trailingHostLarge.visibility = if (reflow) View.VISIBLE else View.GONE
        }
    }
}
