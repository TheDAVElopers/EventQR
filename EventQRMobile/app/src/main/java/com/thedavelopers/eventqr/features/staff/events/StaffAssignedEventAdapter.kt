package com.thedavelopers.eventqr.features.staff

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.ui.components.ListRowBinder

/**
 * One assigned event = one `list_row_date` (§6.3 amendment v1.13). 96dp, date block leading, title
 * up to two lines, one trailing status badge - the card and its two-button footer are deleted. The
 * row is the target and opens the event's registrations; scanning stays on the scanner's context
 * field and the dashboard's `Scan a pass`.
 */
class StaffAssignedEventAdapter(
    private val onClick: (StaffAssignedEventResponse) -> Unit,
) : RecyclerView.Adapter<StaffAssignedEventAdapter.ViewHolder>() {

    private val items = mutableListOf<StaffAssignedEventResponse>()

    fun submitItems(newItems: List<StaffAssignedEventResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_row_date, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position == items.lastIndex)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val row: View) : RecyclerView.ViewHolder(row) {

        fun bind(event: StaffAssignedEventResponse, isLast: Boolean) {
            val date = EventCardPresenter.dateParts(event.eventStartAt)
            row.findViewById<TextView>(R.id.listRowDay).text = date.day
            row.findViewById<TextView>(R.id.listRowMonth).text = date.month
            val status = EventStatusBadgeStyler.resolve(event.status, event.eventStartAt, event.eventEndAt)
            ListRowBinder.bind(
                row,
                title = event.title.ifBlank { row.context.getString(R.string.common_untitled_event) },
                meta = listOf(date.time, EventCardPresenter.location(event.location))
                    .filter {
                        it != EventCardPresenter.UNKNOWN_TIME && it != EventCardPresenter.UNKNOWN_LOCATION
                    }
                    .joinToString(" - "),
                trailing = ListRowBinder.Trailing.BADGE,
                trailingText = EventStatusBadgeStyler.displayLabel(status),
                badgeStyle = toneFor(status),
                showDivider = !isLast,
                onClick = { onClick(event) },
            )
        }

        /** §5.4: only `Rejected` is an error; Draft and Cancelled are neutral, not failures. */
        private fun toneFor(status: EventStatus): Int = when (status) {
            EventStatus.ACTIVE -> R.style.Widget_EventQR_Ui_StatusBadge_Valid
            EventStatus.APPROVED -> R.style.Widget_EventQR_Ui_StatusBadge_Info
            EventStatus.PENDING_REVIEW -> R.style.Widget_EventQR_Ui_StatusBadge_Warning
            EventStatus.REJECTED -> R.style.Widget_EventQR_Ui_StatusBadge_Error
            EventStatus.DRAFT, EventStatus.CANCELLED, EventStatus.ENDED, EventStatus.UNKNOWN ->
                R.style.Widget_EventQR_Ui_StatusBadge_Neutral
        }
    }
}
