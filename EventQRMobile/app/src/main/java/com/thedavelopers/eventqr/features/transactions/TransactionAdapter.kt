package com.thedavelopers.eventqr.features.transactions

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val items = mutableListOf<TransactionResponse>()
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMM d, h:mm a")
        .withZone(ZoneId.of("Asia/Manila"))

    fun submitItems(newItems: List<TransactionResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItems(): List<TransactionResponse> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.txtTransactionTitle)
        private val eventView: TextView = itemView.findViewById(R.id.txtTransactionEvent)
        private val timeView: TextView = itemView.findViewById(R.id.txtTransactionTime)
        private val pointsView: TextView = itemView.findViewById(R.id.txtTransactionPoints)
        private val tagView: TextView = itemView.findViewById(R.id.txtTransactionTag)
        private val iconLayout: FrameLayout = itemView.findViewById(R.id.layoutTransactionIcon)
        private val trendIcon: ImageView = itemView.findViewById(R.id.imgTransactionTrend)

        fun bind(item: TransactionResponse) {
            val isApproved = item.transactionResult.name == "APPROVED"
            val isEarned = item.pointsDelta >= 0
            val title = mapTransactionTitle(item.transactionType)

            titleView.text = title
            eventView.text = item.eventTitle.orEmpty()
            timeView.text = item.scannedAt?.let { displayFormatter.format(it) } ?: "-"

            if (!isApproved) {
                // A rejected/failed scan awards nothing; never render it as a green "+0 pts".
                pointsView.text = "Rejected"
                pointsView.setTextColor(itemView.context.color(R.color.danger))
            } else if (item.pointsDelta == 0) {
                pointsView.text = itemView.context.getString(R.string.transaction_no_points)
                pointsView.setTextColor(itemView.context.color(R.color.text_secondary))
            } else {
                val deltaPrefix = if (isEarned) "+" else ""
                pointsView.text = "$deltaPrefix${item.pointsDelta} pts"
                pointsView.setTextColor(if (isEarned) itemView.context.color(R.color.success_dark) else itemView.context.color(R.color.danger))
            }

            tagView.text = if (isApproved) "Success" else "Failed"
            tagView.setBackgroundResource(if (isApproved) R.drawable.bg_green_pill else R.drawable.bg_red_warning)
            tagView.setTextColor(if (isApproved) itemView.context.color(R.color.on_success_container) else itemView.context.color(R.color.error))

            val iconColor = resolveIconColor(item.transactionType)
            val background = (iconLayout.background as? GradientDrawable)?.mutate() as? GradientDrawable
            if (background != null) {
                background.setColor(iconColor)
                iconLayout.background = background
            } else {
                iconLayout.setBackgroundResource(if (isEarned) R.drawable.bg_transaction_earned_icon else R.drawable.bg_transaction_redeemed_icon)
            }
            trendIcon.setImageResource(if (isEarned) R.drawable.ic_trend_up else R.drawable.ic_trend_down)
            trendIcon.setColorFilter(if (isEarned) itemView.context.color(R.color.primary) else itemView.context.color(R.color.success_dark))
        }

        private fun mapTransactionTitle(type: TransactionType): String {
            return when (type) {
                TransactionType.ENTRY -> "Event Entry"
                TransactionType.ATTENDANCE -> "Session Attendance"
                TransactionType.BOOTH_VISIT -> "Booth Visit"
                TransactionType.REWARD_REDEMPTION_SCAN -> "Reward Redemption Scan"
                TransactionType.REWARD_REDEMPTION -> "Reward Redeemed"
                TransactionType.EXIT -> "Event Exit"
                else -> type.name.lowercase().replace('_', ' ').split(' ').joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }

        private fun resolveIconColor(type: TransactionType): Int {
            return when (type) {
                TransactionType.ENTRY -> itemView.context.color(R.color.accent_container_strong)
                TransactionType.ATTENDANCE -> itemView.context.color(R.color.secondary_container)
                TransactionType.BOOTH_VISIT -> itemView.context.color(R.color.info_container)
                TransactionType.REWARD_REDEMPTION,
                TransactionType.REWARD_REDEMPTION_SCAN -> itemView.context.color(R.color.success_container)
                TransactionType.EXIT -> itemView.context.color(R.color.surface_muted)
                else -> itemView.context.color(R.color.accent_container)
            }
        }
    }
}