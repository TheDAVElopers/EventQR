package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

interface EventRegistrationsContract {
    interface View {
        fun renderRegistrations(items: List<RegistrationResponse>)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}
