package com.thedavelopers.eventqr.features.transactions

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

class TransactionLogAdapter : RecyclerView.Adapter<TransactionLogAdapter.ViewHolder>() {

    private val items = mutableListOf<TransactionResponse>()

    fun submitItems(newItems: List<TransactionResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userNameView: TextView = itemView.findViewById(R.id.txtUserName)
        private val eventNameView: TextView = itemView.findViewById(R.id.txtEventName)
        private val transactionIdView: TextView = itemView.findViewById(R.id.txtTransactionId)
        private val statusBadgeView: TextView = itemView.findViewById(R.id.txtStatusBadge)
        private val timeView: TextView = itemView.findViewById(R.id.txtTransactionTime)
        private val purposeNameView: TextView = itemView.findViewById(R.id.txtPurposeName)
        private val footerIconView: ImageView = itemView.findViewById(R.id.imgFooterIcon)
        private val footerMessageView: TextView = itemView.findViewById(R.id.txtFooterMessage)
        private val userAvatarView: ImageView = itemView.findViewById(R.id.imgUserAvatar)

        fun bind(item: TransactionResponse) {
            userNameView.text = "Attendee: ${item.attendeeUserId.toString().take(8)}"
            eventNameView.text = "Event ID: ${item.eventId.toString().take(8)}"
            transactionIdView.text = "TXN-${item.transactionId.toString().take(8).uppercase()}"
            
            val isSuccess = item.transactionResult.name == "APPROVED" || item.transactionResult.name == "SUCCESS"
            
            statusBadgeView.text = if (isSuccess) "Accepted" else "Rejected"
            statusBadgeView.setBackgroundResource(if (isSuccess) R.drawable.bg_accepted_badge else R.drawable.bg_rejected_badge)
            statusBadgeView.setTextColor(if (isSuccess) Color.parseColor("#059669") else Color.parseColor("#D97706"))
            
            timeView.text = DateFormatters.formatInstant(item.scannedAt).replace(" ", "\n")
            
            purposeNameView.text = item.transactionType.name.lowercase().capitalize().replace("_", " ")
            
            footerMessageView.text = if (isSuccess) "Scan processed successfully" else (item.reason ?: "Scan rejected by system")
            footerMessageView.setTextColor(if (isSuccess) Color.parseColor("#059669") else Color.parseColor("#DC2626"))
            footerIconView.imageTintList = ColorStateList.valueOf(if (isSuccess) Color.parseColor("#059669") else Color.parseColor("#DC2626"))
            
            userAvatarView.backgroundTintList = ColorStateList.valueOf(if (isSuccess) Color.parseColor("#E0F2F1") else Color.parseColor("#FFEDD5"))
        }
    }
}
