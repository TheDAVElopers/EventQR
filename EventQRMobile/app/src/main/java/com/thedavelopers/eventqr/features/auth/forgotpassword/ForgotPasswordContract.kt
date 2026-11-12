package com.thedavelopers.eventqr.features.auth.forgotpassword

interface ForgotPasswordContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showEmailError(message: String?)
        /** A failure of the whole request: the §5.16 strip above Send code. [retryable] only for a transient one. */
        fun showFormError(message: String, retryable: Boolean)
        fun navigateToResetPassword(email: String)
        fun navigateBackToSignIn()
    }
}
