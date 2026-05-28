package com.thedavelopers.eventqr.features.auth.forgotpassword

interface ForgotPasswordContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showEmailError(message: String?)
        fun showMessage(message: String)
        fun navigateBackToSignIn()
    }
}