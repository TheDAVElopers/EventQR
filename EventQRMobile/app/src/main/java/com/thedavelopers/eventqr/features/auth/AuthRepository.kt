package com.thedavelopers.eventqr.features.auth

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse
import com.thedavelopers.eventqr.features.auth.model.dto.RegisterRequest
import com.thedavelopers.eventqr.features.auth.model.dto.ChangePasswordRequest
import com.thedavelopers.eventqr.features.auth.model.dto.ForgotPasswordRequest
import com.thedavelopers.eventqr.features.auth.model.dto.ResetPasswordRequest
import com.thedavelopers.eventqr.features.auth.model.dto.ResetTokenValidationResponse
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse

open class AuthRepository(context: Context) {
    private val apiService = ApiClient.getService(context)
    private val sessionManager = SessionManager(context)

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

    suspend fun forgotPassword(email: String) = safeApiCall {
        apiService.forgotPassword(ForgotPasswordRequest(email))
    }

    suspend fun validateResetToken(token: String) = safeApiCall {
        apiService.validateResetToken(token)
    }

    suspend fun resetPassword(token: String, newPassword: String, confirmPassword: String) = safeApiCall {
        apiService.resetPassword(ResetPasswordRequest(token, newPassword, confirmPassword))
    }

    suspend fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) = safeApiCall {
        apiService.changePassword(ChangePasswordRequest(currentPassword, newPassword, confirmPassword))
    }

    fun storeSession(loginResponse: LoginResponse) {
        sessionManager.saveLoginResponse(loginResponse)
    }

    fun saveUserRole(role: AccountRole?) {
        sessionManager.saveRole(role)
    }

    /**
     * Re-issues the access token against the user's CURRENT role in the database
     * and stores the refreshed token locally. Used after a role change (e.g. an
     * event request approval upgrading an attendee to organizer) so the client
     * reflects the new role without requiring a logout/login.
     *
     * The refresh response carries the fresh token + role but omits some profile
     * fields (the backend login record has no phone), so the existing locally
     * stored profile values are preserved.
     */
    suspend fun refreshSessionToken(): NetworkResult<LoginResponse> =
        safeApiCall { apiService.refreshToken() }.also { result ->
            if (result is NetworkResult.Success) {
                val refreshed = result.data
                // Preserve locally stored profile fields that the refresh response omits.
                val merged = refreshed.copy(
                    phone = sessionManager.getPhone(),
                    fullName = sessionManager.getFullName() ?: refreshed.fullName,
                    email = sessionManager.getEmail() ?: refreshed.email,
                    userId = sessionManager.getUserId()?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: refreshed.userId,
                )
                sessionManager.saveLoginResponse(merged)
            }
        }
}
