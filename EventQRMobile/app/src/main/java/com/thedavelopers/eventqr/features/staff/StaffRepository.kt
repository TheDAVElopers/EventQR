package com.thedavelopers.eventqr.features.staff

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.attendee.loadAllPages
import com.thedavelopers.eventqr.features.staff.model.dto.StaffTodaySummary
import com.thedavelopers.eventqr.features.staff.model.dto.StaffTransactionSummary
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdBatchPrintRequest
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionGrantRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanRequest
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeRequest
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

open class StaffRepository(context: Context) {
    private val apiService = ApiClient.getService(context)

    open suspend fun getEvents(): NetworkResult<List<StaffAssignedEventResponse>> = safeApiCall { apiService.getStaffEvents() }

    suspend fun getEventById(eventId: String) = safeApiCall { apiService.getStaffEventById(eventId) }

    suspend fun getScanPurposesByEvent(eventId: String) = safeApiCall { apiService.getStaffScanPurposes(eventId) }

    suspend fun verifyScan(request: TransactionRequest): NetworkResult<ScanVerificationResponse> = safeApiCall {
        apiService.verifyScan(request.eventId.toString(), request)
    }

    suspend fun createTransaction(request: TransactionRequest, purposeCode: ScanPurposeCode) = safeApiCall {
        when (purposeCode) {
            ScanPurposeCode.ENTRY -> apiService.logEntry(request.eventId.toString(), request)
            ScanPurposeCode.ATTENDANCE -> apiService.logAttendance(request.eventId.toString(), request)
            ScanPurposeCode.BENEFIT_CLAIM -> apiService.logBenefitClaim(request.eventId.toString(), request)
            ScanPurposeCode.BOOTH_VISIT, ScanPurposeCode.SESSION_VISIT -> apiService.logBoothVisit(request.eventId.toString(), request)
            ScanPurposeCode.REWARD_REDEMPTION_SCAN, ScanPurposeCode.REWARD_REDEMPTION -> apiService.logRewardRedemption(request.eventId.toString(), request)
            ScanPurposeCode.EXIT -> apiService.logExit(request.eventId.toString(), request)
            else -> apiService.createTransaction(request)
        }
    }

    /** Paged event transactions, optionally limited to one attendee. Read `totalElements` for counts. */
    open suspend fun getTransactionsByEvent(
        eventId: String,
        attendeeUserId: String? = null,
        page: Int = 0,
        size: Int = 20,
    ): NetworkResult<PageResponse<TransactionResponse>> =
        safeApiCall { apiService.getStaffTransactions(eventId, attendeeUserId, page, size) }

    /** The caller's own scans, newest first, one server page at a time. */
    open suspend fun getMyTransactions(
        eventId: String? = null,
        purposeId: String? = null,
        page: Int = 0,
        size: Int = 20,
    ): NetworkResult<PageResponse<TransactionResponse>> = safeApiCall {
        apiService.getStaffMyTransactions(eventId, purposeId, page, size)
    }

    open suspend fun getMyTransactionSummary(eventId: String? = null, purposeId: String? = null): NetworkResult<StaffTransactionSummary> =
        safeApiCall { apiService.getStaffTransactionSummary(eventId, purposeId) }

    suspend fun getTodayTransactionsByEvent(eventId: String) = safeApiCall { apiService.getStaffTodayTransactions(eventId) }

    open suspend fun getMyTodaySummary(): NetworkResult<StaffTodaySummary> = safeApiCall { apiService.getStaffTodaySummary() }

    open suspend fun getMyTodayTransactions() = safeApiCall { apiService.getStaffMyTodayTransactions() }

    suspend fun getAttendeeTransactions(eventId: String, attendeeId: String) = safeApiCall { apiService.getStaffAttendeeTransactions(eventId, attendeeId) }

    suspend fun getAttendeeByEvent(eventId: String, attendeeId: String) = safeApiCall {
        apiService.getStaffAttendee(eventId, attendeeId)
    }

    suspend fun getRewardBalance(eventId: String, attendeeUserId: String) = safeApiCall {
        apiService.getRewardBalance(eventId, attendeeUserId)
    }

    suspend fun getRewardsByEvent(eventId: String) = safeApiCall {
        apiService.getRewardsByEvent(eventId)
    }

    suspend fun rewardRedemptionScan(request: RewardRedemptionScanRequest) = safeApiCall {
        apiService.rewardRedemptionScan(request)
    }

    suspend fun redeemRewardStaff(request: RewardRedemptionGrantRequest) = safeApiCall {
        apiService.redeemRewardStaff(request)
    }

    suspend fun getLatestScan(eventId: String) = safeApiCall { apiService.getLatestScan(eventId) }

    suspend fun printAttendeeId(eventId: String, attendeeId: String) = safeApiCall { apiService.printAttendeeId(eventId, attendeeId) }

    suspend fun reprintAttendeeId(eventId: String, attendeeId: String) = safeApiCall { apiService.reprintAttendeeId(eventId, attendeeId) }

    suspend fun getStaffPrintLogs(eventId: String) = safeApiCall { apiService.getStaffPrintLogs(eventId) }

    suspend fun printIdBatch(eventId: String, attendeeUserIds: List<java.util.UUID>, reprint: Boolean) =
        safeApiCall { apiService.printIdBatch(eventId, IdBatchPrintRequest(attendeeUserIds, reprint)) }

    /** One server page of an event's registrations; [query] is searched server-side (name, email, registration number). */
    open suspend fun getRegistrationsPage(
        eventId: String,
        query: String?,
        page: Int,
        size: Int = REGISTRATIONS_PAGE_SIZE,
    ): NetworkResult<PageResponse<RegistrationResponse>> =
        safeApiCall { apiService.getRegistrationsByEvent(eventId, page, size, query?.trim()?.takeIf { it.isNotEmpty() }) }

    /** Server-side count of registrations with [status] (null = all), read from `totalElements` of a size-1 page. */
    open suspend fun countRegistrations(eventId: String, status: RegistrationStatus? = null): NetworkResult<Long> =
        when (val r = safeApiCall { apiService.getRegistrationsByEvent(eventId, 0, 1, null, status?.name) }) {
            is NetworkResult.Success -> NetworkResult.Success(r.data.totalElements)
            is NetworkResult.Error -> r
            NetworkResult.Loading -> NetworkResult.Loading
        }

    /** Every registration (optionally matching [query]); errors instead of returning a truncated list. */
    open suspend fun getAllRegistrations(eventId: String, query: String? = null): NetworkResult<List<RegistrationResponse>> {
        val q = query?.trim()?.takeIf { it.isNotEmpty() }
        return loadAllPages<RegistrationResponse, Long>(MAX_REGISTRATION_PAGES) { page ->
            apiService.getRegistrationsByEvent(eventId, page, ALL_PAGES_SIZE, q)
        }
    }

    suspend fun getNotificationsByRecipient(recipientUserId: String) = safeApiCall { apiService.getNotificationsByRecipient(recipientUserId) }

    open suspend fun getMyNotifications(): NetworkResult<List<NotificationResponse>> = safeApiCall { apiService.getMyNotifications() }

    suspend fun markNotificationRead(notificationId: String) = safeApiCall { apiService.markNotificationRead(notificationId) }

    suspend fun markAllNotificationsRead() = safeApiCall { apiService.markAllNotificationsRead() }

    companion object {
        const val REGISTRATIONS_PAGE_SIZE = 20
        const val ALL_PAGES_SIZE = 100
        const val MAX_REGISTRATION_PAGES = 20
    }
}
