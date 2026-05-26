package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AttendeeRepository(context: Context) {
    private val apiService = ApiClient.getService(context)
    suspend fun getEvents() = safeApiCall { apiService.getAttendeeVisibleEvents() }
    suspend fun getEvent(eventId: String) = safeApiCall { apiService.getEventById(eventId) }
    suspend fun createRegistration(request: RegistrationRequest) = safeApiCall { apiService.createRegistration(request) }
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
    suspend fun getNotificationsByRecipient(recipientUserId: String) = safeApiCall { apiService.getNotificationsByRecipient(recipientUserId) }
    suspend fun getDashboardSummary() = safeApiCall { apiService.getDashboard() }
    suspend fun parseUuid(value: String?): UUID? = withContext(Dispatchers.Default) {
        runCatching { UUID.fromString(value.orEmpty()) }.getOrNull()
    }
}
