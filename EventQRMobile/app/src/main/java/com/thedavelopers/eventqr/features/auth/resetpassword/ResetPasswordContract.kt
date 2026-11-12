package com.thedavelopers.eventqr.features.auth.resetpassword

/** Step 2 of the reset flow: enter and verify the emailed 6-digit code. */
interface ResetPasswordContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showEmail(email: String)
        fun showCodeError(message: String?)
        /** The previously verified code expired: clear the field, explain inline, and draw attention to Resend. */
        fun showCodeExpired()
        /** Info toast (a new code was sent, the resend cap was hit); failures use the strip instead. */
        fun showMessage(message: String)
        /** A failed verify: the §5.16 strip above Verify. [retryable] re-runs the verify, only for a transient failure. */
        fun showFormError(message: String, retryable: Boolean)
        /** A failed resend: the same strip, but its `Retry` re-runs Resend code, not Verify. */
        fun showResendError(message: String, retryable: Boolean)
        /** Seconds left before the code can be requested again; 0 re-enables the resend action. */
        fun showResendCooldown(secondsLeft: Int)
        /** The per-session resend cap was hit; the resend action stays disabled. */
        fun showResendUnavailable()
        fun navigateToNewPassword(email: String, code: String)
        fun navigateToLogin()
    }
}
