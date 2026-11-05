package com.thedavelopers.eventqr.features.transactions

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.util.RelativeTimeUtils
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.icons.AppIcons
import java.util.Locale

/**
 * The one transaction-log row (§5.5, item_transaction_log.xml). Hairline rows, one trailing value,
 * a 40dp icon tile, and colour only where it means something (rejected scans).
 */
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
        private val row: View = itemView.findViewById(R.id.listRowContainer)
        private val iconView: ImageView = itemView.findViewById(R.id.imgTypeIcon)
        private val attendeeNameView: TextView = itemView.findViewById(R.id.txtAttendeeName)
        private val typeLabelView: TextView = itemView.findViewById(R.id.txtTransactionType)
        private val relativeTimeView: TextView = itemView.findViewById(R.id.txtRelativeTime)
        private val exactTimeView: TextView = itemView.findViewById(R.id.txtExactTime)
        private val reasonView: TextView = itemView.findViewById(R.id.txtReason)
        private val pointsView: TextView = itemView.findViewById(R.id.txtPointsBadge)

        fun bind(item: TransactionResponse) {
            val context = itemView.context
            val isApproved = item.transactionResult == TransactionResult.APPROVED

            attendeeNameView.text = item.attendeeName?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.ui_staff_logs_attendee_fallback)
            typeLabelView.text = item.scanPurposeName?.takeIf { it.isNotBlank() }
                ?: formatType(item.transactionType.name)

            iconView.setImageResource(iconForType(item.transactionType))
            ImageViewCompat.setImageTintList(
                iconView,
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        if (isApproved) R.color.ui_primary else R.color.ui_error,
                    ),
                ),
            )

            // Relative time is the row's line; tapping the row reveals the exact timestamp once.
            relativeTimeView.text = RelativeTimeUtils.formatRelative(item.scannedAt)
            exactTimeView.text = RelativeTimeUtils.formatFull(item.scannedAt)
            exactTimeView.visibility = View.GONE
            row.setOnClickListener {
                exactTimeView.visibility =
                    if (exactTimeView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }

            val reason = item.reason?.takeIf { it.isNotBlank() }
            if (!isApproved && reason != null) {
                reasonView.visibility = View.VISIBLE
                reasonView.text = reason
            } else {
                reasonView.visibility = View.GONE
            }

            val points = item.pointsDelta
            if (points == 0) {
                pointsView.visibility = View.GONE
            } else {
                pointsView.visibility = View.VISIBLE
                pointsView.text = if (points > 0) "+$points pts" else "$points pts"
                pointsView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (points > 0) R.color.ui_text_primary else R.color.ui_error,
                    ),
                )
            }
        }
    }

    private fun iconForType(type: TransactionType): Int = when (type) {
        TransactionType.ENTRY -> AppIcons.QrCode
        TransactionType.ATTENDANCE -> AppIcons.Check
        TransactionType.BENEFIT_CLAIM -> AppIcons.Reward
        TransactionType.BOOTH_VISIT -> AppIcons.Location
        TransactionType.SESSION_VISIT -> AppIcons.Event
        TransactionType.REWARD_REDEMPTION_SCAN, TransactionType.REWARD_REDEMPTION -> AppIcons.Reward
        TransactionType.EXIT -> AppIcons.Close
        TransactionType.ID_PRINT -> AppIcons.Print
        TransactionType.REGISTRATION -> AppIcons.Pass
    }

    private fun formatType(value: String): String = value
        .lowercase(Locale.US)
        .split('_')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.US) } }
}
