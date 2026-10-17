package com.thedavelopers.eventqr.core.session

/**
 * Lets low-level networking code announce that the session can no longer be recovered (the
 * refresh token was rejected) without depending on any Activity. The Application registers
 * the handler and sends the user back to the login screen.
 */
object SessionEvents {
    @Volatile
    var onSessionExpired: (() -> Unit)? = null

    fun notifySessionExpired() {
        onSessionExpired?.invoke()
    }

    /**
     * The signed-in user's role changed mid-session (seen in a token refresh or auth/me). The Application reroutes
     * to the right dashboard, or remembers it for the next dashboard visit. May be called from any thread.
     */
    @Volatile
    var onRoleChanged: ((String) -> Unit)? = null

    fun notifyRoleChanged(role: String) {
        onRoleChanged?.invoke(role)
    }
}
