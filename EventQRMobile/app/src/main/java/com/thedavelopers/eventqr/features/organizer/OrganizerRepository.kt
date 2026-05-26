package com.thedavelopers.eventqr.features.organizer

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationRequest
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSnapshot
import com.thedavelopers.eventqr.features.rewards.model.dto.PointRuleRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeRequest
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest

class OrganizerRepository(context: Context) {
    private val apiService = ApiClient.getService(context)

    suspend fun getEvents() = safeApiCall { apiService.getEvents() }
    suspend fun createEvent(request: EventRequest) = safeApiCall { apiService.createEvent(request) }
    suspend fun reviewEvent(eventId: String, request: EventApprovalRequest) = safeApiCall { apiService.reviewEvent(eventId, request) }
    suspend fun activateEvent(eventId: String) = safeApiCall { apiService.activateEvent(eventId) }

    suspend fun getUsers() = safeApiCall { apiService.getUsers() }
    suspend fun createUser(request: UserRequest) = safeApiCall { apiService.createUser(request) }
    suspend fun changeUserRole(userId: String, role: com.thedavelopers.eventqr.core.api.dto.AccountRole) = safeApiCall { apiService.changeUserRole(userId, role) }

    suspend fun getRegistrationsByEvent(eventId: String) = safeApiCall { apiService.getRegistrationsByEvent(eventId) }

    suspend fun createScanPurpose(request: ScanPurposeRequest) = safeApiCall { apiService.createScanPurpose(request) }
    suspend fun getScanPurposesByEvent(eventId: String) = safeApiCall { apiService.getScanPurposesByEvent(eventId) }

    suspend fun saveReward(request: RewardRequest) = safeApiCall { apiService.saveReward(request) }
    suspend fun savePointRule(request: PointRuleRequest) = safeApiCall { apiService.savePointRule(request) }
    suspend fun getRewardsByEvent(eventId: String) = safeApiCall { apiService.getRewardsByEvent(eventId) }
    suspend fun getPointRules(eventId: String) = safeApiCall { apiService.getPointRules(eventId) }
    suspend fun getRewardRedemptions(eventId: String) = safeApiCall { apiService.getRewardRedemptions(eventId) }

    suspend fun getEventReport(eventId: String) = safeApiCall { apiService.getEventReport(eventId) }

    suspend fun createNotification(request: NotificationRequest) = safeApiCall { apiService.createNotification(request) }
    suspend fun getNotificationsByEvent(eventId: String) = safeApiCall { apiService.getNotificationsByEvent(eventId) }
}