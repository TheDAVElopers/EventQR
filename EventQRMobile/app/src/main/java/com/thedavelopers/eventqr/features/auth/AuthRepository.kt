package com.thedavelopers.eventqr.features.auth

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.ApiService
import com.thedavelopers.eventqr.core.api.SessionTokenRotator
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.session.SessionRefresher
import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse
import com.thedavelopers.eventqr.features.auth.model.dto.RegisterRequest
import com.thedavelopers.eventqr.features.auth.model.dto.ChangePasswordRequest
import com.thedavelopers.eventqr.features.auth.model.dto.ChangePasswordResponse
import com.thedavelopers.eventqr.features.auth.model.dto.ForgotPasswordRequest
import com.thedavelopers.eventqr.features.auth.model.dto.ResetPasswordRequest
import com.thedavelopers.eventqr.features.auth.model.dto.VerifyResetCodeRequest
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse

open class AuthRepository internal constructor(
    private val appContext: Context,
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val rotator: SessionTokenRotator,
) {
    constructor(context: Context) : this(
        context.applicationContext,
        ApiClient.getService(context),
        SessionManager(context),
        ApiClient.sessionRotator(context),
    )

    open suspend fun login(email: String, password: String): NetworkResult<LoginResponse> =
        safeApiCall { apiService.login(LoginRequest(email, password)) }

        suspend fun getAuthMe(): NetworkResult<UserResponse> =
        safeApiCall { apiService.getAuthMe() }

    suspend fun getUserProfile(): NetworkResult<UserResponse> =
        safeApiCall { apiService.getUsersMe() }

    suspend fun createUser(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
    ): NetworkResult<UserResponse> =
        safeApiCall {
            apiService.register(
                RegisterRequest(
                    email = email,
                    fullName = fullName,
                    phoneNumber = phoneNumber.ifBlank { null },
                    password = password
                )
            )
        }

    open suspend fun forgotPassword(email: String): NetworkResult<Unit> = safeApiCall {
        apiService.forgotPassword(ForgotPasswordRequest(email))
    }

    /** Checks the emailed code without consuming it, so the new-password step can resubmit it. */
    open suspend fun verifyResetCode(email: String, code: String): NetworkResult<Unit> = safeApiCall {
        apiService.verifyResetCode(VerifyResetCodeRequest(email, code))
    }

    open suspend fun resetPassword(email: String, code: String, newPassword: String, confirmPassword: String): NetworkResult<Unit> = safeApiCall {
        apiService.resetPassword(ResetPasswordRequest(email, code, newPassword, confirmPassword))
    }

    /**
     * Changes the password. The change revokes the old refresh tokens, so a fresh session in the response is
     * stored to keep the user signed in; an older server returns none and the stored session is left alone.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String): NetworkResult<Unit> =
        // Guarded: a token refresh racing this request is rejected by the revocation, which must not log out.
        rotator.duringExternalRotation {
            when (val result = safeApiCall { apiService.changePassword(ChangePasswordRequest(currentPassword, newPassword, confirmPassword)) }) {
                is NetworkResult.Success -> {
                    // safeApiCall yields Unit (not null) for a response without data, so check the type.
                    ((result.data as Any?) as? ChangePasswordResponse)?.let(::storeSessionAfterPasswordChange)
                    NetworkResult.Success(Unit, result.message)
                }
                is NetworkResult.Error -> result
                NetworkResult.Loading -> NetworkResult.Loading
            }
        }

    private fun storeSessionAfterPasswordChange(session: ChangePasswordResponse) {
        val accessToken = session.accessToken?.takeIf { it.isNotBlank() } ?: return
        rotator.adoptTokens(accessToken, session.refreshToken?.takeIf { it.isNotBlank() })
        session.role?.let(sessionManager::saveRole)
    }

    fun storeSession(loginResponse: LoginResponse) {
        sessionManager.saveLoginResponse(loginResponse)
    }

    fun saveUserRole(role: AccountRole?) {
        sessionManager.saveRole(role)
    }

    /**
     * Rotates the session tokens (POST auth/refresh) so the access token carries the user's CURRENT role in the
     * database, then stores that role and profile from auth/me. Used after a role change (e.g. an event request
     * approval upgrading an attendee to organizer) so the client reflects it without a logout/login. Skipped when
     * the tokens were rotated within the last minute; offline keeps the cached session.
     */
    suspend fun refreshSessionToken(): SessionRefresher.Result = SessionRefresher.refreshNow(appContext)
}
