package com.thedavelopers.eventqr.features.attendee

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.ui.components.ListRowBinder

/**
 * Discover's rows are `list_row_date` (§5.5): a 44dp neutral date block, title + meta, one
 * trailing `StatusBadge`, hairline divider. The row opens the event detail; there is no card,
 * no progress bar and no per-row action.
 */
class AttendeeEventAdapter(
    private val onClick: (AttendeeEventResponse) -> Unit,
) : RecyclerView.Adapter<AttendeeEventAdapter.ViewHolder>() {

    private val items = mutableListOf<AttendeeEventResponse>()

    init {
        setHasStableIds(true)
    }

    fun submitItems(newItems: List<AttendeeEventResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        return items[position].eventId.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_row_date, parent, false)
        return ViewHolder(row)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val row: View) : RecyclerView.ViewHolder(row) {

        fun bind(item: AttendeeEventResponse) {
            val date = EventCardPresenter.dateParts(item.eventStartAt)
            row.findViewById<TextView>(R.id.listRowDay).text = date.day
            row.findViewById<TextView>(R.id.listRowMonth).text = date.month

            ListRowBinder.bind(
                row,
                title = item.title.ifBlank { row.context.getString(R.string.common_untitled_event) },
                meta = metaFor(item, date.time),
                trailing = ListRowBinder.Trailing.BADGE,
                onClick = { onClick(item) },
            )
            AttendeeBadges.bind(
                row.findViewById(R.id.listRowBadge),
                EventStatusBadgeStyler.resolve(item.status, item.eventStartAt, item.eventEndAt),
            )
        }

        /** Time then venue, with the unknown sentinels dropped; both missing names the gap. */
        private fun metaFor(item: AttendeeEventResponse, time: String): String {
            val location = EventCardPresenter.location(item.location)
            val parts = listOf(time, location)
                .filter { it != EventCardPresenter.UNKNOWN_TIME && it != EventCardPresenter.UNKNOWN_LOCATION }
            return if (parts.isEmpty()) {
                row.context.getString(R.string.ui_attendee_date_tbd)
            } else {
                parts.joinToString(" - ")
            }
        }
    }
}
