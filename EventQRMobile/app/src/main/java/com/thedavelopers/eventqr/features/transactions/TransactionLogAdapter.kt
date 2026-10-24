package com.thedavelopers.eventqr.features.transactions

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.util.RelativeTimeUtils
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import java.util.Locale

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
        private val iconContainer: FrameLayout = itemView.findViewById(R.id.iconContainer)
        private val iconView: ImageView = itemView.findViewById(R.id.imgTypeIcon)
        private val attendeeNameView: TextView = itemView.findViewById(R.id.txtAttendeeName)
        private val typeLabelView: TextView = itemView.findViewById(R.id.txtTransactionType)
        private val relativeTimeView: TextView = itemView.findViewById(R.id.txtRelativeTime)
        private val exactTimeView: TextView = itemView.findViewById(R.id.txtExactTime)
        private val reasonView: TextView = itemView.findViewById(R.id.txtReason)
        private val pointsBadgeView: TextView = itemView.findViewById(R.id.txtPointsBadge)

        fun bind(item: TransactionResponse) {
            val isApproved = item.transactionResult == TransactionResult.APPROVED
            val brandPrimary = ContextCompat.getColor(itemView.context, R.color.primary)

            // Line 1: attendee name + transaction type label
            attendeeNameView.text = item.attendeeName?.takeIf { it.isNotBlank() } ?: "Attendee"
            typeLabelView.text = formatType(item.transactionType.name)

            // Icon per transaction type (brand color, no gradients)
            iconView.setImageResource(iconForType(item.transactionType))
            iconView.imageTintList = ColorStateList.valueOf(brandPrimary)
            iconContainer.setBackgroundResource(R.drawable.bg_transaction_icon_container)

            // Line 2: relative time; tap expands to exact scanned_at
            relativeTimeView.text = RelativeTimeUtils.formatRelative(item.scannedAt)
            exactTimeView.text = RelativeTimeUtils.formatFull(item.scannedAt)
            relativeTimeView.setOnClickListener {
                val expanded = exactTimeView.visibility == View.VISIBLE
                exactTimeView.visibility = if (expanded) View.GONE else View.VISIBLE
            }

            // Reason only for rejected scans
            val reason = item.reason?.takeIf { it.isNotBlank() }
            if (!isApproved && reason != null) {
                reasonView.visibility = View.VISIBLE
                reasonView.text = reason
            } else {
                reasonView.visibility = View.GONE
            }

            // Points badge only when delta != 0
            val points = item.pointsDelta
            if (points != 0) {
                pointsBadgeView.visibility = View.VISIBLE
                pointsBadgeView.text = if (points > 0) "+$points pts" else "$points pts"
                pointsBadgeView.setBackgroundResource(
                    if (points > 0) R.drawable.bg_points_chip_green else R.drawable.bg_points_chip_red
                )
                pointsBadgeView.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        if (points > 0) R.color.icon_tint_green else R.color.danger
                    )
                )
            } else {
                pointsBadgeView.visibility = View.GONE
            }
        }
    }

    private fun iconForType(type: TransactionType): Int = when (type) {
        TransactionType.ENTRY -> R.drawable.ic_qr_scan
        TransactionType.ATTENDANCE -> R.drawable.ic_staff_check
        TransactionType.BENEFIT_CLAIM -> R.drawable.ic_gift
        TransactionType.BOOTH_VISIT -> R.drawable.ic_location
        TransactionType.SESSION_VISIT -> R.drawable.ic_calendar
        TransactionType.REWARD_REDEMPTION_SCAN, TransactionType.REWARD_REDEMPTION -> R.drawable.ic_nav_gift
        TransactionType.EXIT -> R.drawable.ic_scan
        TransactionType.ID_PRINT -> R.drawable.ic_print
        TransactionType.REGISTRATION -> R.drawable.ic_nav_registered
    }

    private fun formatType(value: String): String = value
        .lowercase(Locale.US)
        .split('_')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.US) } }
}