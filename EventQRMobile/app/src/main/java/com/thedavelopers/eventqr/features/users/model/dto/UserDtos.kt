package com.thedavelopers.eventqr.features.users.model.dto

import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.AccountStatus
import java.util.UUID

data class UserRequest(
    val email: String,
    val fullName: String,
    val phoneNumber: String? = null,
    val password: String,
    val role: AccountRole,
)

data class UserResponse(
    val userId: UUID,
    val email: String,
    val fullName: String,
    val phoneNumber: String? = null,
    val role: AccountRole,
    val status: AccountStatus,
    val avatarFileId: String? = null,
)

data class ProfileUpdateRequest(
    val fullName: String,
    val phoneNumber: String? = null
)
