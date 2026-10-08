package com.thedavelopers.eventqr.features.admin

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.features.admin.model.dto.AdminStatsResponse
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestDecisionRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse

open class AdminRepository(private val context: Context) {
    private val apiService = ApiClient.getService(context)

    suspend fun getCurrentUser(): NetworkResult<UserResponse> = safeApiCall { apiService.getAuthMe() }

    suspend fun loadAllEventRequests(): NetworkResult<List<EventRequestResponse>> =
        safeApiCall { apiService.getAdminEventRequests() }

    suspend fun getEventRequest(requestId: String): NetworkResult<EventRequestResponse> =
        safeApiCall { apiService.getAdminEventRequest(requestId) }

    suspend fun loadUsers(role: AccountRole? = null): NetworkResult<List<UserResponse>> =
        when (val result = loadUsersPage(role, null, 0, 100)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.content)
            is NetworkResult.Error -> result
            NetworkResult.Loading -> NetworkResult.Loading
        }

    open suspend fun loadUsersPage(
        role: AccountRole?,
        query: String?,
        page: Int,
        size: Int = USERS_PAGE_SIZE,
    ): NetworkResult<PageResponse<UserResponse>> =
        safeApiCall { apiService.getUsers(page = page, size = size, role = role?.name, query = query?.trim()?.takeIf { it.isNotEmpty() }) }

    suspend fun loadAdminStats(): NetworkResult<AdminStatsResponse> = safeApiCall { apiService.getAdminStats() }

    suspend fun disableUser(userId: String): NetworkResult<UserResponse> =
        safeApiCall { apiService.disableUser(userId) }

    suspend fun enableUser(userId: String): NetworkResult<UserResponse> =
        safeApiCall { apiService.enableUser(userId) }

    suspend fun deleteUser(userId: String): NetworkResult<Unit> =
        safeApiCall { apiService.deleteUser(userId) }

    suspend fun createAdminAccount(
        fullName: String,
        email: String,
        phoneNumber: String?,
        password: String,
    ): NetworkResult<UserResponse> = safeApiCall {
        apiService.createAdminUser(
            UserRequest(
                email = email,
                fullName = fullName,
                phoneNumber = phoneNumber,
                password = password,
                role = AccountRole.ADMIN,
            )
        )
    }

    suspend fun loadEvents(): NetworkResult<List<EventResponse>> =
        when (val result = safeApiCall { apiService.getEvents() }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.content)
            is NetworkResult.Error -> result
            NetworkResult.Loading -> NetworkResult.Loading
        }

    suspend fun loadAuditLogsPage(page: Int, size: Int = AUDIT_PAGE_SIZE, actionPrefix: String? = null): NetworkResult<PageResponse<AuditLogResponse>> =
        when (val result = safeApiCall { apiService.getAdminAuditLogs(page, size, actionPrefix?.takeIf { it.isNotBlank() }) }) {
            is NetworkResult.Success -> NetworkResult.Success(parseAuditLogPage(result.data, sharedGson()))
            is NetworkResult.Error -> result
            NetworkResult.Loading -> NetworkResult.Loading
        }

    suspend fun approveEvent(eventId: String, remarks: String?): NetworkResult<EventRequestResponse> {
        return safeApiCall { apiService.approveEventRequest(eventId, EventRequestDecisionRequest(remarks)) }
    }

    suspend fun rejectEvent(eventId: String, remarks: String?): NetworkResult<EventRequestResponse> {
        return safeApiCall { apiService.rejectEventRequest(eventId, EventRequestDecisionRequest(remarks)) }
    }

    suspend fun upgradeOrganizer(eventId: String): NetworkResult<EventRequestResponse> =
        safeApiCall { apiService.upgradeOrganizerFromEventRequest(eventId) }

    companion object {
        const val USERS_PAGE_SIZE = 20
        const val AUDIT_PAGE_SIZE = 50
    }
}
