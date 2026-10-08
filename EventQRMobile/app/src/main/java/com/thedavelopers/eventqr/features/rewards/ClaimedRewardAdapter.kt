package com.thedavelopers.eventqr.features.rewards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ClaimedRewardAdapter : RecyclerView.Adapter<ClaimedRewardAdapter.ViewHolder>() {

    private val items = mutableListOf<RewardRedemptionResponse>()
    private var eventTitle: String? = null
    private var rewardNamesById: Map<String, String> = emptyMap()
    private var eventTitlesById: Map<String, String> = emptyMap()
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMM d, h:mm a")
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

        // Rows are recycled, so remember the layout's amount style and restore it for every non-rejected bind.
        private val amountSizePx = pointsText.textSize
        private val amountColor = pointsText.currentTextColor
        private val amountTypeface = pointsText.typeface

        fun bind(item: RewardRedemptionResponse) {
            val rewardName = rewardNamesById[item.rewardId.toString()]
                ?: item.reason?.takeIf { it.isNotBlank() }
                .orEmpty()
            val eventName = item.reason?.takeIf { it.startsWith("event:", ignoreCase = true) }?.substringAfter(":")
                ?.trim()?.takeIf { it.isNotBlank() }
                ?: eventTitlesById[item.eventId.toString()]
                ?: eventTitle.orEmpty()

            titleText.text = rewardName
            eventText.text = eventName
            dateText.text = item.redeemedAt?.let { displayFormatter.format(it) } ?: "-"
            if (item.status == RedemptionStatus.REJECTED) {
                // A note, not an amount: small and muted instead of the big red "-N pts" style.
                pointsText.text = itemView.context.getString(R.string.claimed_reward_no_points_deducted)
                pointsText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                pointsText.setTextColor(android.graphics.Color.parseColor("#6B7280"))
                pointsText.setTypeface(amountTypeface, android.graphics.Typeface.NORMAL)
            } else {
                pointsText.text = "-${kotlin.math.abs(item.pointsSpent)} pts"
                pointsText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, amountSizePx)
                pointsText.setTextColor(amountColor)
                pointsText.typeface = amountTypeface
            }

            iconView.setImageResource(R.drawable.ic_gift)
            iconView.setColorFilter(android.graphics.Color.parseColor("#12B981"))

            val (statusLabel, statusBackground, statusColor) = mapStatusStyle(item.status)
            statusText.text = statusLabel
            statusText.setBackgroundResource(statusBackground)
            statusText.setTextColor(statusColor)
        }

        private fun mapStatusStyle(status: RedemptionStatus): Triple<String, Int, Int> {
            return when (status) {
                RedemptionStatus.REDEEMED -> Triple(
                    "Approved",
                    R.drawable.bg_green_pill,
                    android.graphics.Color.parseColor("#047857")
                )

                RedemptionStatus.PENDING -> Triple(
                    "Pending",
                    R.drawable.bg_claimed_status_pending,
                    android.graphics.Color.parseColor("#92400E")
                )

                RedemptionStatus.REJECTED -> Triple(
                    "Rejected",
                    R.drawable.bg_red_warning,
                    android.graphics.Color.parseColor("#B91C1C")
                )
            }
        }
    }
}
