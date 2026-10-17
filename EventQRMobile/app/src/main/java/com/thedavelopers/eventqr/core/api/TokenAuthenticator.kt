package com.thedavelopers.eventqr.core.api

import com.thedavelopers.eventqr.core.session.TokenStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** What the server said when asked to exchange a refresh token. */
sealed interface RefreshOutcome {
    /** [role] is the user's current role name from the refresh response; null from servers that omit it. */
    data class Success(val accessToken: String, val refreshToken: String?, val role: String? = null) : RefreshOutcome

    /** The server understood and said no: the session is over. */
    data object Rejected : RefreshOutcome

    /** Offline, timed out or a server error: say nothing about the session's validity. */
    data object Unavailable : RefreshOutcome
}

fun interface RefreshCall {
    fun refresh(refreshToken: String): RefreshOutcome
}

/**
 * On a 401, trades the refresh token for a new access token and retries the request once, so an
 * expired access token is invisible to the user. Only a definite rejection from the server ends
 * the session; being offline never logs anyone out. The refresh itself goes through the shared
 * [SessionTokenRotator], so it can't race the proactive session refresh.
 */
class TokenAuthenticator(private val rotator: SessionTokenRotator) : Authenticator {

    constructor(
        tokenStore: TokenStore,
        refreshCall: RefreshCall,
        onSessionExpired: () -> Unit,
    ) : this(SessionTokenRotator(tokenStore, refreshCall, onSessionExpired))

    override fun authenticate(route: Route?, response: Response): Request? {
        val failedRequest = response.request
        val failedHeader = failedRequest.header("Authorization") ?: return null
        if (isCredentialEndpoint(failedRequest.url.encodedPath) || responseCount(response) >= 2) {
            return null
        }
        val failedToken = failedHeader.removePrefix("Bearer ").trim()
        val token = rotator.renewAfterUnauthorized(failedToken) ?: return null
        return withToken(failedRequest, token)
    }

    private fun withToken(request: Request, token: String): Request =
        request.newBuilder().header("Authorization", "Bearer $token").build()

    // A 401 from these means "wrong credentials", not "expired session".
    private fun isCredentialEndpoint(path: String): Boolean =
        path.endsWith("/auth/login") || path.endsWith("/auth/register") || path.endsWith("/auth/refresh")

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
