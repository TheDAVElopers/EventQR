package com.thedavelopers.eventqr.features.auth.register

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RegistrationPresenter(
    private var view: RegistrationContract.View?,
    private val repository: AuthRepository,
    private val strings: UiStrings,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var registrationJob: Job? = null

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
            val fullNameValue = listOf(firstNameValue, lastNameValue).filter { it.isNotBlank() }.joinToString(" ").trim()
            when (val result = repository.createUser(fullNameValue, emailValue, phoneValue, passwordValue)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: strings.get(R.string.register_account_created))
                    view?.navigateToSignIn()
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}