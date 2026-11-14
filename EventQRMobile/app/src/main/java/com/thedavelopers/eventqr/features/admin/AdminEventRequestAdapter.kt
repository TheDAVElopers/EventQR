package com.thedavelopers.eventqr.features.admin

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.features.events.requesterDisplay
import com.thedavelopers.eventqr.ui.components.ListRowBinder
import com.thedavelopers.eventqr.ui.components.MetricRowStacking

/**
 * Admin request rows (§6.5): a flat 88dp page row, not the attendee's [EventRequestCard]. The
 * requester leads, the event and submitted date are the meta, and one status badge trails the text
 * column as its own cell so the title keeps the full column width.
 *
 * TalkBack: one node per row (`{event}. {status}. {requester}. {submitted}`) whose click is named
 * `Open request`; the children are `importantForAccessibility="no"`, so nothing is read twice.
 */
class AdminEventRequestAdapter(
    private val onTap: (EventRequestResponse) -> Unit,
) : RecyclerView.Adapter<AdminEventRequestAdapter.AdminEventRequestViewHolder>() {

    private val rows = mutableListOf<EventRequestResponse>()

    fun submit(items: List<EventRequestResponse>) {
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminEventRequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_event_request, parent, false)
        return AdminEventRequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdminEventRequestViewHolder, position: Int) {
        holder.bind(rows[position], onTap)
    }

    override fun getItemCount(): Int = rows.size

    class AdminEventRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val row: ViewGroup = itemView.findViewById(R.id.requestRow)
        private val textColumn: ViewGroup = itemView.findViewById(R.id.textRequestColumn)
        private val textAvatar: TextView = itemView.findViewById(R.id.textRequestAvatar)
        private val textTitle: TextView = itemView.findViewById(R.id.textRequestTitle)
        private val textRequester: TextView = itemView.findViewById(R.id.textRequestRequester)
        private val textSubmitted: TextView = itemView.findViewById(R.id.textRequestSubmitted)
        private val textStatus: TextView = itemView.findViewById(R.id.textRequestStatus)

        fun bind(
            request: EventRequestResponse,
            onTap: (EventRequestResponse) -> Unit,
        ) {
            val context = itemView.context
            val requester = requesterDisplay(
                request.requesterName,
                request.contactEmail,
                request.requesterUserId,
                context.getString(R.string.ui_request_detail_unknown_requester),
            )
            val title = request.eventName.ifBlank { context.getString(R.string.common_untitled_event) }
            val submitted = context.getString(
                R.string.ui_request_detail_submitted_on,
                DateFormatters.formatEventDate(request.createdAt),
            )
            val status = context.getString(labelFor(request.status))

            textAvatar.text = requester.trim().take(1).uppercase().ifBlank { "U" }
            textTitle.text = title
            textRequester.text = requester
            textSubmitted.text = submitted
            textStatus.text = status
            // Text appearance never tints the container, so apply the whole tone, not the text alone.
            ListRowBinder.applyBadgeTone(textStatus, toneFor(request.status))

            // One TalkBack node for the row: the visible order, no tone, no repeated status word.
            row.contentDescription = context.getString(
                R.string.ui_admin_request_row_description,
                title,
                status,
                requester,
                submitted,
            )
            row.setOnClickListener { onTap(request) }
            row.accessibilityDelegate = RequestRowAccessibility

            placeBadge(MetricRowStacking.stacks(context.resources.configuration.fontScale))
        }

        /**
         * §6.5 reflow: above fontScale 1.3 the badge leaves the row's end for the text column's last
         * line, because a trailing badge beside a grown title leaves too little width for the name.
         * Idempotent: every parameter is absolute, so a recycled row never stacks a second badge.
         */
        private fun placeBadge(stacked: Boolean) {
            val into = if (stacked) textColumn else row
            if (textStatus.parent !== into) {
                (textStatus.parent as? ViewGroup)?.removeView(textStatus)
                into.addView(textStatus)
            }
            val gap = itemView.resources.getDimensionPixelSize(R.dimen.space_8)
            textStatus.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = if (stacked) Gravity.START else Gravity.CENTER_VERTICAL
                marginStart = if (stacked) 0 else gap
                topMargin = if (stacked) gap else 0
            }
        }

        @StringRes
        private fun labelFor(status: EventRequestStatus): Int = when (status) {
            EventRequestStatus.PENDING -> R.string.ui_attendee_pending_review
            EventRequestStatus.APPROVED -> R.string.common_approved
            EventRequestStatus.REJECTED -> R.string.common_rejected
        }

        @StyleRes
        private fun toneFor(status: EventRequestStatus): Int = when (status) {
            EventRequestStatus.PENDING -> R.style.Widget_EventQR_Ui_StatusBadge_Warning
            EventRequestStatus.APPROVED -> R.style.Widget_EventQR_Ui_StatusBadge_Success
            EventRequestStatus.REJECTED -> R.style.Widget_EventQR_Ui_StatusBadge_Error
        }
    }
}

/**
 * The row is one node with the Button role, and its click names its outcome (Voice Access shows
 * `Open request` instead of the generic `Activate`). A chip cannot reuse this: a chip's visible
 * label is already its action name, so the shared `ButtonRoleDelegate` stays label-free.
 */
private object RequestRowAccessibility : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = Button::class.java.name
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_CLICK,
                host.context.getString(R.string.ui_admin_requests_open),
            ),
        )
    }
}
