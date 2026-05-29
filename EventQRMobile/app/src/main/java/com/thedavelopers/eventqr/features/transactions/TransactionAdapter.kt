package com.thedavelopers.eventqr.features.transactions

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

class TransactionAdapter(private val eventTitle: String? = null) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val items = mutableListOf<TransactionResponse>()

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
            val isEarned = item.pointsDelta >= 0
            
            titleView.text = item.reason ?: (if (isEarned) "Points Earned" else "Points Redeemed")
            
            eventView.text = item.eventTitle?.takeIf { it.isNotBlank() } ?: eventTitle ?: "Event"
            timeView.text = DateFormatters.formatInstant(item.scannedAt)
            
            val deltaPrefix = if (isEarned) "+" else ""
            pointsView.text = "$deltaPrefix${item.pointsDelta}"
            pointsView.setTextColor(if (isEarned) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
            
            tagView.text = if (item.transactionResult.name == "APPROVED") "Success" else "Failed"
            tagView.setBackgroundResource(if (item.transactionResult.name == "APPROVED") R.drawable.bg_green_pill else R.drawable.bg_red_warning)
            tagView.setTextColor(if (item.transactionResult.name == "APPROVED") Color.parseColor("#059669") else Color.parseColor("#DC2626"))
            
            iconLayout.setBackgroundResource(if (isEarned) R.drawable.bg_transaction_earned_icon else R.drawable.bg_transaction_redeemed_icon)
            trendIcon.setImageResource(if (isEarned) R.drawable.ic_trend_up else R.drawable.ic_trend_down)
        }
    }
}