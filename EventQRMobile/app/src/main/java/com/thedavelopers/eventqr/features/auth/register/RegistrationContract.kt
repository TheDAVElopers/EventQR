package com.thedavelopers.eventqr.features.auth.register

interface RegistrationContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showFieldError(field: String, message: String?)
        fun showMessage(message: String)
        fun navigateToSignIn()
    }
}