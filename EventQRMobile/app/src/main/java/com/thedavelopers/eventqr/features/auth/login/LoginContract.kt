package com.thedavelopers.eventqr.features.auth.login

interface LoginContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showEmailError(message: String?)
        fun showPasswordError(message: String?)
        fun showMessage(message: String)

        /**
         * A failure that belongs to the whole form (bad credentials, rate limit, network): one sentence in
         * the §5.16 strip above Sign in. [retryable] is true only for a transient failure, where repeating
         * the same request can succeed; a wrong password would only burn another rate-limit attempt.
         */
        fun showFormError(message: String, retryable: Boolean)
        fun navigateToDashboard(role: String?)

        /** Called once the server accepted the credentials, with the e-mail exactly as submitted (trimmed). */
        fun onLoginSucceeded(email: String) = Unit

        fun navigateToRegistration()
        fun navigateToForgotPassword()
    }
}