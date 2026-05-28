package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

interface QrCredentialContract {
    interface View : AttendeeView {
        fun renderQr(snapshot: QrCredentialSnapshot, registration: RegistrationResponse?, eventTitle: String?)
    }
}
