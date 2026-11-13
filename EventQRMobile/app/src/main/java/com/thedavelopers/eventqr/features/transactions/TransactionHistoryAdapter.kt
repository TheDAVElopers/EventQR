package com.thedavelopers.eventqr.features.transactions

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.icons.AppIcons

/**
 * Attendee history rows (§5.5, item_transaction.xml): scan rows keep their existing binding, point
 * rows name the activity that produced them so a manual adjustment never reads as a scan.
 */
class TransactionHistoryAdapter : RecyclerView.Adapter<TransactionHistoryAdapter.ViewHolder>() {

    private val items = mutableListOf<TransactionHistoryRow>()

    fun submitItems(newItems: List<TransactionHistoryRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: TransactionHistoryRow) = when (item) {
            is TransactionHistoryRow.Scan -> bindScanTransactionRow(itemView, item.transaction)
            is TransactionHistoryRow.Point -> bindPointRow(item)
        }

        private fun bindPointRow(item: TransactionHistoryRow.Point) {
            val context = itemView.context
            val titleView = itemView.findViewById<TextView>(R.id.txtTransactionTitle)
            val eventView = itemView.findViewById<TextView>(R.id.txtTransactionEvent)
            val timeView = itemView.findViewById<TextView>(R.id.txtTransactionTime)
            val pointsView = itemView.findViewById<TextView>(R.id.txtTransactionPoints)
            val trendIcon = itemView.findViewById<ImageView>(R.id.imgTransactionTrend)

            val isEarned = item.pointsDelta >= 0

            titleView.text = pointReasonLabel(context, item.reason, item.pointsDelta)
            eventView.text = item.eventTitle?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.common_untitled_event)
            timeView.text = item.occurredAt?.let { transactionDisplayFormatter.format(it) }
                ?: context.getString(R.string.dashboard_value_unavailable)

            trendIcon.setImageResource(if (isEarned) AppIcons.TrendUp else AppIcons.TrendDown)
            trendIcon.setColorFilter(ContextCompat.getColor(context, R.color.ui_primary))

            when {
                item.pointsDelta == 0 -> {
                    pointsView.text = context.getString(R.string.transaction_no_points)
                    pointsView.setTextColor(ContextCompat.getColor(context, R.color.ui_text_secondary))
                }
                else -> {
                    pointsView.text = context.getString(
                        if (isEarned) R.string.ui_points_gain else R.string.common_points_short,
                        item.pointsDelta,
                    )
                    pointsView.setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (isEarned) R.color.ui_text_primary else R.color.ui_error,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * A point row's title. The backend defaults ("Manual point assignment"/"Manual point deduction"/
 * "Reward redemption") become localized labels; an organizer's own reason is shown verbatim.
 */
internal fun pointReasonLabel(context: Context, reason: String?, pointsDelta: Int): String {
    val trimmed = reason?.trim().orEmpty()
    return when {
        trimmed.equals("Reward redemption", ignoreCase = true) ->
            context.getString(R.string.ui_attendee_reward_redemption)
        trimmed.isEmpty() ||
            trimmed.equals("Manual point assignment", ignoreCase = true) ||
            trimmed.equals("Manual point deduction", ignoreCase = true) ->
            context.getString(
                if (pointsDelta >= 0) R.string.ui_attendee_points_added else R.string.ui_attendee_points_deducted,
            )
        else -> trimmed
    }
}
