package com.thedavelopers.eventqr.features.auth.resetpassword

/**
 * Client-side throttling for "Resend code" on the reset-code screen. All limits live here.
 * Pure functions so the escalation is unit tested without a coroutine or a view.
 */
object ResetResendPolicy {
    /** Wait after the first code is sent; doubles with every resend (30s, 60s, 120s, ...). */
    const val BASE_COOLDOWN_SECONDS = 30

    /** Upper bound for a single wait. */
    const val MAX_COOLDOWN_SECONDS = 600

    /** Resends allowed per screen session; after that the action is disabled. */
    const val MAX_RESENDS = 5

    /** Seconds to wait before the next resend, given how many resends were already made this session. */
    fun cooldownSeconds(resendsDone: Int): Int {
        if (resendsDone <= 0) return BASE_COOLDOWN_SECONDS
        // Clamp the shift so a large count cannot overflow before the cap applies.
        val shift = resendsDone.coerceAtMost(16)
        return (BASE_COOLDOWN_SECONDS.toLong() shl shift)
            .coerceAtMost(MAX_COOLDOWN_SECONDS.toLong())
            .toInt()
    }

    fun canResend(resendsDone: Int): Boolean = resendsDone < MAX_RESENDS
}
