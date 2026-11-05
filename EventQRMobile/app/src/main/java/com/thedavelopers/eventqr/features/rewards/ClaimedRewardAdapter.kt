package com.thedavelopers.eventqr.features.rewards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.features.attendee.AttendeeBadges
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.ui.icons.AppIcons
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Claimed-reward rows (§6.2). Status is the one badge; the amount is a tabular `numeric_s` value in
 * the error tone because it is points leaving the balance. A rejected claim carries a note instead
 * of an amount, so it is never read as a charge.
 */
class ClaimedRewardAdapter : RecyclerView.Adapter<ClaimedRewardAdapter.ViewHolder>() {

    private val items = mutableListOf<RewardRedemptionResponse>()
    private var eventTitle: String? = null
    private var rewardNamesById: Map<String, String> = emptyMap()
    private var eventTitlesById: Map<String, String> = emptyMap()
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("d MMM yyyy, h:mm a")
        .withZone(ZoneId.of("Asia/Manila"))

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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.imgClaimedGift)
        private val statusText: TextView = itemView.findViewById(R.id.txtClaimedStatus)
        private val titleText: TextView = itemView.findViewById(R.id.txtClaimedTitle)
        private val eventText: TextView = itemView.findViewById(R.id.txtClaimedEvent)
        private val dateText: TextView = itemView.findViewById(R.id.txtClaimedDate)
        private val pointsText: TextView = itemView.findViewById(R.id.txtClaimedPoints)

        fun bind(item: RewardRedemptionResponse) {
            val context = itemView.context
            val rewardName = rewardNamesById[item.rewardId.toString()]
                ?: item.reason?.takeIf { it.isNotBlank() }
                .orEmpty()
            val eventName = item.reason?.takeIf { it.startsWith("event:", ignoreCase = true) }?.substringAfter(":")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: eventTitlesById[item.eventId.toString()]
                ?: eventTitle.orEmpty()

            titleText.text = rewardName
            eventText.text = eventName
            if (item.redeemedAt != null) {
                dateText.text = displayFormatter.format(item.redeemedAt)
                dateText.visibility = View.VISIBLE
            } else {
                dateText.text = null
                dateText.visibility = View.GONE
            }

            if (item.status == RedemptionStatus.REJECTED) {
                // A note, not an amount: no points left the balance, so nothing is charged.
                pointsText.text = context.getString(R.string.claimed_reward_no_points_deducted)
                pointsText.setTextAppearance(R.style.TextAppearance_EventQR_Type_Micro)
                pointsText.setTextColor(ContextCompat.getColor(context, R.color.ui_text_secondary))
            } else {
                pointsText.text = context.getString(
                    R.string.ui_attendee_points_spent,
                    kotlin.math.abs(item.pointsSpent),
                )
                pointsText.setTextAppearance(R.style.TextAppearance_EventQR_Type_NumericS)
                pointsText.setTextColor(ContextCompat.getColor(context, R.color.ui_error))
            }

            iconView.setImageResource(AppIcons.Reward)
            iconView.setColorFilter(ContextCompat.getColor(context, R.color.ui_primary))
            AttendeeBadges.bind(statusText, item.status)
        }
    }
}
