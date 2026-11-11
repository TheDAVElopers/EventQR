package com.thedavelopers.eventqr.features.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.icons.AppIcons
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One date format for every transaction row, attendee history and staff scan results alike. */
internal val transactionDisplayFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
    .withZone(ZoneId.of("Asia/Manila"))

/**
 * The attendee transaction-history row (§5.5, item_transaction.xml). Hierarchy comes from size and
 * weight, not from tinted icon backgrounds; only a rejected scan uses the error colour, and the
 * amount is the single trailing element.
 */
class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

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
        fun bind(item: TransactionResponse) = bindScanTransactionRow(itemView, item)
    }
}

/** Binds a scan row; shared with the attendee history adapter that mixes scans and point rows. */
internal fun bindScanTransactionRow(itemView: View, item: TransactionResponse) {
    val context = itemView.context
    val titleView: TextView = itemView.findViewById(R.id.txtTransactionTitle)
    val eventView: TextView = itemView.findViewById(R.id.txtTransactionEvent)
    val timeView: TextView = itemView.findViewById(R.id.txtTransactionTime)
    val pointsView: TextView = itemView.findViewById(R.id.txtTransactionPoints)
    val trendIcon: ImageView = itemView.findViewById(R.id.imgTransactionTrend)

    val isApproved = item.transactionResult.name == "APPROVED"
    val isEarned = item.pointsDelta >= 0

    titleView.text = mapTransactionTitle(titleView, item.transactionType)
    eventView.text = item.eventTitle?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.common_untitled_event)
    timeView.text = item.scannedAt?.let { transactionDisplayFormatter.format(it) }
        ?: context.getString(R.string.dashboard_value_unavailable)

    trendIcon.setImageResource(if (isEarned) AppIcons.TrendUp else AppIcons.TrendDown)
    trendIcon.setColorFilter(
        ContextCompat.getColor(context, if (isApproved) R.color.ui_primary else R.color.ui_error),
    )

    when {
        !isApproved -> {
            // A rejected scan awards nothing; never render it as a green "+0 pts".
            pointsView.text = context.getString(R.string.common_rejected)
            pointsView.setTextColor(ContextCompat.getColor(context, R.color.ui_error))
        }
        item.pointsDelta == 0 -> {
            pointsView.text = context.getString(R.string.transaction_no_points)
            pointsView.setTextColor(ContextCompat.getColor(context, R.color.ui_text_secondary))
        }
        else -> {
            val deltaPrefix = if (isEarned) "+" else ""
            pointsView.text = "$deltaPrefix${item.pointsDelta} pts"
            pointsView.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isEarned) R.color.ui_text_primary else R.color.ui_error,
                ),
            )
        }
    }
}

// One label per scan purpose, so a row never falls back to a raw enum name ("Id print").
internal fun mapTransactionTitle(view: TextView, type: TransactionType): String = when (type) {
    TransactionType.ENTRY -> view.context.getString(R.string.item_transaction_log_entry)
    TransactionType.ATTENDANCE -> view.context.getString(R.string.scan_purpose_type_session_attendance)
    TransactionType.BOOTH_VISIT -> view.context.getString(R.string.scan_purpose_type_booth_visit)
    TransactionType.SESSION_VISIT -> view.context.getString(R.string.scan_purpose_type_session_visit)
    TransactionType.BENEFIT_CLAIM -> view.context.getString(R.string.scan_purpose_type_benefit_claim)
    TransactionType.REWARD_REDEMPTION_SCAN, TransactionType.REWARD_REDEMPTION ->
        view.context.getString(R.string.scan_purpose_type_reward_redemption)
    TransactionType.EXIT -> view.context.getString(R.string.scan_purpose_type_event_exit)
    TransactionType.ID_PRINT -> view.context.getString(R.string.scan_purpose_type_id_print)
    TransactionType.REGISTRATION -> view.context.getString(R.string.ui_staff_result_registration)
}
