package com.thedavelopers.eventqr.features.organizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

class AttendeeManagementAdapter(
    private val onClick: (RegistrationResponse) -> Unit
) : RecyclerView.Adapter<AttendeeManagementAdapter.ViewHolder>() {

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
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.txtRegistrationTitle)
        private val emailText: TextView = itemView.findViewById(R.id.txtRegistrationDetails)
        private val statusText: TextView = itemView.findViewById(R.id.txtRegistrationStatus)

        fun bind(item: RegistrationResponse) {
            nameText.text = item.attendeeName
            emailText.text = item.attendeeEmail
            
            val isCheckedIn = item.status == RegistrationStatus.REGISTERED // Placeholder logic
            statusText.text = if (isCheckedIn) "Checked In" else "Not Checked In"
            statusText.setBackgroundResource(if (isCheckedIn) R.drawable.bg_green_pill else R.drawable.bg_soft_gray_pill)
            
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
