package com.thedavelopers.eventqr.features.rewards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse

class ClaimedRewardAdapter : RecyclerView.Adapter<ClaimedRewardAdapter.ViewHolder>() {

    private val items = mutableListOf<RewardRedemptionResponse>()

    fun submitItems(newItems: List<RewardRedemptionResponse>) {
        items.clear()
        items.addAll(newItems)
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
        private val categoryText: TextView = itemView.findViewById(R.id.txtClaimedCategory)
        private val statusText: TextView = itemView.findViewById(R.id.txtClaimedStatus)
        private val titleText: TextView = itemView.findViewById(R.id.txtClaimedTitle)
        private val detailText: TextView = itemView.findViewById(R.id.txtClaimedDetails)

        fun bind(item: RewardRedemptionResponse) {
            // Since RewardRedemptionResponse might not have reward name/category directly in this DTO, 
            // we'd normally need a joined DTO. For now, we'll use IDs or placeholders if necessary.
            // Assuming the UI layout has these IDs:
            titleText.text = "Reward ID: ${item.rewardId.toString().take(8)}"
            categoryText.text = "Redemption"
            statusText.text = item.status.name
            
            detailText.text = buildString {
                append("Points Used: ")
                append(item.pointsSpent)
                append("\nRedeemed At: ")
                append(DateFormatters.formatInstant(item.redeemedAt))
                append("\nRef: ")
                append(item.redemptionId.toString().take(12))
            }
        }
    }
}
