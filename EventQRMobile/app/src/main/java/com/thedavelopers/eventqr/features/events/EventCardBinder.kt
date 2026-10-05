package com.thedavelopers.eventqr.features.events

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.ui.components.EventCard
import com.thedavelopers.eventqr.ui.components.parseBadgeStatus
import com.thedavelopers.eventqr.ui.theme.EventQrTheme

object EventCardBinder {

    fun inflate(
        context: Context,
        parent: ViewGroup?,
        title: String,
        status: String,
        day: String,
        month: String,
        time: String,
        location: String,
        count: Int,
        capacity: Int,
        percent: Int,
        onClick: (View) -> Unit,
    ): View {
        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        bind(composeView, title, status, day, month, time, location, count, capacity, percent, onClick)
        return composeView
    }

    fun bind(
        view: View,
        title: String,
        status: String,
        day: String,
        month: String,
        time: String,
        location: String,
        count: Int,
        capacity: Int,
        percent: Int,
        onClick: (View) -> Unit,
    ) {
        if (view is ComposeView) {
            val badgeStatus = parseBadgeStatus(status)
            view.setContent {
                EventQrTheme {
                    EventCard(
                        title = title,
                        status = badgeStatus,
                        statusLabel = status,
                        day = day,
                        month = month,
                        time = time,
                        location = location,
                        registeredCount = count,
                        capacity = capacity,
                        onClick = { onClick(view) },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
            return
        }

        // Fallback for legacy XML views if any
        val ctx = view.context
        val eventStatus = EventStatusBadgeStyler.fromLabel(status)

        view.findViewById<TextView?>(R.id.txtAttendeeEventTitle)?.text =
            title.ifBlank { "Untitled event" }

        view.findViewById<TextView?>(R.id.txtAttendeeEventStatus)?.let {
            EventStatusBadgeStyler.bind(it, eventStatus, status)
        }

        view.findViewById<View?>(R.id.layoutEventDate)?.setBackgroundResource(
            EventStatusBadgeStyler.dateBadgeRes(eventStatus),
        )

        view.findViewById<TextView?>(R.id.txtEventDay)?.text = day
        view.findViewById<TextView?>(R.id.txtEventMonth)?.text = month
        view.findViewById<TextView?>(R.id.txtAttendeeEventDateTime)?.text = time
        view.findViewById<TextView?>(R.id.txtAttendeeEventLocation)?.text = location

        val regCount = "$count/$capacity registered"
        view.findViewById<TextView?>(R.id.txtRegistrationCount)?.text = regCount
        view.findViewById<TextView?>(R.id.txtRegistrationPercent)?.text = "$percent%"

        view.findViewById<ProgressBar?>(R.id.pbRegistration)?.let { progressBar ->
            progressBar.progress = percent
            progressBar.progressDrawable = ctx.getDrawable(
                when (eventStatus) {
                    EventStatus.ENDED -> R.drawable.pb_event_completed
                    EventStatus.APPROVED -> R.drawable.pb_event_upcoming
                    else -> R.drawable.pb_event_active
                },
            )
        }

        view.setOnClickListener { onClick(it) }
    }
}
