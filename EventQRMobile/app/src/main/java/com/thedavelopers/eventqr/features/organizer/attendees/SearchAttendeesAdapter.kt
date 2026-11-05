package com.thedavelopers.eventqr.features.organizer.attendees

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpAttendee
import com.thedavelopers.eventqr.features.organizer.attendeeInitial
import com.thedavelopers.eventqr.features.organizer.bindStatusBadge

/**
 * Search-result rows on the shared attendee geometry. The status tone comes from the one mapping
 * (§5.4); the adapter draws no pill and no card.
 */
class SearchAttendeesAdapter(
    private val onClick: (OrganizerMvpAttendee) -> Unit,
) : RecyclerView.Adapter<SearchAttendeesAdapter.ViewHolder>() {

    private val items = mutableListOf<OrganizerMvpAttendee>()

    fun submitItems(newItems: List<OrganizerMvpAttendee>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_attendee, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position == items.lastIndex)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val initial: TextView = itemView.findViewById(R.id.txtSearchAttendeeInitial)
        private val nameText: TextView = itemView.findViewById(R.id.txtSearchAttendeeName)
        private val emailText: TextView = itemView.findViewById(R.id.txtSearchAttendeeEmail)
        private val statusText: TextView = itemView.findViewById(R.id.txtSearchAttendeeStatus)

        fun bind(item: OrganizerMvpAttendee, isLast: Boolean) {
            initial.text = attendeeInitial(item.name)
            nameText.text = item.name
            emailText.text = item.email
            item.bindStatusBadge(statusText)
            itemView.findViewById<View>(R.id.searchAttendeeRowContainer).setOnClickListener { onClick(item) }
            itemView.findViewById<View>(R.id.searchAttendeeDivider).visibility =
                if (isLast) View.GONE else View.VISIBLE
        }
    }
}
