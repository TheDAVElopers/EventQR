package com.thedavelopers.eventqr.features.organizer

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R

class AttendeeManagementAdapter(
    private val onClick: (OrganizerMvpAttendee) -> Unit,
) : RecyclerView.Adapter<AttendeeManagementAdapter.ViewHolder>() {

    private val items = mutableListOf<OrganizerMvpAttendee>()

    fun submitItems(newItems: List<OrganizerMvpAttendee>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_organizer_attendee, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: TextView = itemView.findViewById(R.id.txtAttendeeInitial)
        private val nameText: TextView = itemView.findViewById(R.id.txtAttendeeName)
        private val emailText: TextView = itemView.findViewById(R.id.txtAttendeeEmail)
        private val pointsText: TextView = itemView.findViewById(R.id.txtAttendeePoints)
        private val statusText: TextView = itemView.findViewById(R.id.txtAttendeeStatus)

        fun bind(item: OrganizerMvpAttendee) {
            avatar.text = attendeeInitial(item.name)
            nameText.text = item.name
            emailText.text = item.email

            if (item.points > 0) {
                pointsText.visibility = View.VISIBLE
                pointsText.text = pointsText.context.getString(R.string.common_points_short, item.points)
            } else {
                pointsText.visibility = View.GONE
            }

            val (backgroundColor, textColor) = item.statusPalette(itemView.context)
            statusText.text = item.statusBucket()
            statusText.background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = 999f
            }
            statusText.setTextColor(textColor)

            itemView.setOnClickListener { onClick(item) }
        }
    }
}
