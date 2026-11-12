package com.thedavelopers.eventqr.features.auth.register

interface RegistrationContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showFieldError(field: String, message: String?)
        fun showMessage(message: String)

        /**
         * A failure of the whole request: one sentence in the §5.16 strip above the footer, never a `Retry`
         * (creating an account is not safe to repeat when the first answer was lost).
         */
        fun showFormError(message: String)

        /** A field the API itself identified as the problem (a taken email): field error, focus, announcement. */
        fun showServerFieldError(field: String, message: String)
        fun navigateToSignIn()
    }
}