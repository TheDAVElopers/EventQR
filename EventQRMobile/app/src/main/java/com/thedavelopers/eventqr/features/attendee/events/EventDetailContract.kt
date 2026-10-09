package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse

interface EventDetailContract {
    interface View : AttendeeView {
        fun renderEvent(event: AttendeeEventResponse)
        fun updateRegistrationStatus(isRegistered: Boolean)
        fun onRegistrationStatusCheckFailed()
        /** The id of the user's cancellable (REGISTERED) registration for this event, or null when there is none. */
        fun setCancellableRegistration(registrationId: String?)
        fun showCancelling(isCancelling: Boolean)
        fun onRegistrationCancelled(message: String)
        /** Cancel failures carry a full-sentence reason (e.g. the 409 message), so they must not be shown as a truncating toast. */
        fun showCancelFailure(message: String)
        fun openRegistration(eventId: String, eventTitle: String, email: String, fullName: String, phoneNumber: String)
        fun getSessionUserId(): String?
        fun getSessionEmail(): String
        fun getSessionFullName(): String
        fun getSessionPhone(): String
    }
}
