package com.thedavelopers.eventqr.features.organizer

import android.content.Context
import com.thedavelopers.eventqr.R
import com.google.gson.JsonElement
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerAttendeeDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDashboardDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerScanPurposeDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerScanPurposeRequestDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerStaffDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionRuleDto
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerUserSearchDto
import com.thedavelopers.eventqr.features.organizer.model.dto.StaffAssignmentRequestDto
import com.thedavelopers.eventqr.features.organizer.model.dto.StaffAssignmentUpdateRequestDto
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeRequest
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest
import java.io.File
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

data class OrganizerMvpLoad<T>(
    val data: T,
    val source: OrganizerMvpDataSource,
    val message: String? = null,
)

enum class OrganizerMvpDataSource {
    BACKEND,
    ERROR,
}

class OrganizerRepository(private val context: Context) {
  private val apiService = ApiClient.getService(context)
  // Cached in-memory event store (replaces OrganizerMvpPlaceholders cachedEvents)
  private var cachedEvents: List<OrganizerMvpEvent> = emptyList()

    private val selectionPrefs = context.getSharedPreferences("organizer_mvp_selection", Context.MODE_PRIVATE)

    private fun List<OrganizerMvpEvent>.manageable(): List<OrganizerMvpEvent> =
        filter {
            it.status.equals("Approved", ignoreCase = true) ||
                it.status.equals("Active", ignoreCase = true) ||
                it.status.equals("Completed", ignoreCase = true)
        }

    suspend fun getApprovedOrganizerEvents(): List<OrganizerMvpEvent> {
        val now = System.currentTimeMillis()
        val ttlExpired = (now - lastCacheTime) > CACHE_TTL_MS
        return if (cachedEvents.isNotEmpty() && !ttlExpired) {
            cachedEvents.manageable()
        } else {
            val result = fetchOrganizerEvents()
            when (result) {
                is NetworkResult.Success -> {
                    val mapped = result.data.map { it.toMvpEvent(context) }
                    cachedEvents = mapped
                    lastCacheTime = System.currentTimeMillis()
                    mapped.manageable()
                }
                else -> emptyList()
            }
        }
    }

    fun getSelectedEventId(): String? = selectionPrefs.getString(KEY_SELECTED_EVENT_ID, null)

    fun saveSelectedEventId(eventId: String?) {
        selectionPrefs.edit().apply {
            if (eventId.isNullOrBlank()) remove(KEY_SELECTED_EVENT_ID) else putString(KEY_SELECTED_EVENT_ID, eventId)
        }.apply()
    }

    fun resolveSelectedEvent(events: List<OrganizerMvpEvent>, requestedEventId: String? = null): OrganizerMvpEvent? {
        val manageable = events.manageable()
        val selected = requestedEventId?.takeIf { it.isNotBlank() }?.let { eventId ->
            manageable.firstOrNull { it.id == eventId }
        } ?: manageable.firstOrNull { it.id == getSelectedEventId() } ?: manageable.firstOrNull()
        saveSelectedEventId(selected?.id)
        return selected
    }

    suspend fun loadEventsForMvp(): OrganizerMvpLoad<List<OrganizerMvpEvent>> {
        return when (val result = fetchOrganizerEvents()) {
                    is NetworkResult.Success -> {
                        val mapped = result.data.map { it.toMvpEvent(context) }
                        cachedEvents = mapped
                        lastCacheTime = System.currentTimeMillis()
                        OrganizerMvpLoad(mapped, OrganizerMvpDataSource.BACKEND)
                    }
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadEventForMvp(eventId: String): OrganizerMvpLoad<OrganizerMvpEvent?> {
        return when (val result = fetchOrganizerEvent(eventId)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.toMvpEvent(context), OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(null, OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(null, OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadDashboardForMvp(): OrganizerMvpLoad<OrganizerDashboardDto?> {
        return when (val result = fetchOrganizerDashboardSummary()) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(null, OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(null, OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadAttendeesForMvp(eventId: String): OrganizerMvpLoad<List<OrganizerMvpAttendee>> {
        return when (val result = fetchOrganizerAttendees(eventId)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.map { it.toMvpAttendee(context) }, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadTransactionsForMvp(eventId: String, eventTitle: String): OrganizerMvpLoad<List<OrganizerMvpTransaction>> {
        return when (val result = fetchOrganizerTransactions(eventId)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.map { it.toMvpTransaction(context, eventTitle) }, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadScanPurposesForMvp(eventId: String): OrganizerMvpLoad<List<OrganizerMvpScanPurpose>> {
        return when (val result = fetchOrganizerScanPurposes(eventId)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.map { it.toMvpScanPurpose(context) }, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadStaffForMvp(event: OrganizerMvpEvent): OrganizerMvpLoad<List<OrganizerMvpStaff>> {
        return when (val result = fetchOrganizerStaff(event.id)) {
            is NetworkResult.Success -> {
                val mapped = result.data.map { it.toMvpStaff(context, event.title) }
                OrganizerMvpLoad(mapped, OrganizerMvpDataSource.BACKEND)
            }
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun searchStaffUsersForMvp(query: String): OrganizerMvpLoad<List<OrganizerMvpStaff>> {
        return when (val result = searchOrganizerUsers(query)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.map { it.toAvailableStaff(context) }, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun addStaffForMvp(event: OrganizerMvpEvent, staff: OrganizerMvpStaff): OrganizerMvpLoad<OrganizerMvpStaff> {
        val request = buildStaffAssignmentRequest(staff)
        return when (val result = addOrganizerStaff(event.id, request)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.toMvpStaff(context, event.title), OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(staff, OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(staff, OrganizerMvpDataSource.ERROR, null)
        }
    }

    /** PATCH /organizer/events/{eventId}/staff/{assignmentId}: [staff].id is the ASSIGNMENT id, not the user id. */
    suspend fun updateStaffForMvp(event: OrganizerMvpEvent, staff: OrganizerMvpStaff): OrganizerMvpLoad<OrganizerMvpStaff> {
        val request = buildStaffUpdateRequest(staff)
        return when (val result = updateOrganizerStaff(event.id, staff.id, request)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.toMvpStaff(context, event.title), OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(staff, OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(staff, OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun removeStaffForMvp(event: OrganizerMvpEvent, staff: OrganizerMvpStaff): OrganizerMvpLoad<Unit> {
        return when (val result = removeOrganizerStaff(event.id, staff.id)) {
            is NetworkResult.Success -> OrganizerMvpLoad(Unit, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(Unit, OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(Unit, OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun saveScanPurposesForMvp(eventId: String, purposes: List<OrganizerMvpScanPurpose>): OrganizerMvpLoad<List<OrganizerMvpScanPurpose>> {
        val saved = mutableListOf<OrganizerMvpScanPurpose>()
        purposes.forEach { purpose ->
            val request = purpose.toOrganizerRequest()
            val result = if (purpose.id.isNullOrBlank()) {
                createOrganizerScanPurpose(eventId, request)
            } else {
                updateOrganizerScanPurpose(eventId, purpose.id, request)
            }
            when (result) {
                is NetworkResult.Success -> saved.add(result.data.toMvpScanPurpose(context))
                is NetworkResult.Error -> return OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
                NetworkResult.Loading -> Unit
            }
        }
        return OrganizerMvpLoad(saved.ifEmpty { purposes }, OrganizerMvpDataSource.BACKEND)
    }

    suspend fun loadTransactionRulesForMvp(eventId: String): OrganizerMvpLoad<List<OrganizerTransactionRuleDto>> {
        return when (val result = getTransactionRules(eventId)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun saveTransactionRuleForMvp(
        eventId: String,
        request: TransactionRuleRequest,
    ): OrganizerMvpLoad<OrganizerTransactionRuleDto> {
        return when (val result = saveTransactionRule(eventId, request)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(OrganizerTransactionRuleDto(eventId = UUID.fromString(eventId), scanPurposeId = request.scanPurposeId), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(OrganizerTransactionRuleDto(eventId = UUID.fromString(eventId), scanPurposeId = request.scanPurposeId), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun enableScanPurposeForMvp(eventId: String, purposeId: String, enabled: Boolean) =
        if (enabled) safeApiCall { apiService.enableOrganizerScanPurpose(eventId, purposeId) }
        else safeApiCall { apiService.disableOrganizerScanPurpose(eventId, purposeId) }

    suspend fun updateScanPurposeTrackingOnlyForMvp(eventId: String, purposeId: String, trackingOnly: Boolean) =
        safeApiCall { apiService.updateOrganizerScanPurposeTrackingOnly(eventId, purposeId, trackingOnly) }

    suspend fun deleteScanPurposeForMvp(eventId: String, purposeId: String) =
        safeApiCall { apiService.deleteOrganizerScanPurpose(eventId, purposeId) }

    suspend fun getEvents(): NetworkResult<List<EventResponse>> =
        when (val result = safeApiCall { apiService.getEvents() }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.content)
            is NetworkResult.Error -> result
            NetworkResult.Loading -> NetworkResult.Loading
        }
    suspend fun fetchOrganizerEvents() = safeApiCall { apiService.getOrganizerEvents() }
    suspend fun fetchOrganizerEvent(eventId: String) = safeApiCall { apiService.getOrganizerEvent(eventId) }
    suspend fun updateOrganizerEvent(eventId: String, request: com.thedavelopers.eventqr.features.events.model.dto.EventRequest) =
        safeApiCall { apiService.updateOrganizerEvent(eventId, request) }
    suspend fun uploadEventBanner(file: File): NetworkResult<com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse> = safeApiCall {
        val contentType = detectImageMediaType(file) ?: "image/jpeg"
        val requestBody = file.asRequestBody(contentType.toMediaTypeOrNull())
        val uploadName = ensureImageExtension(file.name, contentType)
        val part = MultipartBody.Part.createFormData("file", uploadName, requestBody)
        apiService.uploadEventLogo(part)
    }
    suspend fun getStoredFile(fileId: String) = safeApiCall { apiService.getStoredFile(fileId) }
    suspend fun fetchOrganizerDashboardSummary() = safeApiCall { apiService.getOrganizerDashboardSummary() }
    suspend fun fetchOrganizerDashboard(eventId: String) = safeApiCall { apiService.getOrganizerDashboard(eventId) }
    suspend fun fetchOrganizerAttendees(eventId: String) = safeApiCall { apiService.getOrganizerAttendees(eventId) }
    suspend fun fetchOrganizerTransactions(eventId: String) = safeApiCall { apiService.getOrganizerTransactions(eventId) }
    suspend fun fetchOrganizerStaff(eventId: String) = safeApiCall { apiService.getOrganizerStaff(eventId) }
    suspend fun addOrganizerStaff(eventId: String, request: StaffAssignmentRequestDto) =
        safeApiCall { apiService.addOrganizerStaff(eventId, request) }
    suspend fun updateOrganizerStaff(eventId: String, assignmentId: String, request: StaffAssignmentUpdateRequestDto) =
        safeApiCall { apiService.updateOrganizerStaff(eventId, assignmentId, request) }
    suspend fun removeOrganizerStaff(eventId: String, assignmentId: String) =
        safeApiCall { apiService.removeOrganizerStaff(eventId, assignmentId) }
    suspend fun searchOrganizerUsers(query: String) = safeApiCall { apiService.searchOrganizerUsers(query) }
    suspend fun fetchOrganizerScanPurposes(eventId: String) = safeApiCall { apiService.getOrganizerScanPurposes(eventId) }
    suspend fun createOrganizerScanPurpose(eventId: String, request: OrganizerScanPurposeRequestDto) =
        safeApiCall { apiService.createOrganizerScanPurpose(eventId, request) }
    suspend fun updateOrganizerScanPurpose(eventId: String, purposeId: String, request: OrganizerScanPurposeRequestDto) =
        safeApiCall { apiService.updateOrganizerScanPurpose(eventId, purposeId, request) }

    suspend fun createEvent(request: EventRequest) = safeApiCall { apiService.createEvent(request) }
    suspend fun reviewEvent(eventId: String, request: EventApprovalRequest) = safeApiCall { apiService.reviewEvent(eventId, request) }
    suspend fun activateEvent(eventId: String) = safeApiCall { apiService.activateEvent(eventId) }


    suspend fun getRegistrationsByEvent(eventId: String): NetworkResult<List<RegistrationResponse>> =
        when (val result = safeApiCall { apiService.getRegistrationsByEvent(eventId) }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.content)
            is NetworkResult.Error -> result
            NetworkResult.Loading -> NetworkResult.Loading
        }

    suspend fun createScanPurpose(request: ScanPurposeRequest) = safeApiCall { apiService.createScanPurpose(request) }
    suspend fun getScanPurposesByEvent(eventId: String) = safeApiCall { apiService.getScanPurposesByEvent(eventId) }

    suspend fun saveReward(request: RewardRequest) = safeApiCall { apiService.saveReward(request) }
    suspend fun getRewardsByEvent(eventId: String) = safeApiCall { apiService.getRewardsByEvent(eventId) }
    suspend fun getRewardRedemptions(eventId: String) = safeApiCall { apiService.getRewardRedemptions(eventId) }

    suspend fun getTransactionsByEvent(eventId: String) = safeApiCall { apiService.getTransactionsByEvent(eventId) }

    suspend fun getTransactionRules(eventId: String) = safeApiCall { apiService.getOrganizerTransactionRules(eventId) }

    suspend fun saveTransactionRule(
        eventId: String,
        request: TransactionRuleRequest,
    ) = safeApiCall { apiService.saveOrganizerTransactionRule(eventId, request) }

    suspend fun getMyNotifications(
        eventId: java.util.UUID? = null,
        notificationType: NotificationType? = null,
    ): NetworkResult<List<NotificationResponse>> = safeApiCall { apiService.getMyNotifications(eventId = eventId, notificationType = notificationType) }

    suspend fun markNotificationRead(notificationId: String): NetworkResult<NotificationResponse> =
        safeApiCall { apiService.markNotificationRead(notificationId) }

    suspend fun markAllNotificationsRead(): NetworkResult<Unit> =
        safeApiCall { apiService.markAllNotificationsRead() }

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

    companion object {
    const val CACHE_TTL_MS = 300_000L // 5 minutes
    private var lastCacheTime = 0L
        private const val KEY_SELECTED_EVENT_ID = "selected_event_id"
    }
}

// Kept in sync with backend OrganizerService.displayStatus() (and lifecycleStatus()/approvedOnly()
// on the client): ACTIVE is a distinct label from APPROVED so an ongoing event is never misclassified
// as Upcoming.
private fun OrganizerEventDto.toMvpEvent(context: Context): OrganizerMvpEvent = OrganizerMvpEvent(
    id = eventId.toString(),
    title = title ?: "",
    organizerName = organizerName ?: context.getString(R.string.organizer_repo_organizer),
    dateTime = dateTime ?: "-",
    shortDate = shortDate ?: "-",
    venue = venue ?: "Venue not set",
    status = status ?: "Pending",
    submittedDate = submittedDate ?: "-",
    adminRemarks = adminRemarks ?: context.getString(R.string.organizer_repo_no_admin_remarks),
    additionalOrganizers = additionalOrganizers.orEmpty().filterNotNull(),
    registeredCount = registeredCount,
    enteredCount = enteredCount,
    attendedCount = attendedCount,
    exitedCount = exitedCount,
    noShowCount = noShowCount,
    totalTransactions = totalTransactions,
    successfulScans = successfulScans,
    rejectedScans = rejectedScans,
    benefitClaims = benefitClaims,
    boothSessionVisits = boothSessionVisits,
    rewardRedemptions = rewardRedemptions,
    totalPointsAwarded = totalPointsAwarded,
    idTemplateStatus = idTemplateStatus ?: context.getString(R.string.organizer_repo_not_configured),
    rewardsStatus = rewardsStatus ?: context.getString(R.string.organizer_repo_not_configured),
    staffCount = staffCount,
    scanPurposesCount = scanPurposesCount,
    description = description.orEmpty(),
    registrationCloseDate = DateFormatters.formatInstant(registrationCloseAt),
    capacity = capacity,
    currentAttendeeCount = currentAttendeeCount,
    availableSlots = availableSlots,
)

private fun OrganizerAttendeeDto.toMvpAttendee(context: Context): OrganizerMvpAttendee = OrganizerMvpAttendee(
    id = attendeeId?.toString() ?: registrationId.toString(),
    eventId = eventId.toString(),
    name = name ?: context.getString(R.string.organizer_repo_unnamed_attendee),
    email = email ?: context.getString(R.string.organizer_repo_no_email),
    phone = phone ?: context.getString(R.string.organizer_repo_not_available),
    registrationStatus = registrationStatus ?: "Registered",
    currentEventStatus = currentEventStatus ?: "Registered",
    points = points,
    lastTransactionTime = lastTransactionTime ?: "-",
    registeredDate = registeredDate ?: "-",
    qrCredentialStatus = qrCredentialStatus ?: if (qrCredentialId != null) "Issued" else "Pending",
    recentTransactions = (recentTransactions ?: emptyList()).mapNotNull { it.toMvpTransactionEntry() },
    recentRejectedScans = recentRejectedScans,
    countedAsRegistered = countedAsRegistered ?: !(
        currentEventStatus.equals("Cancelled", ignoreCase = true) ||
            currentEventStatus.equals("No Show", ignoreCase = true)
        ),
)

private fun JsonElement.toMvpTransactionEntry(): OrganizerMvpTransactionEntry? = when {
    isJsonPrimitive && asJsonPrimitive.isString ->
        OrganizerMvpTransactionEntry(type = asString, timestamp = null)
    isJsonObject -> {
        val type = asJsonObject.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val timestamp = asJsonObject.get("timestamp")?.takeIf { it.isJsonPrimitive }?.asString
        if (type.isBlank()) null else OrganizerMvpTransactionEntry(type = type, timestamp = timestamp?.takeIf { it.isNotBlank() })
    }
    else -> null
}

private fun OrganizerTransactionDto.toMvpTransaction(context: Context, fallbackEventTitle: String): OrganizerMvpTransaction {
    val rejected = resultStatus == TransactionResult.REJECTED
    val displayType = transactionType.toDisplayType(context)
    return OrganizerMvpTransaction(
        id = transactionId.toString(),
        eventId = eventId.toString(),
        eventTitle = eventTitle ?: fallbackEventTitle,
        attendeeId = attendeeId?.toString() ?: registrationId?.toString() ?: "unknown",
        attendeeName = attendeeName ?: context.getString(R.string.organizer_repo_unknown_attendee),
        attendeeEmail = attendeeEmail.orEmpty(),
        qrId = qrId ?: qrCredentialId?.toString().orEmpty(),
        staffId = staffId?.toString() ?: context.getString(R.string.organizer_repo_not_available),
        staffName = staffName ?: context.getString(R.string.organizer_repo_staff_not_available),
        staffEmail = staffEmail.orEmpty(),
        scanPurpose = scanPurpose ?: displayType,
        type = displayType,
        timestamp = DateFormatters.formatInstant(createdTimestamp),
        status = if (rejected) "Rejected" else "Approved",
        message = message ?: if (rejected) context.getString(R.string.organizer_repo_scan_rejected) else context.getString(R.string.organizer_repo_type_recorded, displayType),
        reason = reason ?: if (rejected) context.getString(R.string.organizer_repo_rejected_scan) else context.getString(R.string.organizer_repo_approved_scan),
        deviceSource = deviceSource ?: context.getString(R.string.organizer_repo_not_available),
        pointsDelta = pointsDelta,
        relatedItem = relatedItem ?: context.getString(R.string.organizer_repo_not_available),
    )
}

private fun OrganizerStaffDto.toMvpStaff(context: Context, eventTitle: String): OrganizerMvpStaff = OrganizerMvpStaff(
    id = assignmentId.toString(),
    name = name ?: context.getString(R.string.organizer_repo_unknown_staff),
    email = email ?: context.getString(R.string.organizer_repo_no_email),
    assignedEventId = eventId.toString(),
    assignedEvent = eventTitle,
    roleLabel = roleLabel ?: "Scanner",
    accessStatus = if (active) "Active" else "Disabled",
    addedDate = DateFormatters.formatInstant(addedAt),
    permissions = permissions.ifEmpty {
        buildList {
            if (canScan) add(StaffPermissions.SCAN)
            if (canPrintId) add(StaffPermissions.PRINT_ID)
            if (canViewLogs) add(StaffPermissions.VIEW_LOGS)
            if (canManageRewards) add(StaffPermissions.MANAGE_REWARDS)
        }
    },
    promotedToStaff = promotedToStaff,
    canScan = canScan,
    canPrintId = canPrintId,
    canViewLogs = canViewLogs,
    canManageRewards = canManageRewards,
)

private fun OrganizerUserSearchDto.toAvailableStaff(context: Context): OrganizerMvpStaff = OrganizerMvpStaff(
    id = userId.toString(),
    name = name ?: context.getString(R.string.organizer_repo_unnamed_user),
    email = email ?: context.getString(R.string.organizer_repo_no_email),
    assignedEventId = "",
    assignedEvent = context.getString(R.string.organizer_repo_not_assigned),
    roleLabel = if (role.equals("STAFF", ignoreCase = true)) "Scanner" else "Support Staff",
    accessStatus = status ?: "Available",
    addedDate = context.getString(R.string.organizer_repo_not_added),
    // Real defaults for a new assignment: scan only. The organizer picks the rest before assigning.
    permissions = listOf(StaffPermissions.SCAN),
    accountRole = role.orEmpty(),
)

private fun OrganizerScanPurposeDto.toMvpScanPurpose(context: Context): OrganizerMvpScanPurpose = OrganizerMvpScanPurpose(
    label = title?.takeIf { it.isNotBlank() } ?: code.toDisplayPurposeName(context),
    description = description ?: title ?: code.toDisplayPurposeName(context),
    enabled = enabled,
    duplicateRule = duplicateRuleSummary ?: code.defaultDuplicateRule(),
    trackingOnly = trackingOnly,
    pointsEnabled = pointsEnabled,
    pointsValue = pointsValue,
    requiredSelectionLabel = requiredSelectionLabel ?: code.defaultRequiredSelection(),
    id = scanPurposeId?.toString(),
    code = code,
)

private fun OrganizerMvpScanPurpose.toOrganizerRequest(): OrganizerScanPurposeRequestDto = OrganizerScanPurposeRequestDto(
    scanPurposeId = id?.toUuidOrNull(),
    title = label,
    code = code ?: label.toScanPurposeCode(),
    enabled = enabled,
    trackingOnly = trackingOnly,
    pointsEnabled = pointsEnabled,
    pointsValue = pointsValue,
    allowDuplicate = duplicateRule.contains("allow", ignoreCase = true),
    duplicateRuleSummary = duplicateRule,
    requiredSelectionLabel = requiredSelectionLabel,
    description = description,
)

/** Add-staff body: Scan QR is always granted, the other flags come from the permission switches. */
internal fun buildStaffAssignmentRequest(staff: OrganizerMvpStaff) = StaffAssignmentRequestDto(
    staffUserId = staff.id.toUuidOrNull(),
    email = staff.email,
    name = staff.name,
    roleLabel = staff.roleLabel,
    canScan = true,
    canPrintId = staff.canPrintId,
    canViewLogs = staff.canViewLogs,
    canManageRewards = staff.canManageRewards,
)

/** Update body for an existing assignment ([OrganizerMvpStaff.id] is the assignment id). */
internal fun buildStaffUpdateRequest(staff: OrganizerMvpStaff) = StaffAssignmentUpdateRequestDto(
    active = staff.accessStatus.equals("Active", ignoreCase = true),
    roleLabel = staff.roleLabel,
    canScan = true,
    canPrintId = staff.canPrintId,
    canViewLogs = staff.canViewLogs,
    canManageRewards = staff.canManageRewards,
)

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

private fun TransactionType.toDisplayType(context: Context): String = when (this) {
    TransactionType.ENTRY -> context.getString(R.string.organizer_repo_type_entry)
    TransactionType.ATTENDANCE -> context.getString(R.string.organizer_repo_type_attendance)
    TransactionType.BENEFIT_CLAIM -> context.getString(R.string.organizer_repo_type_benefit_claim)
    TransactionType.BOOTH_VISIT -> context.getString(R.string.organizer_repo_type_booth_session_visit)
    TransactionType.SESSION_VISIT -> context.getString(R.string.organizer_repo_type_booth_session_visit)
    TransactionType.REWARD_REDEMPTION_SCAN, TransactionType.REWARD_REDEMPTION -> context.getString(R.string.organizer_repo_type_reward_redemption)
    TransactionType.EXIT -> context.getString(R.string.organizer_repo_type_exit)
    TransactionType.ID_PRINT -> context.getString(R.string.organizer_repo_type_id_printing)
    TransactionType.REGISTRATION -> context.getString(R.string.organizer_repo_type_registration)
}

private fun com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.toDisplayPurposeName(context: Context): String = when (this) {
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ENTRY -> context.getString(R.string.organizer_repo_purpose_entrance_logging)
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ATTENDANCE -> context.getString(R.string.organizer_repo_purpose_attendance_recording)
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BENEFIT_CLAIM -> context.getString(R.string.organizer_repo_purpose_benefit_claiming)
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BOOTH_VISIT,
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.SESSION_VISIT -> context.getString(R.string.organizer_repo_purpose_booth_session_visit)
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION_SCAN,
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION -> context.getString(R.string.organizer_repo_purpose_reward_redemption)
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.EXIT -> context.getString(R.string.organizer_repo_purpose_exit_logging)
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ID_PRINT -> context.getString(R.string.organizer_repo_purpose_id_printing)
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REGISTRATION_LOOKUP -> context.getString(R.string.organizer_repo_purpose_id_reprinting)
}

private fun com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.defaultDuplicateRule(): String = when (this) {
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ENTRY -> "Prevent duplicate entry"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ATTENDANCE -> "Prevent duplicate attendance if configured"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BENEFIT_CLAIM -> "Prevent duplicate benefit claim"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION_SCAN,
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION -> "Prevent duplicate reward claim"
    else -> "Allow valid scan once per required selection"
}

private fun com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.defaultRequiredSelection(): String = when (this) {
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BOOTH_VISIT -> "Booth"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.SESSION_VISIT,
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ATTENDANCE -> "Session"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BENEFIT_CLAIM -> "Benefit"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION,
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION_SCAN -> "Reward"
    else -> "Event"
}

private fun String.toScanPurposeCode(): com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode = when {
    contains("reprint", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REGISTRATION_LOOKUP
    contains("print", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ID_PRINT
    contains("attendance", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ATTENDANCE
    contains("benefit", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BENEFIT_CLAIM
    contains("booth", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BOOTH_VISIT
    contains("session", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.SESSION_VISIT
    contains("reward", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION
    contains("exit", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.EXIT
    else -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ENTRY
}

private fun TransactionResponse.toMvpTransaction(
    context: Context,
    eventTitle: String,
    attendeeName: String?,
    purpose: ScanPurposeResponse?,
): OrganizerMvpTransaction {
    val rejected = transactionResult == TransactionResult.REJECTED
    val displayType = transactionType.toDisplayType(context)
    return OrganizerMvpTransaction(
        id = transactionId.toString(),
        eventId = eventId.toString(),
        eventTitle = eventTitle,
        attendeeId = attendeeUserId.toString(),
        attendeeName = attendeeName ?: context.getString(R.string.organizer_repo_attendee),
        attendeeEmail = "",
        qrId = qrCredentialId.toString(),
        staffId = context.getString(R.string.organizer_repo_not_available),
        staffName = context.getString(R.string.organizer_repo_staff_not_available),
        staffEmail = "",
        scanPurpose = purpose?.name ?: displayType,
        type = displayType,
        timestamp = DateFormatters.formatInstant(scannedAt),
        status = if (rejected) "Rejected" else "Successful",
        message = reason ?: if (rejected) context.getString(R.string.organizer_repo_scan_rejected) else context.getString(R.string.organizer_repo_type_recorded, displayType),
        reason = reason ?: if (rejected) context.getString(R.string.organizer_repo_rejected_scan) else context.getString(R.string.organizer_repo_approved_scan),
        deviceSource = context.getString(R.string.organizer_repo_not_available),
        pointsDelta = pointsDelta,
        relatedItem = purpose?.description ?: context.getString(R.string.organizer_repo_not_available),
    )
}
