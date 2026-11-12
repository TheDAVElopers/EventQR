package com.thedavelopers.eventqr.features.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

/**
 * Admin request rows (§6.5): a flat 88dp page row, not the attendee's [EventRequestCard]. The
 * requester leads, the event and submitted date are the meta, and one status badge trails.
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
            textAvatar.text = requester.trim().take(1).uppercase().ifBlank { "U" }
            textTitle.text = request.eventName.ifBlank { context.getString(R.string.common_untitled_event) }
            textRequester.text = requester
            textSubmitted.text = context.getString(
                R.string.ui_request_detail_submitted_on,
                DateFormatters.formatEventDate(request.createdAt),
            )
            textStatus.setText(labelFor(request.status))
            // Text appearance never tints the container, so apply the whole tone, not the text alone.
            ListRowBinder.applyBadgeTone(textStatus, toneFor(request.status))
            itemView.setOnClickListener { onTap(request) }
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
