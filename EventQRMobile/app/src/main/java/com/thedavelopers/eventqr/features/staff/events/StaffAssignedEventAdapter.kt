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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.ui.components.EventCard
import com.thedavelopers.eventqr.ui.components.parseBadgeStatus
import com.thedavelopers.eventqr.ui.theme.EventQrRowTheme
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

data class StaffAssignedEventCardState(
    val title: String = "",
    val badgeStatusRaw: String = "",
    val day: String = EventCardPresenter.UNKNOWN_DAY,
    val month: String = EventCardPresenter.UNKNOWN_MONTH,
    val time: String = EventCardPresenter.UNKNOWN_TIME,
    val location: String = EventCardPresenter.UNKNOWN_LOCATION,
)

class StaffAssignedEventAdapter(
    private val onScanClick: (StaffAssignedEventResponse) -> Unit,
    private val onAttendeesClick: (StaffAssignedEventResponse) -> Unit,
) : RecyclerView.Adapter<StaffAssignedEventAdapter.ViewHolder>() {

    private val items = mutableListOf<StaffAssignedEventResponse>()

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

    inner class ViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        private val state = mutableStateOf(StaffAssignedEventCardState())

        private var item: StaffAssignedEventResponse? = null

        init {
            composeView.setContent {
                EventQrRowTheme {
                    StaffAssignedEventCard(
                        state = state.value,
                        onScanClick = { item?.let(onScanClick) },
                        onAttendeesClick = { item?.let(onAttendeesClick) },
                    )
                }
            }
        }

        fun bind(event: StaffAssignedEventResponse) {
            item = event
            val resolvedStatus = EventStatusBadgeStyler.resolve(
                event.status,
                event.eventStartAt,
                event.eventEndAt,
            )
            val date = EventCardPresenter.dateParts(event.eventStartAt)

            state.value = StaffAssignedEventCardState(
                title = event.title,
                badgeStatusRaw = EventStatusBadgeStyler.displayLabel(resolvedStatus),
                day = date.day,
                month = date.month,
                time = date.time,
                location = EventCardPresenter.location(event.location),
            )
        }
    }
}

@Composable
private fun StaffAssignedEventCard(
    state: StaffAssignedEventCardState,
    onScanClick: () -> Unit,
    onAttendeesClick: () -> Unit,
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.mediumSmall),
    ) {
        EventCard(
            title = state.title,
            status = parseBadgeStatus(state.badgeStatusRaw),
            day = state.day,
            month = state.month,
            time = state.time,
            location = state.location,
        )

        Spacer(modifier = Modifier.height(spacing.micro))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onScanClick,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(spacing.mediumSmall + spacing.extraSmall),
                )
                Spacer(modifier = Modifier.width(spacing.micro))
                Text(
                    text = "Scan QR",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            OutlinedButton(
                onClick = onAttendeesClick,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(spacing.mediumSmall + spacing.extraSmall),
                )
                Spacer(modifier = Modifier.width(spacing.micro))
                Text(
                    text = "Attendees",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
