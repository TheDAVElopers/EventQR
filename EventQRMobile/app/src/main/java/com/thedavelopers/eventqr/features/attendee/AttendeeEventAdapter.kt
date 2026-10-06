package com.thedavelopers.eventqr.features.attendee

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.ui.components.EventCardHolder

class AttendeeEventAdapter(
    private val onClick: (AttendeeEventResponse) -> Unit,
) : RecyclerView.Adapter<AttendeeEventAdapter.ViewHolder>() {

    private val items = mutableListOf<AttendeeEventResponse>()

    fun submitItems(newItems: List<AttendeeEventResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(EventCardHolder(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val holder: EventCardHolder) :
        RecyclerView.ViewHolder(holder.view) {

        fun bind(item: AttendeeEventResponse) {
            val status = EventStatusBadgeStyler.resolve(item.status, item.eventStartAt, item.eventEndAt)
            val date = EventCardPresenter.dateParts(item.eventStartAt)

            holder.update(
                title = item.title,
                status = EventStatusBadgeStyler.displayLabel(status),
                day = date.day,
                month = date.month,
                time = date.time,
                location = EventCardPresenter.location(item.location),
                count = item.currentAttendeeCount,
                capacity = EventCardPresenter.capacity(item.capacity),
                onClick = { onClick(item) },
            )
        }
    }
}
