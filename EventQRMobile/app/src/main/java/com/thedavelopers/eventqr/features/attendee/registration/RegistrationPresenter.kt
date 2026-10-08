package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class RegistrationPresenter(
    private var view: RegistrationContract.View?,
    private val repository: AttendeeRepository,
    private val strings: UiStrings,
    /** The logged-in user's own email (session/profile). The backend rejects any other email with 403. */
    private val ownEmailProvider: () -> String? = { null },
    /** Shown for a 403 "own account email" rejection (string resource registration_own_email_required). */
    private val ownEmailMessage: String = strings.get(R.string.registration_own_email_required),
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun submit(eventId: String, fullName: String, email: String, phoneNumber: String) {
        val normalizedFullName = fullName.trim()
        val normalizedEmail = resolveRegistrationEmail(ownEmailProvider(), email)
        val normalizedPhone = phoneNumber.trim()

        if (!Validators.isNonEmpty(normalizedFullName)) {
            view?.showFieldError("fullName", strings.get(R.string.attendee_registration_full_name_is_required))
            return
        }
        if (!Validators.isValidEmail(normalizedEmail)) {
            view?.showFieldError("email", strings.get(R.string.create_admin_account_enter_a_valid_email_address))
            return
        }
        if (!Validators.isValidPhoneNumber(normalizedPhone)) {
            view?.showFieldError("phone", Validators.PHONE_ERROR)
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
                    RegistrationsCache.addRegistration(submission.registration)
                    val registrationId = submission.registration.registrationId.toString()
                    val qrCredentialId = submission.qrCredential.qrCredentialId.toString()

                    view?.showLoading(false)
                    view?.showMessage(strings.get(R.string.attendee_registration_registration_successful))
                    view?.openQr(registrationId, qrCredentialId)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(toFriendlyRegistrationError(regResult.serverMessage ?: regResult.message, strings, ownEmailMessage))
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}

/** The registration email is always the logged-in user's own; the form field is only a fallback when none is known. */
internal fun resolveRegistrationEmail(ownEmail: String?, fieldEmail: String): String =
    ownEmail?.trim()?.takeIf { it.isNotEmpty() } ?: fieldEmail.trim()
