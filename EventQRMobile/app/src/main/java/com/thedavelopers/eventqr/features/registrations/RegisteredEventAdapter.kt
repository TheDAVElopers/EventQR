package com.thedavelopers.eventqr.features.registrations

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.attendee.AttendeeQrCredentialActivity
import com.thedavelopers.eventqr.features.attendee.EXTRA_REGISTRATION_ID
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

class RegisteredEventAdapter : RecyclerView.Adapter<RegisteredEventAdapter.ViewHolder>() {

    private val items = mutableListOf<RegistrationResponse>()

    fun submitItems(newItems: List<RegistrationResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_registered_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.txtRegisteredEventTitle)
        private val statusView: TextView = itemView.findViewById(R.id.txtRegisteredEventStatus)
        private val dateTimeView: TextView = itemView.findViewById(R.id.txtEventDateTime)
        private val locationView: TextView = itemView.findViewById(R.id.txtEventLocation)
        private val regDateView: TextView = itemView.findViewById(R.id.txtRegistrationDate)
        private val pointsView: TextView = itemView.findViewById(R.id.txtPoints)
        private val btnTransactions: Button = itemView.findViewById(R.id.btnTransactionHistory)
        private val btnDetails: Button = itemView.findViewById(R.id.btnEventDetails)

        fun bind(registration: RegistrationResponse) {
            titleView.text = registration.eventTitle ?: "Registered event"
            statusView.text = registration.status.name.lowercase().replaceFirstChar { it.uppercase() }
            
            val (statusColor, statusBg) = when (registration.status) {
                RegistrationStatus.REGISTERED -> "#4F46E5" to R.drawable.bg_purple_pill
                RegistrationStatus.ENTERED -> "#16A34A" to R.drawable.bg_green_pill
                RegistrationStatus.CANCELLED -> "#EF4444" to R.drawable.bg_red_warning
                else -> "#6B7280" to R.drawable.bg_soft_gray_pill
            }

            statusView.setTextColor(android.graphics.Color.parseColor(statusColor))
            statusView.setBackgroundResource(statusBg)

            dateTimeView.text = registration.eventStartAt?.let(DateFormatters::formatInstant) ?: "Date not specified"
            locationView.text = registration.eventLocation ?: "Location not specified"
            regDateView.text = "Registered: ${registration.registeredAt?.let { DateFormatters.formatInstant(it) } ?: "N/A"}"
            
            // Points would ideally come from the registration or a separate balance call
            pointsView.text = "0 pts"

            btnTransactions.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, AttendeeQrCredentialActivity::class.java).apply {
                    putExtra(EXTRA_REGISTRATION_ID, registration.registrationId.toString())
                    putExtra(com.thedavelopers.eventqr.features.attendee.EXTRA_QR_CREDENTIAL_ID, registration.qrCredentialId?.toString().orEmpty())
                }
                context.startActivity(intent)
            }

            btnTransactions.text = "View QR"
            btnDetails.visibility = View.VISIBLE
            btnDetails.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, com.thedavelopers.eventqr.features.attendee.EventDetailActivity::class.java).apply {
                    putExtra(com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_ID, registration.eventId.toString())
                    putExtra(com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_TITLE, registration.eventTitle.orEmpty())
                }
                context.startActivity(intent)
            }
        }
    }
}