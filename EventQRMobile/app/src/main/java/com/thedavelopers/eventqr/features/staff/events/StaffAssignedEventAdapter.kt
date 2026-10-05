package com.thedavelopers.eventqr.features.staff

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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.ui.components.EventCard
import com.thedavelopers.eventqr.ui.components.parseBadgeStatus
import com.thedavelopers.eventqr.ui.theme.BrandPrimary
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.TextOnPrimary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class StaffAssignedEventAdapter(
    private val onScanClick: (StaffAssignedEventResponse) -> Unit,
    private val onAttendeesClick: (StaffAssignedEventResponse) -> Unit,
) : RecyclerView.Adapter<StaffAssignedEventAdapter.ViewHolder>() {

    private val items = mutableListOf<StaffAssignedEventResponse>()
    private val manilaZone = ZoneId.of("Asia/Manila")
    private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    fun submitItems(newItems: List<StaffAssignedEventResponse>) {
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
        fun bind(item: StaffAssignedEventResponse) {
            val resolvedStatus = EventStatusBadgeStyler.resolve(
                item.status, item.eventStartAt, item.eventEndAt,
            )
            val day: String
            val month: String
            val time: String

            if (item.eventStartAt != null) {
                val zdt = item.eventStartAt.atZone(manilaZone)
                day = zdt.dayOfMonth.toString()
                month = zdt.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
                time = zdt.format(timeFmt)
            } else {
                day = "--"
                month = "---"
                time = "-"
            }

            val location = item.location?.takeIf { it.isNotBlank() } ?: "Location not set"
            val badgeStatus = parseBadgeStatus(resolvedStatus.name)

            composeView.setContent {
                EventQrTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        EventCard(
                            title = item.title.ifBlank { "Untitled Event" },
                            status = badgeStatus,
                            day = day,
                            month = month,
                            time = time,
                            location = location,
                            onClick = { onAttendeesClick(item) },
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onScanClick(item) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandPrimary,
                                    contentColor = TextOnPrimary,
                                ),
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan QR", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            OutlinedButton(
                                onClick = { onAttendeesClick(item) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Attendees", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}
