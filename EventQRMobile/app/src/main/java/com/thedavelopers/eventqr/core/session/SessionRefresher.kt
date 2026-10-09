package com.thedavelopers.eventqr.core.session

import android.content.Context
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Refreshes the signed-in session in the background right after launch, so the dashboard opens on the cached
 * session instead of waiting for the network. Runs on an application-level scope because the launching activity
 * finishes as soon as it has routed.
 */
object SessionRefresher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called on the main thread with the new role when the refresh shows it differs from the cached one. */
    @Volatile
    var onRoleChanged: ((String) -> Unit)? = null

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val sessionManager = SessionManager(appContext)
            val cachedRole = RoleMapper.normalizeRole(sessionManager.getUserRole())
            val repo = AuthRepository(appContext)
            // Re-issue the access token so the client reflects the CURRENT role in the database (e.g. an attendee
            // upgraded to organizer after approval) instead of the stale role embedded in the old JWT. A failure
            // (e.g. offline) keeps the cached session; an expired one is handled by SessionEvents.onSessionExpired.
            // The refreshed role is persisted by refreshSessionToken.
            val refreshed = repo.refreshSessionToken() as? NetworkResult.Success ?: return@launch
            (repo.getAuthMe() as? NetworkResult.Success)?.let { me ->
                sessionManager.updateProfile(me.data.fullName, me.data.phoneNumber)
            }
            val newRole = RoleMapper.normalizeRole(refreshed.data.role?.name)
            if (newRole.isNotEmpty() && newRole != cachedRole) {
                withContext(Dispatchers.Main) { onRoleChanged?.invoke(newRole) }
            }
        }
    }
}
