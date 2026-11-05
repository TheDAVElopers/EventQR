package com.thedavelopers.eventqr.features.rewards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.attendee.AttendeeBadges
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse

/**
 * Reward rows (§6.2). The cost is a tabular `numeric_s` value, the state is the one badge, and the
 * meta line is the reward's own description (or the stock, when the organizer wrote none).
 */
class RewardAdapter(
    private val onClick: (RewardResponse) -> Unit,
) : RecyclerView.Adapter<RewardAdapter.ViewHolder>() {

    private val items = mutableListOf<RewardResponse>()

    fun submitItems(newItems: List<RewardResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reward, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.txtRewardTitle)
        private val pointsView: TextView = itemView.findViewById(R.id.txtRewardPoints)
        private val metaView: TextView = itemView.findViewById(R.id.txtRewardStock)
        private val statusView: TextView = itemView.findViewById(R.id.txtRewardStatus)

        fun bind(item: RewardResponse) {
            val context = itemView.context
            titleView.text = item.name
            pointsView.text = context.getString(R.string.ui_attendee_points_cost, item.pointsRequired.toString())

            val stockLabel = item.stockQuantity?.let {
                context.getString(R.string.ui_attendee_stock_left, it)
            } ?: context.getString(R.string.ui_attendee_stock_unlimited)
            metaView.text = item.description?.takeIf { it.isNotBlank() } ?: stockLabel

            AttendeeBadges.bind(statusView, item.status)
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
