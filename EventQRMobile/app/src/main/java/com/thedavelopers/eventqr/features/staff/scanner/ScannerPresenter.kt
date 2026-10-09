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
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class ScannerPresenter(
    private var view: ScannerContract.View?,
    private val repository: StaffRepository,
    private val strings: UiStrings,
) {
    private val tag = "StaffQrScanner"
    private val scope = MainScope()
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    fun loadEvents() {
        view?.showLoading(true)
        job = scope.launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> {
                    val selectable = result.data
                        .filter { it.canScan && it.status != EventStatus.ENDED }
                        .map { EventSpinnerOption(it.eventId.toString(), it.title, it.canScan, it.eventStartAt) }
                    if (selectable.isEmpty() && result.data.any { it.status == EventStatus.ENDED }) {
                        view?.showMessage(strings.get(R.string.scanner_event_has_ended))
                    }
                    view?.showEvents(selectable)
                }
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun submitRewardRedemptionScan(eventId: String, purpose: ScanPurposeResponse, qrValue: String, staffUserId: String?) {
        if (!Validators.isNonEmpty(eventId)) {
            view?.showMessage(strings.get(R.string.scanner_select_an_assigned_event))
            return
        }
        if (!Validators.isNonEmpty(qrValue)) {
            view?.showMessage(strings.get(R.string.scanner_input_is_required))
            return
        }

        val trimmed = qrValue.trim()
        val isShortId = trimmed.startsWith("#") || trimmed.all { it.isDigit() }

        view?.showLoading(true)
        job = scope.launch {
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
            when (val result = repository.rewardRedemptionScan(request)) {
                is NetworkResult.Success -> view?.showRewardRedemptionScanResult(result.data)
                is NetworkResult.Error -> view?.showScanError(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun loadPurposes(eventId: String) {
        android.util.Log.d(tag, "Loading scan purposes for eventId=$eventId")
        view?.showLoading(true)
        job = scope.launch {
            when (val result = repository.getScanPurposesByEvent(eventId)) {
                is NetworkResult.Success -> {
                    android.util.Log.d(tag, "Loaded ${result.data.size} purposes for eventId=$eventId")
                    view?.showPurposes(result.data)
                }
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun submitScan(eventId: String, purpose: ScanPurposeResponse, qrValue: String, notes: String, staffUserId: String?) {
        if (!Validators.isNonEmpty(eventId)) {
            view?.showMessage(strings.get(R.string.scanner_select_an_assigned_event))
            return
        }
        if (!Validators.isNonEmpty(qrValue)) {
            view?.showMessage(strings.get(R.string.scanner_input_is_required))
            return
        }

        val trimmed = qrValue.trim()
        val isShortId = trimmed.startsWith("#") || trimmed.all { it.isDigit() }

        view?.showLoading(true)
        job = scope.launch {
            val eventUuid = parseUuid(eventId)
            val staffUuid = staffUserId?.takeIf { it.isNotBlank() }?.let(::parseUuid)
            if (eventUuid == null || (!staffUserId.isNullOrBlank() && staffUuid == null)) {
                view?.showLoading(false)
                view?.showScanError(strings.get(R.string.scanner_scan_setup_is_invalid))
                return@launch
            }
            val request = if (isShortId) {
                TransactionRequest(
                    eventId = eventUuid,
                    scanPurposeId = purpose.scanPurposeId,
                    shortId = trimmed,
                    staffUserId = staffUuid,
                    notes = notes.ifBlank { null },
                )
            } else {
                TransactionRequest(
                    eventId = eventUuid,
                    scanPurposeId = purpose.scanPurposeId,
                    qrValue = trimmed,
                    staffUserId = staffUuid,
                    notes = notes.ifBlank { null },
                )
            }
            when (val result = repository.verifyScan(request)) {
                is NetworkResult.Success -> {
                    android.util.Log.d(
                        tag,
                        "backend verification result=SUCCESS eventId=${result.data.eventId} scanPurposeId=${result.data.scanPurposeId}"
                    )
                    view?.showVerificationResult(result.data)
                }
                is NetworkResult.Error -> {
                    android.util.Log.w(tag, "backend verification result=ERROR eventId=$eventUuid scanPurposeId=${purpose.scanPurposeId}")
                    view?.showScanError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
