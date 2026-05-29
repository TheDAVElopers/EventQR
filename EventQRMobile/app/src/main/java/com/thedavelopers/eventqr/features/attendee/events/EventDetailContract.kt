package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse

interface EventDetailContract {
    interface View : AttendeeView {
        fun renderEvent(event: AttendeeEventResponse)
        fun updateRegistrationStatus(isRegistered: Boolean)
        fun openRegistration(eventId: String, eventTitle: String, email: String, fullName: String, phoneNumber: String)
        fun getSessionUserId(): String?
        fun getSessionEmail(): String
        fun getSessionFullName(): String
        fun getSessionPhone(): String
    }
}
