package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse

interface EventsContract {
    interface View : AttendeeView {
        fun showEvents(items: List<AttendeeEventResponse>)
        fun showError(message: String)
    }
}
