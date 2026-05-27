package com.thedavelopers.eventqr.features.auth.model.dto

import com.thedavelopers.eventqr.core.api.dto.AccountRole
import java.util.UUID

data class LoginRequest(
    val email: String,
    val password: String,
)

data class LoginResponse(
    val accessToken: String,
    val userId: UUID,
    val email: String,
    val phone: String? = null,
    val fullName: String,
    val role: AccountRole?,
    val message: String? = null,
)

data class RegisterRequest(
    val email: String,
    val phone: String? = null,
    val fullName: String,
    val phoneNumber: String? = null,
    val password: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val email: String,
    val resetCode: String,
    val newPassword: String
)

data class PasswordChangeRequest(
    val currentPassword: String,
    val newPassword: String
)
