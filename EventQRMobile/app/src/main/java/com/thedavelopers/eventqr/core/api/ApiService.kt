package com.thedavelopers.eventqr.core.api

import com.thedavelopers.eventqr.core.api.dto.ApiResponse
import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationRequest
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSnapshot
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointRuleRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.PointRuleResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeRequest
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>

    @POST("users")
    suspend fun createUser(@Body request: UserRequest): ApiResponse<UserResponse>

    @GET("users")
    suspend fun getUsers(): ApiResponse<List<UserResponse>>

    @PUT("users/{userId}/role/{role}")
    suspend fun changeUserRole(
        @Path("userId") userId: String,
        @Path("role") role: AccountRole,
    ): ApiResponse<UserResponse>

    @POST("events")
    suspend fun createEvent(@Body request: EventRequest): ApiResponse<EventResponse>

    @PUT("events/{eventId}/review")
    suspend fun reviewEvent(
        @Path("eventId") eventId: String,
        @Body request: EventApprovalRequest,
    ): ApiResponse<EventResponse>

    @PUT("events/{eventId}/activate")
    suspend fun activateEvent(@Path("eventId") eventId: String): ApiResponse<EventResponse>

    @GET("events")
    suspend fun getEvents(): ApiResponse<List<EventResponse>>

    @GET("events/attendee-visible")
    suspend fun getAttendeeVisibleEvents(): ApiResponse<List<AttendeeEventResponse>>

    @GET("events/{eventId}")
    suspend fun getEventById(@Path("eventId") eventId: String): ApiResponse<AttendeeEventResponse>

    @POST("registrations")
    suspend fun createRegistration(@Body request: RegistrationRequest): ApiResponse<RegistrationResponse>

    @GET("registrations/{registrationId}")
    suspend fun getRegistration(@Path("registrationId") registrationId: String): ApiResponse<RegistrationResponse>

    @GET("registrations/event/{eventId}")
    suspend fun getRegistrationsByEvent(@Path("eventId") eventId: String): ApiResponse<List<RegistrationResponse>>

    @GET("qr-credentials/registration/{registrationId}")
    suspend fun getQrCredentialByRegistration(@Path("registrationId") registrationId: String): ApiResponse<QrCredentialSnapshot>

    @PATCH("qr-credentials/{qrCredentialId}/displayed")
    suspend fun markQrDisplayed(@Path("qrCredentialId") qrCredentialId: String): ApiResponse<QrCredentialSnapshot>

    @PATCH("qr-credentials/{qrCredentialId}/downloaded")
    suspend fun markQrDownloaded(@Path("qrCredentialId") qrCredentialId: String): ApiResponse<QrCredentialSnapshot>

    @POST("scan-purposes")
    suspend fun createScanPurpose(@Body request: ScanPurposeRequest): ApiResponse<ScanPurposeResponse>

    @GET("scan-purposes/event/{eventId}")
    suspend fun getScanPurposesByEvent(@Path("eventId") eventId: String): ApiResponse<List<ScanPurposeResponse>>

    @POST("transactions")
    suspend fun createTransaction(@Body request: TransactionRequest): ApiResponse<TransactionResponse>

    @GET("transactions/event/{eventId}")
    suspend fun getTransactionsByEvent(@Path("eventId") eventId: String): ApiResponse<List<TransactionResponse>>

    @POST("id-printing")
    suspend fun printId(@Body request: IdPrintRequest): ApiResponse<IdPrintResponse>

    @GET("id-printing/event/{eventId}")
    suspend fun getIdPrintsByEvent(@Path("eventId") eventId: String): ApiResponse<List<IdPrintResponse>>

    @GET("dashboard")
    suspend fun getDashboard(): ApiResponse<DashboardSummary>

    @POST("rewards/rules")
    suspend fun savePointRule(@Body request: PointRuleRequest): ApiResponse<PointRuleRequest>

    @POST("rewards")
    suspend fun saveReward(@Body request: RewardRequest): ApiResponse<RewardResponse>

    @POST("rewards/redeem")
    suspend fun redeemReward(@Body request: RewardRedemptionRequest): ApiResponse<RewardRedemptionResponse>

    @GET("rewards/event/{eventId}")
    suspend fun getRewardsByEvent(@Path("eventId") eventId: String): ApiResponse<List<RewardResponse>>

    @GET("rewards/balance/{eventId}/{attendeeUserId}")
    suspend fun getRewardBalance(
        @Path("eventId") eventId: String,
        @Path("attendeeUserId") attendeeUserId: String,
    ): ApiResponse<PointBalanceResponse>

    @GET("rewards/redemptions/{eventId}")
    suspend fun getRewardRedemptions(@Path("eventId") eventId: String): ApiResponse<List<RewardRedemptionResponse>>

    @GET("rewards/rules/{eventId}")
    suspend fun getPointRules(@Path("eventId") eventId: String): ApiResponse<List<PointRuleResponse>>

    @GET("reports/event/{eventId}")
    suspend fun getEventReport(@Path("eventId") eventId: String): ApiResponse<EventReportSnapshot>

    @POST("notifications")
    suspend fun createNotification(@Body request: NotificationRequest): ApiResponse<NotificationResponse>

    @GET("notifications/recipient/{recipientUserId}")
    suspend fun getNotificationsByRecipient(@Path("recipientUserId") recipientUserId: String): ApiResponse<List<NotificationResponse>>

    @GET("notifications/event/{eventId}")
    suspend fun getNotificationsByEvent(@Path("eventId") eventId: String): ApiResponse<List<NotificationResponse>>

    @GET("health")
    suspend fun healthCheck(): ResponseBody
}
