package com.thedavelopers.eventqr.core.session

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.ApiService
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.SessionTokenRotator
import com.thedavelopers.eventqr.core.api.SessionTokenRotator.Rotation
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.core.util.RoleMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Refreshes the signed-in session in the background right after launch, so the dashboard opens on the cached
 * session instead of waiting for the network. Runs on an application-level scope because the launching activity
 * finishes as soon as it has routed.
 *
 * A refresh rotates the token pair (POST auth/refresh) so the access token carries the CURRENT database role
 * (e.g. an attendee upgraded to organizer after approval), then reads auth/me to store that role and profile.
 */
object SessionRefresher {
    /** A refresh within this window is skipped: resuming a dashboard shouldn't spend a refresh token each time. */
    const val MIN_REFRESH_INTERVAL_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Result(
        val rotation: Rotation,
        /**
         * The normalized role when auth/me showed a change the rotation itself didn't (that one is reported by
         * [SessionTokenRotator]), else null. Already passed to [SessionEvents.notifyRoleChanged] by [refreshNow].
         */
        val changedRole: String?,
    )

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refreshNow(appContext) }
    }

    /**
     * Rotates the session now (throttled by [MIN_REFRESH_INTERVAL_MS]) and stores the current role and profile.
     * A role change goes to [SessionEvents.notifyRoleChanged] exactly once: from the rotation when the refresh
     * response carried the new role, otherwise from auth/me. Being offline keeps the cached session; a rejected
     * refresh token expires it via [SessionEvents].
     */
    suspend fun refreshNow(context: Context): Result {
        val appContext = context.applicationContext
        return refreshSession(
            ApiClient.sessionRotator(appContext),
            ApiClient.getService(appContext),
            SessionManager(appContext),
            MIN_REFRESH_INTERVAL_MS,
        ).also { result -> result.changedRole?.let(SessionEvents::notifyRoleChanged) }
    }

    internal suspend fun refreshSession(
        rotator: SessionTokenRotator,
        api: ApiService,
        sessionManager: SessionManager,
        minIntervalMs: Long,
    ): Result {
        // The rotation stores (and reports) the role its response carried.
        val rotation = withContext(Dispatchers.IO) { rotator.rotate(minIntervalMs) }
        if (rotation != Rotation.ROTATED) return Result(rotation, null)
        var changedRole: String? = null
        (safeApiCall { api.getAuthMe() } as? NetworkResult.Success)?.data?.let { me ->
            // Gson leaves an unknown enum value null despite the non-null type.
            val role: AccountRole? = me.role
            if (role != null) {
                // Compared with the role as of after the rotation, so a change it already reported isn't repeated.
                val before = RoleMapper.normalizeRole(sessionManager.getUserRole())
                val after = RoleMapper.normalizeRole(role.name)
                sessionManager.saveRole(role)
                if (before.isNotEmpty() && after.isNotEmpty() && after != before) changedRole = after
            }
            sessionManager.updateProfile(me.fullName, me.phoneNumber)
        }
        return Result(rotation, changedRole)
    }
}
