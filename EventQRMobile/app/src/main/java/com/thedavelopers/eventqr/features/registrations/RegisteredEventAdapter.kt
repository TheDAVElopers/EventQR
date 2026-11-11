package com.thedavelopers.eventqr.features.registrations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.features.attendee.AttendeeBadges
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.ui.components.ListRowBinder

/**
 * My passes rows are `list_row_date` (§5.11) at the Discover density: a 44dp neutral date block,
 * title + "time - venue" meta, one trailing badge derived from the event window, hairline
 * divider. The row opens the event detail; the pass lives on the detail's primary action, so there
 * is no per-row button and no card.
 */
class RegisteredEventAdapter(
    private val onClick: (RegistrationResponse) -> Unit,
) : RecyclerView.Adapter<RegisteredEventAdapter.ViewHolder>() {

    private val items = mutableListOf<RegistrationResponse>()

    init {
        setHasStableIds(true)
    }

    fun submitItems(newItems: List<RegistrationResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        // Guarded for the pre-layout pass; the namespace keeps the id stable against other adapters.
        if (position !in items.indices) return RecyclerView.NO_ID
        return ("reg:${items[position].registrationId}").hashCode().toLong()
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

        fun bind(item: RegistrationResponse) {
            val date = EventCardPresenter.dateParts(item.eventStartAt)
            row.findViewById<TextView>(R.id.listRowDay).text = date.day
            row.findViewById<TextView>(R.id.listRowMonth).text = date.month

            ListRowBinder.bind(
                row,
                title = item.eventTitle?.takeIf { it.isNotBlank() }
                    ?: row.context.getString(R.string.common_untitled_event),
                meta = metaFor(item, date.time),
                trailing = ListRowBinder.Trailing.BADGE,
                onClick = { onClick(item) },
            )
            // Same three states as the filter chips: Upcoming / Active / Completed.
            AttendeeBadges.bind(
                row.findViewById(R.id.listRowBadge),
                EventStatusBadgeStyler.resolve(null, item.eventStartAt, item.eventEndAt),
            )
        }

        /** Time then venue, with the unknown sentinels dropped; both missing names the gap. */
        private fun metaFor(item: RegistrationResponse, time: String): String {
            val location = EventCardPresenter.location(item.eventLocation)
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
