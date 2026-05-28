package com.thedavelopers.eventqr.features.auth.login

interface LoginContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showEmailError(message: String?)
        fun showPasswordError(message: String?)
        fun showMessage(message: String)
        fun navigateToDashboard(role: String?)
        fun navigateToRegistration()
        fun navigateToForgotPassword()
    }
}