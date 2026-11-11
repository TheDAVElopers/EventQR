package com.thedavelopers.eventqr.features.staff.scanner

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanRequest
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.UUID

class ScannerPresenter(
    private var view: ScannerContract.View?,
    private val repository: StaffRepository,
    private val strings: UiStrings,
    private val scope: CoroutineScope = MainScope(),
) {
    private val tag = "StaffQrScanner"
    private var job: Job? = null

    // A submit belongs to the event it was started for. It gets its own job so an event switch can
    // abandon it without touching the events load, and so its late callback can be dropped.
    private var scanJob: Job? = null

    // Purpose loads are re-derived on every event change, so they get their own job: cancelling a
    // stale purpose load must never cancel a scan that is being saved.
    private var purposesJob: Job? = null
    private var purposesEventId: String? = null

    fun detach() {
        job?.cancel()
        scanJob?.cancel()
        purposesJob?.cancel()
        scope.cancel()
        view = null
    }

    /**
     * The event selection changed: the submit in flight, and its outcome, belong to the event now
     * left behind. A late [ScannerContract.View.showRecordedResult] would otherwise paint event A's
     * scan under event B, which the scanner's own flags cannot detect because they were reset.
     */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun loadEvents() {
        view?.showLoading(true)
        job = scope.launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> {
                    val selectable = result.data
                        .filter { it.canStartScan }
                        .map { EventSpinnerOption(it.eventId.toString(), it.title, it.canScan, it.eventStartAt, it.canPrintId) }
                    if (selectable.isEmpty() && result.data.any { it.status == EventStatus.ENDED }) {
                        view?.showMessage(strings.get(R.string.scanner_event_has_ended))
                    }
                    view?.showEvents(selectable)
                }
                is NetworkResult.Error -> view?.showEventsLoadFailed(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun submitRewardRedemptionScan(eventId: String, purpose: ScanPurposeResponse, qrValue: String, staffUserId: String?) {
        if (!Validators.isNonEmpty(eventId)) {
            // Every early return ends in a state callback, never a bare toast: the caller holds a
            // submit-in-flight flag that only a terminal callback clears.
            view?.showScanError(strings.get(R.string.scanner_select_an_assigned_event))
            return
        }
        if (!Validators.isNonEmpty(qrValue)) {
            view?.showScanError(strings.get(R.string.scanner_input_is_required))
            return
        }

        val trimmed = qrValue.trim()
        val isShortId = isShortId(trimmed)

        view?.showLoading(true)
        scanJob = scope.launch {
            val eventUuid = parseUuid(eventId)
            val staffUuid = staffUserId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
            if (eventUuid == null || (!staffUserId.isNullOrBlank() && staffUuid == null)) {
                view?.showLoading(false)
                view?.showScanError(strings.get(R.string.scanner_scan_setup_is_invalid))
                return@launch
            }
            val request = RewardRedemptionScanRequest(
                eventId = eventUuid,
                scanPurposeId = purpose.scanPurposeId,
                qrValue = if (isShortId) null else trimmed,
                shortId = if (isShortId) trimmed else null,
                staffUserId = staffUuid,
            )
            val result = repository.rewardRedemptionScan(request)
            // An event switch cancelled this submit while its response was already on its way:
            // nothing of it may reach the screen under the event now selected.
            ensureActive()
            when (result) {
                is NetworkResult.Success -> view?.showRewardRedemptionScanResult(result.data)
                is NetworkResult.Error -> view?.showScanError(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    /**
     * Records one scan through the endpoint for its purpose. The backend's registration row lock is
     * the only decision that counts - a REJECTED row comes back as a successful response - so the
     * client never re-derives approval from verification. [clientRequestId] is minted once per
     * decoded pass and reused on retry: the server returns the original row instead of logging the
     * attendee twice when a timed-out request did land.
     */
    fun recordScan(
        eventId: String,
        purpose: ScanPurposeResponse,
        qrValue: String,
        staffUserId: String?,
        clientRequestId: UUID,
    ) {
        if (!Validators.isNonEmpty(eventId)) {
            view?.showScanError(strings.get(R.string.scanner_select_an_assigned_event))
            return
        }
        if (!Validators.isNonEmpty(qrValue)) {
            view?.showScanError(strings.get(R.string.scanner_input_is_required))
            return
        }

        view?.showLoading(true)
        scanJob = scope.launch {
            val eventUuid = parseUuid(eventId)
            val staffUuid = staffUserId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
            if (eventUuid == null || (!staffUserId.isNullOrBlank() && staffUuid == null)) {
                view?.showScanError(strings.get(R.string.scanner_scan_setup_is_invalid))
                view?.showLoading(false)
                return@launch
            }
            val recordValue = resolveRecordValue(eventUuid, purpose, qrValue.trim(), staffUuid)
            if (recordValue == null) {
                view?.showLoading(false)
                return@launch
            }
            val request = TransactionRequest(
                eventId = eventUuid,
                scanPurposeId = purpose.scanPurposeId,
                qrValue = recordValue,
                staffUserId = staffUuid,
                clientRequestId = clientRequestId,
            )
            val result = repository.createTransaction(request, purpose.code)
            // The event selection may have changed while this save was in flight (see cancelScan).
            ensureActive()
            when (result) {
                is NetworkResult.Success -> view?.showRecordedResult(result.data)
                is NetworkResult.Error -> view?.showRecordFailed(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    /**
     * ID printing is not a transaction: it needs the attendee identity and the pass value for the
     * card before the system print dialog opens. Verification is the only caller of this path.
     */
    fun verifyScan(eventId: String, purpose: ScanPurposeResponse, qrValue: String, staffUserId: String?) {
        if (!Validators.isNonEmpty(eventId)) {
            view?.showScanError(strings.get(R.string.scanner_select_an_assigned_event))
            return
        }
        if (!Validators.isNonEmpty(qrValue)) {
            view?.showScanError(strings.get(R.string.scanner_input_is_required))
            return
        }

        val trimmed = qrValue.trim()
        val isShortId = isShortId(trimmed)

        view?.showLoading(true)
        scanJob = scope.launch {
            val eventUuid = parseUuid(eventId)
            val staffUuid = staffUserId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
            if (eventUuid == null || (!staffUserId.isNullOrBlank() && staffUuid == null)) {
                view?.showScanError(strings.get(R.string.scanner_scan_setup_is_invalid))
                view?.showLoading(false)
                return@launch
            }
            val request = if (isShortId) {
                TransactionRequest(
                    eventId = eventUuid,
                    scanPurposeId = purpose.scanPurposeId,
                    shortId = trimmed,
                    staffUserId = staffUuid,
                )
            } else {
                TransactionRequest(
                    eventId = eventUuid,
                    scanPurposeId = purpose.scanPurposeId,
                    qrValue = trimmed,
                    staffUserId = staffUuid,
                )
            }
            val result = repository.verifyScan(request)
            ensureActive()
            when (result) {
                is NetworkResult.Success -> view?.showVerifiedResult(result.data)
                is NetworkResult.Error -> view?.showScanError(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    /**
     * The purpose list belongs to one event. A fast event switch can leave two requests in flight,
     * so the previous load is cancelled and any response tagged with an older event id is dropped:
     * otherwise the slower first response would overwrite the purposes of the event now selected.
     */
    fun loadPurposes(eventId: String) {
        android.util.Log.d(tag, "Loading scan purposes for eventId=$eventId")
        purposesJob?.cancel()
        purposesEventId = eventId
        view?.showLoading(true)
        purposesJob = scope.launch {
            val result = repository.getScanPurposesByEvent(eventId)
            if (eventId != purposesEventId) return@launch
            when (result) {
                is NetworkResult.Success -> {
                    android.util.Log.d(tag, "Loaded ${result.data.size} purposes for eventId=$eventId")
                    view?.showPurposes(result.data)
                }
                is NetworkResult.Error -> view?.showPurposesUnavailable(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    /**
     * `recordNew` looks a pass up by `qrValue` only, so a typed attendee number is resolved through
     * verification first; verification is a read and has no say in the result. A failed lookup keeps
     * the scanner live with the inline strip - there is nothing recorded to retry.
     */
    private suspend fun resolveRecordValue(
        eventId: UUID,
        purpose: ScanPurposeResponse,
        value: String,
        staffUserId: UUID?,
    ): String? {
        if (!isShortId(value)) return value
        return when (val result = repository.verifyScan(
            TransactionRequest(
                eventId = eventId,
                scanPurposeId = purpose.scanPurposeId,
                shortId = value,
                staffUserId = staffUserId,
            ),
        )) {
            is NetworkResult.Success -> result.data.qrValue.takeIf { it.isNotBlank() }
                ?: run {
                    view?.showScanError(strings.get(R.string.ui_staff_scan_no_matching_pass))
                    null
                }
            is NetworkResult.Error -> {
                // Only a genuine lookup/validation rejection means "no pass matches"; a 5xx or a
                // dropped connection keeps the server's own reason instead of inventing a missing pass.
                view?.showScanError(
                    if (isLookupFailure(result)) {
                        strings.get(R.string.ui_staff_scan_no_matching_pass)
                    } else {
                        result.message
                    },
                )
                null
            }
            NetworkResult.Loading -> {
                // A lookup that never settles leaves the scan with no outcome; the scanner holds a
                // submit-in-flight flag that only a terminal callback clears, so silence would
                // leave it dead until the screen was recreated.
                view?.showScanError(strings.get(R.string.ui_staff_scan_lookup_unavailable))
                null
            }
        }
    }

    /** 400 / 404 / 422 reject the short ID itself: the pass behind it does not exist. */
    private fun isLookupFailure(error: NetworkResult.Error): Boolean {
        val code = (error.throwable as? retrofit2.HttpException)?.code() ?: return false
        return code == 400 || code == 404 || code == 422
    }

    private fun isShortId(value: String): Boolean = value.startsWith("#") || value.all { it.isDigit() }

    private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
