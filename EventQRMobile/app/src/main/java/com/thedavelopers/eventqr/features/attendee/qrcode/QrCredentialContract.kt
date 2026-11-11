package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

interface QrCredentialContract {
    interface View : AttendeeView {
        fun renderQr(snapshot: QrCredentialSnapshot, registration: RegistrationResponse?, eventTitle: String?)

        /** Load failed: show the cached pass when there is one, otherwise an inline error with Retry. */
        fun showLoadError(message: String)
    }
}
