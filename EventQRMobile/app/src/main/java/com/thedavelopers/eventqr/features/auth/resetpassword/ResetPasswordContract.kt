package com.thedavelopers.eventqr.features.auth.resetpassword

interface ResetPasswordContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showEmail(email: String)
        fun showCodeError(message: String?)
        fun showPasswordError(message: String?)
        fun showConfirmPasswordError(message: String?)
        fun showMessage(message: String)
        /** Seconds left before the code can be requested again; 0 re-enables the resend action. */
        fun showResendCooldown(secondsLeft: Int)
        fun navigateToLogin()
    }
}
