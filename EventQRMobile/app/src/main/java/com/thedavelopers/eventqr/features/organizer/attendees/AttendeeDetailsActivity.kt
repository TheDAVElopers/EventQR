package com.thedavelopers.eventqr.features.organizer.attendees

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class AttendeeDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attendeeId = intent.getStringExtra("extra_attendee_id").orEmpty()
        val eventId = intentEventId().orEmpty()
        val content = organizerShell("Attendee Details", showBack = true)
        if (attendeeId.isBlank() || eventId.isBlank()) {
            content.addView(emptyState("Open attendee details from an event to view live records."))
            return
        }
        content.addView(loadingState("Loading attendee details..."))
        MainScope().launch {
            val attendeeLoad = OrganizerRepository(this@AttendeeDetailsActivity).loadAttendeesForMvp(eventId)
            val attendee = attendeeLoad.data.firstOrNull { it.id == attendeeId }
            content.removeAllViews()
            attendeeLoad.message?.let { content.addView(errorState(it) { recreate() }) }
            if (attendee == null) {
                content.addView(emptyState("Attendee record not found for this event."))
                return@launch
            }
            content.addView(card().apply {
                addView(text(attendee.name, 18, true))
                addView(text("${attendee.email}\n${attendee.phone}\nStatus: ${attendee.currentEventStatus}\nPoints: ${attendee.points}", 13, false, MUTED))
            })
        }
    }
}
