package com.thedavelopers.eventqr.features.auth.changepassword

interface ChangePasswordContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showCurrentPasswordError(message: String?)
        fun showNewPasswordError(message: String?)
        fun showConfirmPasswordError(message: String?)
        fun showMessage(message: String)
        fun navigateBackToSignIn()
    }
}