package com.thedavelopers.eventqr.features.registrations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

class RegistrationAdapter(
    private val onClick: ((RegistrationResponse) -> Unit)? = null,
) : RecyclerView.Adapter<RegistrationAdapter.ViewHolder>() {

    private val items = mutableListOf<RegistrationResponse>()

    fun submitItems(newItems: List<RegistrationResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_registration, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
        holder.itemView.setOnClickListener {
            onClick?.invoke(items[position])
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarView: TextView = itemView.findViewById(R.id.txtRegistrationAvatar)
        private val titleView: TextView = itemView.findViewById(R.id.txtRegistrationTitle)
        private val detailView: TextView = itemView.findViewById(R.id.txtRegistrationDetails)
        private val statusView: TextView = itemView.findViewById(R.id.txtRegistrationStatus)
        private val pointsView: TextView = itemView.findViewById(R.id.txtRegistrationPoints)

        fun bind(item: RegistrationResponse) {
            val name = item.attendeeName.ifBlank { "Attendee" }
            avatarView.text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
            titleView.text = name
            detailView.text = item.attendeeEmail.ifBlank { "No email provided" }
            pointsView.text = "${item.pointsEarned} pts"
            RegistrationStatusBadgeStyler.bind(statusView, item.status)
        }
    }
}