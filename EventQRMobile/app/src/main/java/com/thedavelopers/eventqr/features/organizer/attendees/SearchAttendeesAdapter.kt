package com.thedavelopers.eventqr.features.organizer.attendees

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
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
                "Checked In" -> Color.parseColor("#D1FAE5") to Color.parseColor("#059669")
                "Registered" -> Color.parseColor("#E0E7FF") to Color.parseColor("#4F46E5")
                "Exited" -> Color.parseColor("#E5E7EB") to Color.parseColor("#6B7280")
                "No Show" -> Color.parseColor("#FEF3C7") to Color.parseColor("#B45309")
                else -> Color.parseColor("#E5E7EB") to Color.parseColor("#4B5563")
            }
            statusText.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
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