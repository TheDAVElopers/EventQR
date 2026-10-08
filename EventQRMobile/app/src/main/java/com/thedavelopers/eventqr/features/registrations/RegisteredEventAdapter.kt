package com.thedavelopers.eventqr.features.registrations

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.EventCardPresenter
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
import com.thedavelopers.eventqr.ui.theme.EventQrRowTheme
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

data class RegisteredEventCardState(
    val title: String = "",
    val badgeLabel: String = "",
    val badgeStatusRaw: String = "",
    val day: String = EventCardPresenter.UNKNOWN_DAY,
    val month: String = EventCardPresenter.UNKNOWN_MONTH,
    val time: String = EventCardPresenter.UNKNOWN_TIME,
    val location: String = EventCardPresenter.UNKNOWN_LOCATION,
    val eventId: String = "",
    val registrationId: String = "",
    val qrCredentialId: String = "",
)

class RegisteredEventAdapter : RecyclerView.Adapter<RegisteredEventAdapter.ViewHolder>() {

    private val items = mutableListOf<RegistrationResponse>()

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

    inner class ViewHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        private val state = mutableStateOf(RegisteredEventCardState())

        init {
            composeView.setContent {
                EventQrRowTheme {
                    RegisteredEventCard(state.value, composeView.context)
                }
            }
        }

        fun bind(registration: RegistrationResponse) {
            val status = EventStatusBadgeStyler.resolve(
                null,
                registration.eventStartAt,
                registration.eventEndAt,
            )
            val date = EventCardPresenter.dateParts(registration.eventStartAt)

            state.value = RegisteredEventCardState(
                title = registration.eventTitle.orEmpty(),
                badgeLabel = EventStatusBadgeStyler.displayLabel(status),
                badgeStatusRaw = status.name,
                day = date.day,
                month = date.month,
                time = date.time,
                location = EventCardPresenter.location(registration.eventLocation),
                eventId = registration.eventId.toString(),
                registrationId = registration.registrationId.toString(),
                qrCredentialId = registration.qrCredentialId?.toString().orEmpty(),
            )
        }
    }
}

@Composable
private fun RegisteredEventCard(state: RegisteredEventCardState, context: Context) {
    val spacing = LocalSpacing.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.mediumSmall),
    ) {
        EventCard(
            title = state.title,
            status = parseBadgeStatus(state.badgeStatusRaw),
            statusLabel = state.badgeLabel,
            day = state.day,
            month = state.month,
            time = state.time,
            location = state.location,
            onClick = {
                context.startActivity(
                    Intent(context, EventDetailActivity::class.java).apply {
                        putExtra(EXTRA_EVENT_ID, state.eventId)
                        putExtra(EXTRA_EVENT_TITLE, state.title)
                    },
                )
            },
            // Inside the card, not a separate row underneath it.
            footer = {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, QrDisplayActivity::class.java).apply {
                                putExtra(EXTRA_REGISTRATION_ID, state.registrationId)
                                putExtra(EXTRA_QR_CREDENTIAL_ID, state.qrCredentialId)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(spacing.mediumSmall + spacing.extraSmall),
                    )
                    Spacer(modifier = Modifier.width(spacing.micro))
                    Text(
                        text = stringResource(R.string.registered_event_show_qr),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            },
        )
    }
}
