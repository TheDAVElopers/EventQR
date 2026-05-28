package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class RegistrationPresenter(
    private var view: RegistrationContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun submit(eventId: String, fullName: String, email: String, phoneNumber: String) {
        val normalizedFullName = fullName.trim()
        val normalizedEmail = email.trim()
        val normalizedPhone = phoneNumber.trim()

        if (!Validators.isNonEmpty(normalizedFullName)) {
            view?.showFieldError("fullName", "Full name is required")
            return
        }
        if (!Validators.isValidEmail(normalizedEmail)) {
            view?.showFieldError("email", "Enter a valid email address")
            return
        }
        if (!Validators.isValidPhoneNumber(normalizedPhone)) {
            view?.showFieldError("phone", "Phone number must start with 63 and be 12 digits long")
            return
        }
        view?.showFieldError("email", null)
        view?.showFieldError("fullName", null)
        view?.showFieldError("phone", null)
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            val regResult = repository.createRegistration(
                RegistrationRequest(
                    eventId = UUID.fromString(eventId),
                    email = normalizedEmail,
                    fullName = normalizedFullName,
                    phoneNumber = normalizedPhone.ifBlank { null },
                )
            )

            when (regResult) {
                is NetworkResult.Success -> {
                    val submission = regResult.data
                    val registrationId = submission.registration.registrationId.toString()
                    val qrCredentialId = submission.qrCredential.qrCredentialId.toString()

                    view?.showLoading(false)
                    view?.showMessage("Registration successful")
                    view?.openQr(registrationId, qrCredentialId)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(toFriendlyRegistrationError(regResult.message))
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}
