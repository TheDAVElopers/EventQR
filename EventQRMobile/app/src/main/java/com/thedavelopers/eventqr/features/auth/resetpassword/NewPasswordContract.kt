package com.thedavelopers.eventqr.features.auth.resetpassword

/** Final step of the reset flow: choose the new password for an already verified code. */
interface NewPasswordContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showPasswordError(message: String?)
        fun showConfirmPasswordError(message: String?)
        /** Info toast (the password was reset); failures use the strip instead. */
        fun showMessage(message: String)
        /**
         * A failure of the whole request: one sentence in the §5.16 strip above the footer, never a `Retry`.
         * The reset code is one-time, so a repeat of a request whose answer was lost could land on a spent code.
         */
        fun showFormError(message: String)
        /** The code is no longer accepted; return to the code step (empty field) so the user can request a new one. */
        fun navigateBackToExpiredCode(email: String)
        fun navigateToLogin()
    }
}
