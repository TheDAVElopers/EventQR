package com.thedavelopers.eventqr.features.organizer.attendees

import com.thedavelopers.eventqr.ui.theme.dimen
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpAttendee
import com.thedavelopers.eventqr.features.organizer.statusBucket
import com.thedavelopers.eventqr.features.organizer.statusLabel

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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.txtSearchAttendeeName)
        private val emailText: TextView = itemView.findViewById(R.id.txtSearchAttendeeEmail)
        private val statusText: TextView = itemView.findViewById(R.id.txtSearchAttendeeStatus)

        fun bind(item: OrganizerMvpAttendee) {
            nameText.text = item.name
            emailText.text = item.email

            val status = item.statusBucket()
            statusText.text = item.statusLabel(itemView.context)
            val (backgroundColor, textColor) = when (status) {
                "Checked In" -> itemView.context.color(R.color.success_container) to itemView.context.color(R.color.success_dark)
                "Registered" -> itemView.context.color(R.color.accent_container_strong) to itemView.context.color(R.color.primary)
                "Exited" -> itemView.context.color(R.color.outline) to itemView.context.color(R.color.text_secondary)
                "No Show" -> itemView.context.color(R.color.warning_container) to itemView.context.color(R.color.warning_dark)
                else -> itemView.context.color(R.color.outline) to itemView.context.color(R.color.text_body)
            }
            statusText.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = itemView.dimen(R.dimen.radius_pill)
                setColor(backgroundColor)
            }
            statusText.setPadding(statusText.paddingLeft, statusText.paddingTop, statusText.paddingRight, statusText.paddingBottom)
            when (status) {
                else -> Unit
            }
            statusText.setTextColor(textColor)

            itemView.setOnClickListener { onClick(item) }
        }
    }
}