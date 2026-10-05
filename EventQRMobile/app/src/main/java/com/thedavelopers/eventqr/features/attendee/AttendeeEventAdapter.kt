package com.thedavelopers.eventqr.features.attendee

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.features.events.EventCardBinder
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class AttendeeEventAdapter(
    private val onClick: (AttendeeEventResponse) -> Unit,
) : RecyclerView.Adapter<AttendeeEventAdapter.ViewHolder>() {

    private val items = mutableListOf<AttendeeEventResponse>()
    private val manilaZone = ZoneId.of("Asia/Manila")
    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    fun submitItems(newItems: List<AttendeeEventResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val composeView = ComposeView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        return ViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        fun bind(item: AttendeeEventResponse) {
            val status = EventStatusBadgeStyler.resolve(item.status, item.eventStartAt, item.eventEndAt)
            val day: String
            val month: String
            val time: String

            if (item.eventStartAt != null) {
                val zonedDateTime = item.eventStartAt.atZone(manilaZone)
                day = zonedDateTime.dayOfMonth.toString()
                month = zonedDateTime.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
                time = zonedDateTime.format(timeFormatter)
            } else {
                day = "--"
                month = "---"
                time = "-"
            }

            val location = item.location?.takeIf { it.isNotBlank() } ?: "Location not set"
            val capacity = item.capacity.coerceAtLeast(1)
            val current = item.currentAttendeeCount
            val percent = (current.toFloat() / capacity.toFloat() * 100).toInt().coerceIn(0, 100)

            EventCardBinder.bind(
                view = composeView,
                title = item.title,
                status = status.name,
                day = day,
                month = month,
                time = time,
                location = location,
                count = current,
                capacity = capacity,
                percent = percent,
                onClick = { onClick(item) },
            )
        }
    }
}
