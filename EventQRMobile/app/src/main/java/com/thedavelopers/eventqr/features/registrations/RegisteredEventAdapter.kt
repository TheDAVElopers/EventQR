package com.thedavelopers.eventqr.features.registrations

import android.content.Intent
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.attendee.EXTRA_QR_CREDENTIAL_ID
import com.thedavelopers.eventqr.features.attendee.EXTRA_REGISTRATION_ID
import com.thedavelopers.eventqr.features.attendee.EventDetailActivity
import com.thedavelopers.eventqr.features.attendee.QrDisplayActivity
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.ui.components.EventCard
import com.thedavelopers.eventqr.ui.components.parseBadgeStatus
import com.thedavelopers.eventqr.ui.theme.BrandPrimary
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.PaperWhite
import com.thedavelopers.eventqr.ui.theme.TextOnPrimary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class RegisteredEventAdapter : RecyclerView.Adapter<RegisteredEventAdapter.ViewHolder>() {

    private val items = mutableListOf<RegistrationResponse>()
    private val manilaZone = ZoneId.of("Asia/Manila")
    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    fun submitItems(newItems: List<RegistrationResponse>) {
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
        fun bind(registration: RegistrationResponse) {
            val status = EventStatusBadgeStyler.resolve(null, registration.eventStartAt, registration.eventEndAt)
            val day: String
            val month: String
            val time: String

            if (registration.eventStartAt != null) {
                val zdt = registration.eventStartAt.atZone(manilaZone)
                day = zdt.dayOfMonth.toString()
                month = zdt.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
                time = zdt.format(timeFormatter)
            } else {
                day = "--"
                month = "---"
                time = "-"
            }

            val location = registration.eventLocation?.takeIf { it.isNotBlank() } ?: "Location not set"
            val badgeStatus = parseBadgeStatus(status.name)
            val context = composeView.context

            composeView.setContent {
                EventQrTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        EventCard(
                            title = registration.eventTitle ?: "Registered event",
                            status = badgeStatus,
                            day = day,
                            month = month,
                            time = time,
                            location = location,
                            onClick = {
                                val intent = Intent(context, EventDetailActivity::class.java).apply {
                                    putExtra(EXTRA_EVENT_ID, registration.eventId.toString())
                                    putExtra(EXTRA_EVENT_TITLE, registration.eventTitle.orEmpty())
                                }
                                context.startActivity(intent)
                            },
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, QrDisplayActivity::class.java).apply {
                                        putExtra(EXTRA_REGISTRATION_ID, registration.registrationId.toString())
                                        putExtra(EXTRA_QR_CREDENTIAL_ID, registration.qrCredentialId?.toString().orEmpty())
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandPrimary,
                                    contentColor = TextOnPrimary,
                                ),
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Show QR", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(context, EventDetailActivity::class.java).apply {
                                        putExtra(EXTRA_EVENT_ID, registration.eventId.toString())
                                        putExtra(EXTRA_EVENT_TITLE, registration.eventTitle.orEmpty())
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Details", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}