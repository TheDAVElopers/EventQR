package com.thedavelopers.eventqr.features.attendee

interface RegistrationContract {
    interface View : AttendeeView {
        fun showFieldError(field: String, message: String?)
        fun openQr(registrationId: String, qrCredentialId: String)
    }
}
