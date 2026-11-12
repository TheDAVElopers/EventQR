package com.thedavelopers.eventqr.features.auth.register

import android.util.Log
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.logSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException

class RegistrationPresenter(
    private var view: RegistrationContract.View?,
    private val repository: AuthRepository,
    private val strings: UiStrings,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var registrationJob: Job? = null

    private companion object {
        /** The API rejects a fullName over 255 characters with HTTP 400. */
        const val MAX_FULL_NAME_LENGTH = 255
        const val TAG = "RegistrationPresenter"
    }

    fun attach(view: RegistrationContract.View) {
        this.view = view
    }

    fun detach() {
        registrationJob?.cancel()
        scope.cancel()
        view = null
    }

    fun submitRegistration(
        firstName: String,
        lastName: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String,
    ) {
        val firstNameValue = firstName.trim()
        val lastNameValue = lastName.trim()
        val emailValue = email.trim()
        val phoneValue = phoneNumber.trim()
        // Passwords are never trimmed: leading/trailing spaces are part of the secret.
        val passwordValue = password
        val confirmValue = confirmPassword
        // Same value the request sends: the trimmed names joined by one space.
        val fullNameValue = listOf(firstNameValue, lastNameValue).filter { it.isNotBlank() }.joinToString(" ")

        var valid = true
        if (!Validators.isNonEmpty(firstNameValue)) {
            view?.showFieldError("firstName", strings.get(R.string.create_admin_account_first_name_is_required))
            valid = false
        } else {
            view?.showFieldError("firstName", null)
        }
        if (!Validators.isNonEmpty(lastNameValue)) {
            view?.showFieldError("lastName", strings.get(R.string.create_admin_account_last_name_is_required))
            valid = false
        } else {
            view?.showFieldError("lastName", null)
        }
        // Combined length is what the API caps, so it can only be judged once both names exist.
        if (Validators.isNonEmpty(firstNameValue) && Validators.isNonEmpty(lastNameValue)) {
            val nameTooLong = fullNameValue.length > MAX_FULL_NAME_LENGTH
            view?.showFieldError("fullName", if (nameTooLong) strings.get(R.string.register_full_name_too_long) else null)
            if (nameTooLong) {
                valid = false
            }
        }
        if (!Validators.isValidEmail(emailValue)) {
            view?.showFieldError("email", strings.get(R.string.create_admin_account_enter_a_valid_email_address))
            valid = false
        } else {
            view?.showFieldError("email", null)
        }
        if (!Validators.isValidPhoneNumber(phoneValue)) {
            view?.showFieldError("phone", Validators.PHONE_ERROR)
            valid = false
        } else {
            view?.showFieldError("phone", null)
        }
        if (!Validators.isValidSignUpPassword(passwordValue)) {
            val tooLong = Validators.passwordRequirements(passwordValue).let { it.isOtherwiseValid && !it.withinMaxLength }
            view?.showFieldError("password", if (tooLong) Validators.PASSWORD_TOO_LONG_ERROR else strings.get(R.string.register_password_must_meet_all_requirements))
            valid = false
        } else {
            view?.showFieldError("password", null)
        }
        if (passwordValue != confirmValue) {
            view?.showFieldError("confirmPassword", strings.get(R.string.password_error_mismatch))
            valid = false
        } else {
            view?.showFieldError("confirmPassword", null)
        }

        if (!valid) {
            return
        }

        view?.showLoading(true)
        registrationJob = scope.launch {
            when (val result = repository.createUser(fullNameValue, emailValue, phoneValue, passwordValue)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: strings.get(R.string.register_account_created))
                    view?.navigateToSignIn()
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    // Server text stays in the log; the screen shows fixed copy (§7.5).
                    Log.w(TAG, "registration failed: ${result.logSummary()}")
                    when {
                        // 409: the email already has an account. The API names the field, so it is a field error.
                        statusCode(result) == 409 ->
                            view?.showServerFieldError("email", strings.get(R.string.ui_auth_register_email_taken))
                        statusCode(result) == 400 ->
                            view?.showFormError(strings.get(R.string.ui_auth_register_rejected))
                        statusCode(result) == 429 ->
                            view?.showFormError(strings.get(R.string.ui_auth_error_rate_limited))
                        result.throwable is java.io.IOException ->
                            view?.showFormError(strings.get(R.string.ui_auth_error_network))
                        else -> view?.showFormError(strings.get(R.string.ui_auth_register_failed))
                    }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun statusCode(result: NetworkResult.Error): Int? = (result.throwable as? HttpException)?.code()
}