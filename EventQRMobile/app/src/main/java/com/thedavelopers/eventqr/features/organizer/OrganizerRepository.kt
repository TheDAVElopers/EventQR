package com.thedavelopers.eventqr.features.organizer

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationRequest
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerAttendeeDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDashboardDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerOverallReportDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerReportDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerScanPurposeDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerScanPurposeRequestDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerStaffDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionDto
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionRuleDto
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerUserSearchDto
import com.thedavelopers.eventqr.features.organizer.model.dto.StaffAssignmentRequestDto
import com.thedavelopers.eventqr.features.organizer.model.dto.StaffAssignmentUpdateRequestDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSnapshot
import com.thedavelopers.eventqr.features.rewards.model.dto.PointRuleRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeRequest
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest
import java.util.UUID
import kotlinx.coroutines.runBlocking

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
    private val sessionManager = SessionManager(context)
    private val selectionPrefs = context.getSharedPreferences("organizer_mvp_selection", Context.MODE_PRIVATE)

    private fun List<OrganizerMvpEvent>.manageable(): List<OrganizerMvpEvent> =
        filter {
            it.status.equals("Approved", ignoreCase = true) ||
                it.status.equals("Active", ignoreCase = true) ||
                it.status.equals("Completed", ignoreCase = true)
        }

    fun getApprovedOrganizerEvents(): List<OrganizerMvpEvent> =
        if (OrganizerMvpPlaceholders.cachedEvents.isNotEmpty()) {
            OrganizerMvpPlaceholders.cachedEvents.manageable()
        } else {
            runBlocking {
                when (val result = fetchOrganizerEvents()) {
                    is NetworkResult.Success -> {
                        val mapped = result.data.map { it.toMvpEvent() }
                        OrganizerMvpPlaceholders.cachedEvents = mapped
                        mapped.manageable()
                    }
                    else -> emptyList()
                }
            }
        }

    fun getOrganizerAttendees(eventId: String): List<OrganizerMvpAttendee> = emptyList()

    fun getOrganizerTransactions(eventId: String): List<OrganizerMvpTransaction> = emptyList()

    fun getOrganizerStaff(eventId: String): List<OrganizerMvpStaff> = emptyList()

    fun searchAvailableStaffUsers(query: String): List<OrganizerMvpStaff> = emptyList()

    fun getOrganizerScanPurposes(): List<OrganizerMvpScanPurpose> = emptyList()

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
                val mapped = result.data.map { it.toMvpEvent() }
                OrganizerMvpPlaceholders.cachedEvents = mapped
                OrganizerMvpLoad(mapped, OrganizerMvpDataSource.BACKEND)
            }
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadEventForMvp(eventId: String): OrganizerMvpLoad<OrganizerMvpEvent?> {
        return when (val result = fetchOrganizerEvent(eventId)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.toMvpEvent(), OrganizerMvpDataSource.BACKEND)
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
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.map { it.toMvpAttendee() }, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadTransactionsForMvp(eventId: String, eventTitle: String): OrganizerMvpLoad<List<OrganizerMvpTransaction>> {
        return when (val result = fetchOrganizerTransactions(eventId)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.map { it.toMvpTransaction(eventTitle) }, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadScanPurposesForMvp(eventId: String): OrganizerMvpLoad<List<OrganizerMvpScanPurpose>> {
        return when (val result = fetchOrganizerScanPurposes(eventId)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.map { it.toMvpScanPurpose() }, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadReportForMvp(event: OrganizerMvpEvent): OrganizerMvpLoad<OrganizerMvpEvent> {
        return when (val report = fetchOrganizerReport(event.id)) {
            is NetworkResult.Success -> OrganizerMvpLoad(event.fromOrganizerReport(report.data), OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(event, OrganizerMvpDataSource.ERROR, report.message)
            NetworkResult.Loading -> OrganizerMvpLoad(event, OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadOverallReportForMvp(): OrganizerMvpLoad<OrganizerOverallReportDto?> {
        return when (val report = fetchOrganizerOverallReport()) {
            is NetworkResult.Success -> OrganizerMvpLoad(report.data, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(null, OrganizerMvpDataSource.ERROR, report.message)
            NetworkResult.Loading -> OrganizerMvpLoad(null, OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun loadStaffForMvp(event: OrganizerMvpEvent): OrganizerMvpLoad<List<OrganizerMvpStaff>> {
        return when (val result = fetchOrganizerStaff(event.id)) {
            is NetworkResult.Success -> {
                val mapped = result.data.map { it.toMvpStaff(event.title) }
                OrganizerMvpLoad(mapped, OrganizerMvpDataSource.BACKEND)
            }
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun searchStaffUsersForMvp(query: String): OrganizerMvpLoad<List<OrganizerMvpStaff>> {
        return when (val result = searchOrganizerUsers(query)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.map { it.toAvailableStaff() }, OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun addStaffForMvp(event: OrganizerMvpEvent, staff: OrganizerMvpStaff): OrganizerMvpLoad<OrganizerMvpStaff> {
        val permissionLower = staff.permissions.joinToString("|").lowercase()
        val request = StaffAssignmentRequestDto(
            staffUserId = staff.id.toUuidOrNull(),
            email = staff.email,
            name = staff.name,
            roleLabel = staff.roleLabel,
            canScan = permissionLower.contains("scan"),
            canPrintId = permissionLower.contains("print"),
            canViewLogs = permissionLower.contains("log"),
            canManageRewards = permissionLower.contains("reward"),
            permissions = staff.permissions,
        )
        return when (val result = addOrganizerStaff(event.id, request)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.toMvpStaff(event.title), OrganizerMvpDataSource.BACKEND)
            is NetworkResult.Error -> OrganizerMvpLoad(staff, OrganizerMvpDataSource.ERROR, result.message)
            NetworkResult.Loading -> OrganizerMvpLoad(staff, OrganizerMvpDataSource.ERROR, null)
        }
    }

    suspend fun updateStaffForMvp(event: OrganizerMvpEvent, staff: OrganizerMvpStaff): OrganizerMvpLoad<OrganizerMvpStaff> {
        val permissionLower = staff.permissions.joinToString("|").lowercase()
        val request = StaffAssignmentUpdateRequestDto(
            active = staff.accessStatus.equals("Active", ignoreCase = true),
            roleLabel = staff.roleLabel,
            canScan = permissionLower.contains("scan"),
            canPrintId = permissionLower.contains("print"),
            canViewLogs = permissionLower.contains("log"),
            canManageRewards = permissionLower.contains("reward"),
            permissions = staff.permissions,
        )
        return when (val result = updateOrganizerStaff(event.id, staff.id, request)) {
            is NetworkResult.Success -> OrganizerMvpLoad(result.data.toMvpStaff(event.title), OrganizerMvpDataSource.BACKEND)
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
                is NetworkResult.Success -> saved.add(result.data.toMvpScanPurpose())
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

    private suspend fun buildEventSnapshot(event: EventResponse): OrganizerMvpEvent {
        val registrations = (getRegistrationsByEvent(event.eventId.toString()) as? NetworkResult.Success)?.data.orEmpty()
        val transactions = (getTransactionsByEvent(event.eventId.toString()) as? NetworkResult.Success)?.data.orEmpty()
        val scanPurposes = (getScanPurposesByEvent(event.eventId.toString()) as? NetworkResult.Success)?.data.orEmpty()
        val redemptions = (getRewardRedemptions(event.eventId.toString()) as? NetworkResult.Success)?.data.orEmpty()
        val pointRules = (getPointRules(event.eventId.toString()) as? NetworkResult.Success)?.data.orEmpty()
        return OrganizerMvpEvent(
            id = event.eventId.toString(),
            title = event.title,
            organizerName = "Organizer",
            dateTime = listOf(DateFormatters.formatInstant(event.eventStartAt), DateFormatters.formatInstant(event.eventEndAt))
                .filter { it != "-" }
                .joinToString(" - ")
                .ifBlank { "-" },
            shortDate = DateFormatters.formatInstant(event.eventStartAt),
            venue = event.location ?: "Venue not set",
            status = event.status.toDisplayStatus(),
            submittedDate = DateFormatters.formatInstant(event.registrationOpenAt),
            adminRemarks = event.rejectionReason ?: if (event.status == EventStatus.APPROVED) "Approved." else "No admin remarks.",
            additionalOrganizers = emptyList(),
            registeredCount = registrations.size.takeIf { it > 0 } ?: event.currentAttendeeCount,
            enteredCount = registrations.count { it.status == RegistrationStatus.ENTERED },
            attendedCount = transactions.count { it.transactionResult == TransactionResult.APPROVED && it.transactionType == TransactionType.ATTENDANCE },
            exitedCount = registrations.count { it.status == RegistrationStatus.EXITED },
            noShowCount = registrations.count { it.status == RegistrationStatus.NO_SHOW },
            totalTransactions = transactions.size,
            successfulScans = transactions.count { it.transactionResult == TransactionResult.APPROVED },
            rejectedScans = transactions.count { it.transactionResult == TransactionResult.REJECTED },
            benefitClaims = transactions.count { it.transactionType == TransactionType.BENEFIT_CLAIM },
            boothSessionVisits = transactions.count { it.transactionType == TransactionType.BOOTH_VISIT || it.transactionType == TransactionType.SESSION_VISIT },
            rewardRedemptions = redemptions.size + transactions.count {
                it.transactionType == TransactionType.REWARD_REDEMPTION || it.transactionType == TransactionType.REWARD_REDEMPTION_SCAN
            },
            totalPointsAwarded = transactions.filter { it.transactionResult == TransactionResult.APPROVED }.sumOf { it.pointsDelta }.coerceAtLeast(0),
            idTemplateStatus = "Backend status unavailable",
            rewardsStatus = if (event.rewardsEnabled || pointRules.isNotEmpty()) "Enabled" else "Disabled",
            staffCount = getOrganizerStaff(event.eventId.toString()).size,
            scanPurposesCount = scanPurposes.count { it.active },
        )
    }

    suspend fun getEvents() = safeApiCall { apiService.getEvents() }
    suspend fun fetchOrganizerEvents() = safeApiCall { apiService.getOrganizerEvents() }
    suspend fun fetchOrganizerEvent(eventId: String) = safeApiCall { apiService.getOrganizerEvent(eventId) }
    suspend fun fetchOrganizerDashboardSummary() = safeApiCall { apiService.getOrganizerDashboardSummary() }
    suspend fun fetchOrganizerDashboard(eventId: String) = safeApiCall { apiService.getOrganizerDashboard(eventId) }
    suspend fun fetchOrganizerAttendees(eventId: String) = safeApiCall { apiService.getOrganizerAttendees(eventId) }
    suspend fun fetchOrganizerTransactions(eventId: String) = safeApiCall { apiService.getOrganizerTransactions(eventId) }
    suspend fun fetchOrganizerReport(eventId: String) = safeApiCall { apiService.getOrganizerReport(eventId) }
    suspend fun fetchOrganizerOverallReport() = safeApiCall { apiService.getOrganizerOverallReport() }
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

    suspend fun getTransactionsByEvent(eventId: String) = safeApiCall { apiService.getTransactionsByEvent(eventId) }

    suspend fun getTransactionRules(eventId: String) = safeApiCall { apiService.getOrganizerTransactionRules(eventId) }

    suspend fun saveTransactionRule(
        eventId: String,
        request: TransactionRuleRequest,
    ) = safeApiCall { apiService.saveOrganizerTransactionRule(eventId, request) }

    suspend fun createNotification(request: NotificationRequest) = safeApiCall { apiService.createNotification(request) }
    suspend fun getNotificationsByEvent(eventId: String) = safeApiCall { apiService.getNotificationsByEvent(eventId) }

    companion object {
        private const val KEY_SELECTED_EVENT_ID = "selected_event_id"
    }
}

private fun EventStatus.toDisplayStatus(): String = when (this) {
    EventStatus.APPROVED, EventStatus.ACTIVE -> "Approved"
    EventStatus.PENDING_REVIEW, EventStatus.DRAFT -> "Pending"
    EventStatus.REJECTED -> "Rejected"
    EventStatus.ENDED -> "Completed"
    EventStatus.CANCELLED -> "Rejected"
}

private fun OrganizerEventDto.toMvpEvent(): OrganizerMvpEvent = OrganizerMvpEvent(
    id = eventId.toString(),
    title = title,
    organizerName = organizerName ?: "Organizer",
    dateTime = dateTime ?: "-",
    shortDate = shortDate ?: "-",
    venue = venue ?: "Venue not set",
    status = status ?: "Pending",
    submittedDate = submittedDate ?: "-",
    adminRemarks = adminRemarks ?: "No admin remarks.",
    additionalOrganizers = additionalOrganizers,
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
    idTemplateStatus = idTemplateStatus ?: "Backend status unavailable",
    rewardsStatus = rewardsStatus ?: "Not configured",
    staffCount = staffCount,
    scanPurposesCount = scanPurposesCount,
    description = description.orEmpty(),
    registrationCloseDate = DateFormatters.formatInstant(registrationCloseAt),
    capacity = capacity,
    currentAttendeeCount = currentAttendeeCount,
    availableSlots = availableSlots,
)

private fun OrganizerAttendeeDto.toMvpAttendee(): OrganizerMvpAttendee = OrganizerMvpAttendee(
    id = attendeeId?.toString() ?: registrationId.toString(),
    eventId = eventId.toString(),
    name = name ?: "Unnamed attendee",
    email = email ?: "No email",
    phone = phone ?: "Not available",
    registrationStatus = registrationStatus ?: "Registered",
    currentEventStatus = currentEventStatus ?: "Registered",
    points = points,
    lastTransactionTime = lastTransactionTime ?: "-",
    registeredDate = registeredDate ?: "-",
    qrCredentialStatus = qrCredentialStatus ?: if (qrCredentialId != null) "Issued" else "Pending",
    recentTransactions = recentTransactions,
    recentRejectedScans = recentRejectedScans,
)

private fun OrganizerTransactionDto.toMvpTransaction(fallbackEventTitle: String): OrganizerMvpTransaction {
    val rejected = resultStatus == TransactionResult.REJECTED
    val displayType = transactionType.toDisplayType()
    return OrganizerMvpTransaction(
        id = transactionId.toString(),
        eventId = eventId.toString(),
        eventTitle = eventTitle ?: fallbackEventTitle,
        attendeeId = attendeeId?.toString() ?: registrationId?.toString() ?: "unknown",
        attendeeName = attendeeName ?: "Unknown attendee",
        attendeeEmail = attendeeEmail.orEmpty(),
        qrId = qrId ?: qrCredentialId?.toString().orEmpty(),
        staffId = staffId?.toString() ?: "Not available",
        staffName = staffName ?: "Staff not available",
        staffEmail = staffEmail.orEmpty(),
        scanPurpose = scanPurpose ?: displayType,
        type = displayType,
        timestamp = DateFormatters.formatInstant(createdTimestamp),
        status = if (rejected) "Rejected" else "Approved",
        message = message ?: if (rejected) "Scan rejected" else "$displayType recorded",
        reason = reason ?: if (rejected) "Rejected scan" else "Approved scan",
        deviceSource = deviceSource ?: "Not available",
        pointsDelta = pointsDelta,
        relatedItem = relatedItem ?: "Not available",
    )
}

private fun OrganizerReportDto.toMvpEvent(base: OrganizerMvpEvent): OrganizerMvpEvent = base.copy(
    registeredCount = totalRegistered,
    enteredCount = enteredCount,
    attendedCount = attendanceCount,
    exitedCount = exitedCount,
    noShowCount = noShowCount,
    totalTransactions = approvedTransactionCount + rejectedTransactionCount,
    successfulScans = approvedTransactionCount,
    rejectedScans = rejectedTransactionCount.takeIf { it > 0 } ?: rejectedScans,
    benefitClaims = benefitClaims,
    boothSessionVisits = boothSessionVisits,
    rewardRedemptions = rewardRedemptions,
    totalPointsAwarded = pointsDistributed,
)

private fun OrganizerMvpEvent.fromOrganizerReport(report: OrganizerReportDto): OrganizerMvpEvent = report.toMvpEvent(this)

private fun OrganizerStaffDto.toMvpStaff(eventTitle: String): OrganizerMvpStaff = OrganizerMvpStaff(
    id = assignmentId.toString(),
    name = name ?: "Unknown staff",
    email = email ?: "No email",
    assignedEventId = eventId.toString(),
    assignedEvent = eventTitle,
    roleLabel = roleLabel ?: "Scanner",
    accessStatus = if (active) "Active" else "Disabled",
    addedDate = DateFormatters.formatInstant(addedAt),
    permissions = permissions.ifEmpty {
        buildList {
            if (canScan) add("Scan QR")
            if (canPrintId) add("Print ID")
            if (canViewLogs) add("View Logs")
            if (canManageRewards) add("Manage Rewards")
        }
    },
)

private fun OrganizerUserSearchDto.toAvailableStaff(): OrganizerMvpStaff = OrganizerMvpStaff(
    id = userId.toString(),
    name = name ?: "Unnamed user",
    email = email ?: "No email",
    assignedEventId = "",
    assignedEvent = "Not assigned",
    roleLabel = if (role.equals("STAFF", ignoreCase = true)) "Scanner" else "Support Staff",
    accessStatus = status ?: "Available",
    addedDate = "Not added",
    permissions = listOf("Scan QR", "View attendee details"),
)

private fun OrganizerScanPurposeDto.toMvpScanPurpose(): OrganizerMvpScanPurpose = OrganizerMvpScanPurpose(
    label = title.ifBlank { code.toDisplayPurposeName() },
    description = description ?: title,
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

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

private fun RegistrationStatus.toDisplayStatus(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun RegistrationStatus.toEventStatusLabel(): String = when (this) {
    RegistrationStatus.REGISTERED -> "Registered"
    RegistrationStatus.ENTERED -> "Checked In / Entered"
    RegistrationStatus.EXITED -> "Exited"
    RegistrationStatus.CANCELLED -> "Cancelled"
    RegistrationStatus.NO_SHOW -> "No-show"
}

private fun TransactionType.toDisplayType(): String = when (this) {
    TransactionType.ENTRY -> "Entry"
    TransactionType.ATTENDANCE -> "Attendance"
    TransactionType.BENEFIT_CLAIM -> "Benefit Claim"
    TransactionType.BOOTH_VISIT -> "Booth/Session Visit"
    TransactionType.SESSION_VISIT -> "Booth/Session Visit"
    TransactionType.REWARD_REDEMPTION_SCAN, TransactionType.REWARD_REDEMPTION -> "Reward Redemption"
    TransactionType.EXIT -> "Exit"
    TransactionType.ID_PRINT -> "ID Printing"
    TransactionType.REGISTRATION -> "Registration"
}

private fun com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.toDisplayPurposeName(): String = when (this) {
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ENTRY -> "Entrance Logging"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ATTENDANCE -> "Attendance Recording"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BENEFIT_CLAIM -> "Benefit Claiming"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BOOTH_VISIT,
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.SESSION_VISIT -> "Booth/Session Visit"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION_SCAN,
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION -> "Reward Redemption"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.EXIT -> "Exit Logging"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ID_PRINT -> "ID Printing"
    com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REGISTRATION_LOOKUP -> "ID Reprinting"
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
    eventTitle: String,
    attendeeName: String?,
    purpose: ScanPurposeResponse?,
): OrganizerMvpTransaction {
    val rejected = transactionResult == TransactionResult.REJECTED
    val displayType = transactionType.toDisplayType()
    return OrganizerMvpTransaction(
        id = transactionId.toString(),
        eventId = eventId.toString(),
        eventTitle = eventTitle,
        attendeeId = attendeeUserId.toString(),
        attendeeName = attendeeName ?: "Attendee",
        attendeeEmail = "",
        qrId = qrCredentialId.toString(),
        staffId = "Not available",
        staffName = "Staff not available",
        staffEmail = "",
        scanPurpose = purpose?.name ?: displayType,
        type = displayType,
        timestamp = DateFormatters.formatInstant(scannedAt),
        status = if (rejected) "Rejected" else "Successful",
        message = reason ?: if (rejected) "Scan rejected" else "$displayType recorded",
        reason = reason ?: if (rejected) "Rejected scan" else "Approved scan",
        deviceSource = "Not available",
        pointsDelta = pointsDelta,
        relatedItem = purpose?.description ?: "Not available",
    )
}

private fun OrganizerMvpEvent.fromReport(report: EventReportSnapshot): OrganizerMvpEvent = copy(
    registeredCount = report.registeredCount.takeIf { it > 0 } ?: registeredCount,
    enteredCount = report.enteredCount,
    attendedCount = report.attendanceCount,
    exitedCount = report.exitedCount,
    noShowCount = report.noShowCount,
    totalTransactions = report.approvedTransactions + report.rejectedTransactions,
    successfulScans = report.approvedTransactions,
    rejectedScans = report.rejectedTransactions,
    benefitClaims = report.claimsCount,
    boothSessionVisits = report.boothSessionVisits,
    rewardRedemptions = report.rewardsRedeemed,
    totalPointsAwarded = report.totalPointsEarned,
)
