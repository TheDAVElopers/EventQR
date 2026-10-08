package com.thedavelopers.eventqr.features.auth.login

interface LoginContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showEmailError(message: String?)
        fun showPasswordError(message: String?)
        fun showMessage(message: String)
        fun navigateToDashboard(role: String?)

        /** Called once the server accepted the credentials, with the e-mail exactly as submitted (trimmed). */
        fun onLoginSucceeded(email: String) = Unit

        fun navigateToRegistration()
        fun navigateToForgotPassword()
    }
}