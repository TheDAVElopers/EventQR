package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID

class AttendeeRepository(context: Context) {
    private val apiService = ApiClient.getService(context)
    suspend fun getEvents() = safeApiCall { apiService.getAttendeeVisibleEvents() }
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
    suspend fun uploadAvatar(file: File): NetworkResult<StoredFileResponse> = safeApiCall {
        val contentType = detectImageMediaType(file) ?: "image/jpeg"
        val requestBody = file.asRequestBody(contentType.toMediaTypeOrNull())
        val uploadName = ensureImageExtension(file.name, contentType)
        val part = MultipartBody.Part.createFormData("file", uploadName, requestBody)
        apiService.uploadAvatar(part)
    }
    suspend fun uploadEventPoster(file: File): NetworkResult<StoredFileResponse> = safeApiCall {
        val contentType = detectImageMediaType(file) ?: "image/jpeg"
        val requestBody = file.asRequestBody(contentType.toMediaTypeOrNull())
        val uploadName = ensureImageExtension(file.name, contentType)
        val part = MultipartBody.Part.createFormData("file", uploadName, requestBody)
        apiService.uploadEventLogo(part)
    }
    suspend fun downloadAvatar(avatarPath: String): NetworkResult<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.downloadAvatar(avatarPath).bytes()
        }.fold(
            onSuccess = { bytes ->
                if (bytes.isNotEmpty()) {
                    NetworkResult.Success(bytes)
                } else {
                    NetworkResult.Error("Avatar image is empty")
                }
            },
            onFailure = { throwable ->
                NetworkResult.Error(throwable.message ?: "Unable to load avatar image", throwable)
            }
        )
    }
    suspend fun getStoredFile(fileId: String) = safeApiCall { apiService.getStoredFile(fileId) }
    suspend fun createRegistration(request: RegistrationRequest) = safeApiCall { apiService.createRegistration(request) }
    suspend fun getMyRegistrations() = safeApiCall { apiService.getMyRegistrations() }
    suspend fun getMyEventTransactions(eventId: String) = safeApiCall { apiService.getMyEventTransactions(eventId) }
    suspend fun getMyTransactions() = safeApiCall { apiService.getMyTransactions() }
    suspend fun createQrCredential(registrationId: String) = safeApiCall { apiService.createQrCredential(registrationId) }
    suspend fun linkQrCredential(registrationId: String) = safeApiCall { apiService.linkQrCredential(registrationId) }
    suspend fun getQrCredentialById(qrCredentialId: String) = safeApiCall { apiService.getQrCredentialById(qrCredentialId) }
    suspend fun getRegistration(registrationId: String) = safeApiCall { apiService.getRegistration(registrationId) }
    suspend fun getRegistrationsByEvent(eventId: String) = safeApiCall { apiService.getRegistrationsByEvent(eventId) }
    suspend fun getQrCredentialByRegistration(registrationId: String) = safeApiCall { apiService.getQrCredentialByRegistration(registrationId) }
    suspend fun markQrDisplayed(qrCredentialId: String) = safeApiCall { apiService.markQrDisplayed(qrCredentialId) }
    suspend fun markQrDownloaded(qrCredentialId: String) = safeApiCall { apiService.markQrDownloaded(qrCredentialId) }
    suspend fun getTransactionsByEvent(eventId: String) = safeApiCall { apiService.getTransactionsByEvent(eventId) }
    suspend fun getRewardsByEvent(eventId: String) = safeApiCall { apiService.getRewardsByEvent(eventId) }
    suspend fun getRewardBalance(eventId: String, attendeeUserId: String) = safeApiCall { apiService.getRewardBalance(eventId, attendeeUserId) }
    suspend fun redeemReward(request: RewardRedemptionRequest) = safeApiCall { apiService.redeemReward(request) }
    suspend fun getRewardRedemptions(eventId: String) = safeApiCall { apiService.getRewardRedemptions(eventId) }
    suspend fun getMyNotifications() = safeApiCall { apiService.getMyNotifications() }
    suspend fun markNotificationRead(notificationId: String) = safeApiCall { apiService.markNotificationRead(notificationId) }
    suspend fun markAllNotificationsRead() = safeApiCall { apiService.markAllNotificationsRead() }
    suspend fun getNotificationsByRecipient(recipientUserId: String) = safeApiCall { apiService.getNotificationsByRecipient(recipientUserId) }
    suspend fun getDashboardSummary() = safeApiCall { apiService.getDashboard() }
    suspend fun parseUuid(value: String?): UUID? = withContext(Dispatchers.Default) {
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
}
