package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.qrcredential.model.dto.QrCredentialSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class QrCredentialPresenter(
    private var view: QrCredentialContract.View?,
    private val repository: AttendeeRepository,
    private val strings: UiStrings,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    fun load(registrationId: String, qrCredentialId: String? = null) {
        view?.showLoading(true)
        job = scope.launch {
            val qrResult = when {
                !qrCredentialId.isNullOrBlank() -> repository.getMyQrCredentialById(qrCredentialId)
                registrationId.isNotBlank() -> repository.getMyQrCredentialByRegistration(registrationId)
                else -> NetworkResult.Error(strings.get(R.string.qr_credential_missing_registration_information))
            }

            if (qrResult is NetworkResult.Success) {
                val qrCredential = qrResult.data
                val regResult = repository.getRegistration(qrCredential.registrationId.toString())
                var eventTitle: String? = null
                if (regResult is NetworkResult.Success) {
                    val eventResult = repository.getEvent(regResult.data.eventId.toString())
                    if (eventResult is NetworkResult.Success) {
                        eventTitle = eventResult.data.title
                    }
                }

                view?.showLoading(false)
                view?.renderQr(
                    qrCredential,
                    (regResult as? NetworkResult.Success)?.data,
                    eventTitle
                )
                qrCredential.qrCredentialId.toString().also { repository.markMyQrDisplayed(it) }
            } else if (qrResult is NetworkResult.Error) {
                view?.showLoading(false)
                view?.showMessage(toFriendlyQrError(qrResult.message))
            }
        }
    }

    fun markDownloaded(qrCredentialId: String) {
        job = scope.launch { repository.markMyQrDownloaded(qrCredentialId) }
    }
}
