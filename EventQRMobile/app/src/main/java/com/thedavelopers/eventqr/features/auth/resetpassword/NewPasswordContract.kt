package com.thedavelopers.eventqr.features.auth.resetpassword

/** Final step of the reset flow: choose the new password for an already verified code. */
interface NewPasswordContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showPasswordError(message: String?)
        fun showConfirmPasswordError(message: String?)
        fun showMessage(message: String)
        /** The code is no longer accepted; return to the code step (empty field) so the user can request a new one. */
        fun navigateBackToExpiredCode(email: String)
        fun navigateToLogin()
    }
}
