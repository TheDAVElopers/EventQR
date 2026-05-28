package com.thedavelopers.eventqr.features.staff.scanner

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class ScannerPresenter(
    private var view: ScannerContract.View?,
    private val repository: StaffRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun loadEvents() {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> view?.showEvents(result.data.map { EventSpinnerOption(it.eventId.toString(), it.title, it.canScan) })
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun loadPurposes(eventId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getScanPurposesByEvent(eventId)) {
                is NetworkResult.Success -> view?.showPurposes(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun submitScan(eventId: String, purpose: ScanPurposeResponse, qrValue: String, notes: String, staffUserId: String?) {
        if (!Validators.isNonEmpty(eventId)) {
            view?.showMessage("Select an assigned event")
            return
        }
        if (!Validators.isNonEmpty(qrValue)) {
            view?.showMessage("QR value is required")
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            val request = TransactionRequest(
                eventId = UUID.fromString(eventId),
                scanPurposeId = purpose.scanPurposeId,
                qrValue = qrValue.trim(),
                staffUserId = staffUserId?.takeIf { it.isNotBlank() }?.let(UUID::fromString),
                notes = notes.ifBlank { null },
            )
            when (val result = repository.verifyScan(request)) {
                is NetworkResult.Success -> view?.showVerificationResult(result.data)
                is NetworkResult.Error -> view?.showScanError(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}