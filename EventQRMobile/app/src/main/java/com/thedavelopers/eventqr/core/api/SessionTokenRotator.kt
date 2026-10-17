package com.thedavelopers.eventqr.core.api

import com.thedavelopers.eventqr.core.session.TokenStore
import com.thedavelopers.eventqr.core.util.RoleMapper
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single place that trades the stored refresh token for a new token pair (POST auth/refresh).
 * Refresh tokens rotate and are single use, so every refresh, whether triggered by a 401 in
 * [TokenAuthenticator] or proactively by the session refresher, runs under one lock: two
 * concurrent refreshes can't both spend the same refresh token.
 *
 * Blocking: call off the main thread.
 */
class SessionTokenRotator(
    private val tokenStore: TokenStore,
    private val refreshCall: RefreshCall,
    private val onSessionExpired: () -> Unit,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    /** Called (on the refreshing thread) when a refresh shows the user's role changed; the new role is stored first. */
    private val onRoleChanged: (String) -> Unit = {},
) {
    /** Result of a proactive [rotate]. */
    enum class Rotation {
        /** New tokens were stored. */
        ROTATED,

        /** Skipped: tokens were rotated within the requested interval. */
        RECENT,

        /** Offline, server error or no refresh token stored: the session is kept as is. */
        UNAVAILABLE,

        /** The server rejected the refresh token: the session was cleared and the app told. */
        EXPIRED,
    }

    private val lock = Any()

    @Volatile
    private var lastRotatedAtMs: Long? = null

    /** Requests in flight that will hand back a new token pair themselves (see [duringExternalRotation]). */
    private val externalRotations = AtomicInteger(0)

    /**
     * After a 401 sent with [failedToken]: the token to retry with, or null to give up. Reuses a
     * token another caller already obtained while this one waited for the lock.
     */
    fun renewAfterUnauthorized(failedToken: String): String? = synchronized(lock) {
        val current = tokenStore.getAccessToken()
        if (!current.isNullOrBlank() && current != failedToken) return current
        (rotateLocked() as? RefreshOutcome.Success)?.accessToken
    }

    /**
     * Rotates the token pair now, unless a rotation already succeeded less than [minIntervalMs] ago
     * (avoids spending a refresh token on every screen resume).
     */
    fun rotate(minIntervalMs: Long = 0): Rotation = synchronized(lock) {
        val last = lastRotatedAtMs
        if (last != null && nowMs() - last < minIntervalMs) return Rotation.RECENT
        when (rotateLocked()) {
            is RefreshOutcome.Success -> Rotation.ROTATED
            RefreshOutcome.Rejected -> Rotation.EXPIRED
            RefreshOutcome.Unavailable -> Rotation.UNAVAILABLE
        }
    }

    /** Stores a token pair the server issued elsewhere (e.g. after a password change). */
    fun adoptTokens(accessToken: String, refreshToken: String?) = synchronized(lock) {
        tokenStore.saveTokens(accessToken, refreshToken)
        lastRotatedAtMs = nowMs()
    }

    /**
     * Runs [block], a request that revokes the current refresh tokens and returns a new pair (e.g. a password
     * change), which [block] stores with [adoptTokens]. Meanwhile a refresh the server rejects is not treated as
     * an expired session: the rejection is that revocation, and the replacement tokens are on their way.
     */
    suspend fun <T> duringExternalRotation(block: suspend () -> T): T {
        externalRotations.incrementAndGet()
        try {
            return block()
        } finally {
            externalRotations.decrementAndGet()
        }
    }

    private fun rotateLocked(): RefreshOutcome {
        val refreshToken = tokenStore.getRefreshToken()?.takeIf { it.isNotBlank() } ?: return RefreshOutcome.Unavailable
        val outcome = refreshCall.refresh(refreshToken)
        when (outcome) {
            is RefreshOutcome.Success -> {
                tokenStore.saveTokens(outcome.accessToken, outcome.refreshToken)
                lastRotatedAtMs = nowMs()
                storeRole(outcome.role)
            }
            RefreshOutcome.Rejected -> {
                // Superseded rather than expired: new tokens were stored during the call, or a request that
                // replaces them (password change) is in flight. Keep the session; nothing was rotated.
                val superseded = externalRotations.get() > 0 || tokenStore.getRefreshToken() != refreshToken
                if (superseded) return RefreshOutcome.Unavailable
                tokenStore.clear()
                onSessionExpired()
            }
            RefreshOutcome.Unavailable -> Unit
        }
        return outcome
    }

    /**
     * Every rotation, including the 401 one (the server answers 401 once the token's role no longer matches the
     * database), carries the current role. Persist it and report a change so the app can reroute.
     */
    private fun storeRole(role: String?) {
        val newRole = RoleMapper.normalizeRole(role)
        if (newRole.isEmpty()) return
        val oldRole = RoleMapper.normalizeRole(tokenStore.getUserRole())
        if (newRole == oldRole) return
        tokenStore.saveUserRole(newRole)
        if (oldRole.isNotEmpty()) onRoleChanged(newRole)
    }
}
