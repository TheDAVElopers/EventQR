package com.thedavelopers.eventqr.features.staff

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeRequest
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

class StaffRepository(context: Context) {
    private val apiService = ApiClient.getService(context)

    suspend fun getEvents(): NetworkResult<List<StaffAssignedEventResponse>> = safeApiCall { apiService.getStaffEvents() }

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
            ScanPurposeCode.REWARD_REDEMPTION_SCAN -> apiService.logRewardRedemption(request.eventId.toString(), request)
            ScanPurposeCode.EXIT -> apiService.logExit(request.eventId.toString(), request)
            else -> apiService.createTransaction(request)
        }
    }

    suspend fun getTransactionsByEvent(eventId: String) = safeApiCall { apiService.getStaffTransactions(eventId) }

    suspend fun getAttendeeByEvent(eventId: String, attendeeId: String) = safeApiCall {
        apiService.getStaffAttendee(eventId, attendeeId)
    }

    suspend fun getRewardBalance(eventId: String, attendeeUserId: String) = safeApiCall {
        apiService.getRewardBalance(eventId, attendeeUserId)
    }

    suspend fun getLatestScan(eventId: String) = safeApiCall { apiService.getLatestScan(eventId) }

    suspend fun printId(request: IdPrintRequest) = safeApiCall { apiService.printId(request) }

    suspend fun getIdPrintsByEvent(eventId: String) = safeApiCall { apiService.getIdPrintsByEvent(eventId) }

    suspend fun getRegistrationsByEvent(eventId: String) = safeApiCall { apiService.getRegistrationsByEvent(eventId) }

    suspend fun getNotificationsByRecipient(recipientUserId: String) = safeApiCall { apiService.getNotificationsByRecipient(recipientUserId) }
}