package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.ApiResponse
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID

open class AttendeeRepository(context: Context) {
    private val apiService = ApiClient.getService(context)
    suspend fun getEvents(): NetworkResult<List<AttendeeEventResponse>> =
        when (val result = safeApiCall { apiService.getAttendeeVisibleEvents() }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.content)
            is NetworkResult.Error -> result
            NetworkResult.Loading -> NetworkResult.Loading
        }
    suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> =
        loadAllPages(MAX_PAGES, sortKey = { it.eventStartAt }) { page -> apiService.getAttendeeBrowseEvents(page = page, size = PAGE_SIZE) }
    suspend fun getEvent(eventId: String) = safeApiCall { apiService.getEventById(eventId) }
    suspend fun getEventAvailability(eventId: String) = safeApiCall { apiService.getEventAvailability(eventId) }
    suspend fun getOrganizerEvents(): NetworkResult<List<OrganizerEventDto>> = safeApiCall { apiService.getOrganizerEvents() }
    suspend fun createEventRequest(request: EventCreationRequestDto) = safeApiCall { apiService.createEventRequest(request) }
    suspend fun getMyEventRequests() = safeApiCall { apiService.getMyEventRequests() }
    suspend fun getEventRequest(requestId: String) = safeApiCall { apiService.getEventRequest(requestId) }
    suspend fun getMyProfile() = safeApiCall { apiService.getUsersMe() }
    suspend fun updateProfile(fullName: String, phoneNumber: String?) = safeApiCall {
        apiService.updateUsersMe(com.thedavelopers.eventqr.features.users.model.dto.ProfileUpdateRequest(fullName, phoneNumber))
    }
    suspend fun uploadEventPoster(file: File): NetworkResult<StoredFileResponse> = safeApiCall {
        val contentType = detectImageMediaType(file) ?: "image/jpeg"
        val requestBody = file.asRequestBody(contentType.toMediaTypeOrNull())
        val uploadName = ensureImageExtension(file.name, contentType)
        val part = MultipartBody.Part.createFormData("file", uploadName, requestBody)
        apiService.uploadEventLogo(part)
    }
    suspend fun getStoredFile(fileId: String) = safeApiCall { apiService.getStoredFile(fileId) }
    open suspend fun createRegistration(request: RegistrationRequest) = safeApiCall { apiService.createRegistration(request) }
    suspend fun getMyRegistrations(): NetworkResult<List<RegistrationResponse>> =
        when (val result = loadAllPages(MAX_PAGES, sortKey = { it.eventStartAt }) { page -> apiService.getMyRegistrations(page = page, size = PAGE_SIZE) }) {
            is NetworkResult.Success -> result.also { RegistrationsCache.set(it.data) }
            is NetworkResult.Error -> result
            NetworkResult.Loading -> NetworkResult.Loading
        }
    suspend fun getMyEventTransactions(eventId: String) = safeApiCall { apiService.getMyEventTransactions(eventId) }
    suspend fun getMyTransactions() = safeApiCall { apiService.getMyTransactions() }
    suspend fun createQrCredential(registrationId: String) = safeApiCall { apiService.createQrCredential(registrationId) }
    suspend fun linkQrCredential(registrationId: String) = safeApiCall { apiService.linkQrCredential(registrationId) }
    suspend fun getQrCredentialById(qrCredentialId: String) = safeApiCall { apiService.getQrCredentialById(qrCredentialId) }
    open suspend fun cancelRegistration(registrationId: String): NetworkResult<RegistrationResponse> =
        safeApiCall { apiService.cancelRegistration(registrationId) }
    suspend fun getRegistration(registrationId: String) = safeApiCall { apiService.getRegistration(registrationId) }
    suspend fun getRegistrationsByEvent(eventId: String): NetworkResult<List<RegistrationResponse>> =
        when (val result = safeApiCall { apiService.getRegistrationsByEvent(eventId) }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.content)
            is NetworkResult.Error -> result
            NetworkResult.Loading -> NetworkResult.Loading
        }
    suspend fun getQrCredentialByRegistration(registrationId: String) = safeApiCall { apiService.getQrCredentialByRegistration(registrationId) }
    suspend fun markQrDisplayed(qrCredentialId: String) = safeApiCall { apiService.markQrDisplayed(qrCredentialId) }
    suspend fun markQrDownloaded(qrCredentialId: String) = safeApiCall { apiService.markQrDownloaded(qrCredentialId) }
    suspend fun getMyQrCredentialByRegistration(registrationId: String) = safeApiCall { apiService.getMyQrCredentialByRegistration(registrationId) }
    suspend fun getMyQrCredentialById(qrCredentialId: String) = safeApiCall { apiService.getMyQrCredentialById(qrCredentialId) }
    suspend fun markMyQrDisplayed(qrCredentialId: String) = safeApiCall { apiService.markMyQrDisplayed(qrCredentialId) }
    suspend fun markMyQrDownloaded(qrCredentialId: String) = safeApiCall { apiService.markMyQrDownloaded(qrCredentialId) }
    suspend fun getTransactionsByEvent(eventId: String) = safeApiCall { apiService.getTransactionsByEvent(eventId) }
    /** [includeUnavailable] = true also returns inactive/sold-out rewards (used for claim-history name lookups). */
    suspend fun getRewardsByEvent(eventId: String, includeUnavailable: Boolean = false) =
        safeApiCall { apiService.getAttendeeRewards(eventId, includeUnavailable) }
    suspend fun getRewardBalance(eventId: String, attendeeUserId: String) = safeApiCall { apiService.getRewardBalance(eventId, attendeeUserId) }
    suspend fun redeemReward(request: RewardRedemptionRequest) = safeApiCall { apiService.redeemReward(request) }
    suspend fun getRewardRedemptions(eventId: String) = safeApiCall { apiService.getRewardRedemptions(eventId) }
    suspend fun getMyRewardRedemptions(eventId: String) = safeApiCall { apiService.getMyClaimedRewards(eventId) }
    suspend fun getDashboardSummary() = safeApiCall { apiService.getDashboard() }
    suspend fun parseUuid(value: String?): UUID? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        runCatching { UUID.fromString(value.orEmpty()) }.getOrNull()
    }

    private fun detectImageMediaType(file: File): String? {
        val header = ByteArray(8)
        val count = runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrDefault(0)
        if (count >= 3 && (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xFF) == 0xD8 && (header[2].toInt() and 0xFF) == 0xFF) {
            return "image/jpeg"
        }
        if (count >= 4 && (header[0].toInt() and 0xFF) == 0x89 && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) {
            return "image/png"
        }
        val lowerName = file.name.lowercase()
        return when {
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
            lowerName.endsWith(".png") -> "image/png"
            else -> null
        }
    }

    private fun ensureImageExtension(fileName: String, contentType: String): String {
        val lowerName = fileName.lowercase()
        return when (contentType) {
            "image/png" -> if (lowerName.endsWith(".png")) fileName else "$fileName.png"
            else -> if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) fileName else "$fileName.jpg"
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20
    }
}

/**
 * Loads every page until the server reports the last page, an empty page, or a page identical to the
 * previous one (a backend ignoring page/size). A failure on ANY page, or exhausting [maxPages] without
 * reaching the end, returns an error, so callers never see (or cache) a silently truncated list. When [sortKey] is given the combined list is sorted
 * ascending by it (nulls last, stable), so correctness does not depend on backend order.
 */
internal suspend fun <T, K : Comparable<K>> loadAllPages(
    maxPages: Int,
    sortKey: ((T) -> K?)? = null,
    fetch: suspend (page: Int) -> ApiResponse<PageResponse<T>>,
): NetworkResult<List<T>> {
    val all = mutableListOf<T>()
    var previous: List<T>? = null
    for (page in 0 until maxPages) {
        when (val result = safeApiCall { fetch(page) }) {
            is NetworkResult.Success -> {
                val content = result.data.content
                if (content.isEmpty() || content == previous) return NetworkResult.Success(sorted(all, sortKey))
                all += content
                if (result.data.last) return NetworkResult.Success(sorted(all, sortKey))
                previous = content
            }
            is NetworkResult.Error -> return result
            NetworkResult.Loading -> return NetworkResult.Loading
        }
    }
    // Hit maxPages without seeing `last` or an empty page: the list is truncated, so never report it as complete.
    return NetworkResult.Error("Too many results to load. Please try again later.")
}

private fun <T, K : Comparable<K>> sorted(items: List<T>, sortKey: ((T) -> K?)?): List<T> =
    if (sortKey == null) items else items.sortedWith(compareBy(nullsLast()) { sortKey(it) })
