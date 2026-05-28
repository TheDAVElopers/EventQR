package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

interface RegisteredEventsContract {
    interface View : AttendeeView {
        fun showRegisteredEvents(items: List<RegistrationResponse>)
    }
}
