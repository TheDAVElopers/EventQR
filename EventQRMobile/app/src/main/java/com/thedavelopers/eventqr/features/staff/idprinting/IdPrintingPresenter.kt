package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class IdPrintingPresenter(
    private var view: IdPrintingContract.View?,
    private val repository: StaffRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun print(eventId: String, qrCredentialId: String, staffUserId: String, reprint: Boolean) {
        if (!Validators.isNonEmpty(eventId) || !Validators.isNonEmpty(qrCredentialId) || !Validators.isNonEmpty(staffUserId)) {
            view?.showMessage("Event ID, QR credential ID, and staff ID are required")
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.printId(
                IdPrintRequest(
                    eventId = UUID.fromString(eventId),
                    qrCredentialId = UUID.fromString(qrCredentialId),
                    staffUserId = UUID.fromString(staffUserId),
                    reprint = reprint,
                )
            )) {
                is NetworkResult.Success -> view?.showPrintResult(result.data.message)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    fun loadLogs(eventId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getIdPrintsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderLogs(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}
