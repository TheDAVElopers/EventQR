package com.thedavelopers.eventqr.features.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AdminEventRequestAdapter(
    private val onTap: (EventRequestResponse) -> Unit,
) : RecyclerView.Adapter<AdminEventRequestAdapter.AdminEventRequestViewHolder>() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Manila"))
    private val submittedFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.of("Asia/Manila"))
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
        holder.bind(rows[position], onTap, dateFormatter, submittedFormatter)
    }

    override fun getItemCount(): Int = rows.size

    class AdminEventRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textStatus: TextView = itemView.findViewById(R.id.textStatus)
        private val textDescription: TextView = itemView.findViewById(R.id.textDescription)
        private val textEventDate: TextView = itemView.findViewById(R.id.textEventDate)
        private val textCapacity: TextView = itemView.findViewById(R.id.textCapacity)
        private val textSubmitted: TextView = itemView.findViewById(R.id.textSubmitted)

        fun bind(
            request: EventRequestResponse,
            onTap: (EventRequestResponse) -> Unit,
            dateFormatter: DateTimeFormatter,
            submittedFormatter: DateTimeFormatter,
        ) {
            textTitle.text = request.eventName.ifBlank { "Untitled Event" }
            textDescription.text = request.eventDescription?.takeIf { it.isNotBlank() }
                ?: "No description provided."

            textEventDate.text = formatDate(request.startDateTime, dateFormatter)
            textCapacity.text = "${request.capacity} expected"
            textSubmitted.text = "Submitted ${formatDate(request.createdAt, submittedFormatter)}"
            bindStatus(textStatus, request.status)

            itemView.setOnClickListener { onTap(request) }
        }

        private fun bindStatus(view: TextView, status: EventRequestStatus) {
            when (status) {
                EventRequestStatus.PENDING -> {
                    view.text = "Pending"
                    view.setBackgroundResource(R.drawable.bg_admin_pending_badge)
                    view.setTextColor(0xFF92400E.toInt())
                }
                EventRequestStatus.APPROVED -> {
                    view.text = "Approved"
                    view.setBackgroundResource(R.drawable.bg_admin_approved_badge)
                    view.setTextColor(0xFF065F46.toInt())
                }
                EventRequestStatus.REJECTED -> {
                    view.text = "Rejected"
                    view.setBackgroundResource(R.drawable.bg_admin_rejected_badge)
                    view.setTextColor(0xFF991B1B.toInt())
                }
            }
        }

        private fun formatDate(value: Instant?, formatter: DateTimeFormatter): String {
            return if (value == null) "Not available" else formatter.format(value)
        }
    }
}