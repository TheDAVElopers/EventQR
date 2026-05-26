package com.thedavelopers.eventqr.features.rewards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse

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
        private val detailView: TextView = itemView.findViewById(R.id.txtRewardDetails)
        private val pointsView: TextView = itemView.findViewById(R.id.txtRewardPoints)

        fun bind(item: RewardResponse) {
            titleView.text = item.name
            detailView.text = "Official ${item.name} for the event" // Placeholder description
            pointsView.text = "🪙 ${item.pointsRequired} pts"

            itemView.setOnClickListener { onClick(item) }
        }
    }
}