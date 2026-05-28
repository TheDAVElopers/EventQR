package com.thedavelopers.eventqr.features.auth.register

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RegistrationPresenter(
    private var view: RegistrationContract.View?,
    private val repository: AuthRepository,
) {
    private var registrationJob: Job? = null

    fun attach(view: RegistrationContract.View) {
        this.view = view
    }

    fun detach() {
        registrationJob?.cancel()
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
        val passwordValue = password.trim()
        val confirmValue = confirmPassword.trim()

        var valid = true
        if (!Validators.isNonEmpty(firstNameValue)) {
            view?.showFieldError("firstName", "First name is required")
            valid = false
        } else {
            view?.showFieldError("firstName", null)
        }
        if (!Validators.isNonEmpty(lastNameValue)) {
            view?.showFieldError("lastName", "Last name is required")
            valid = false
        } else {
            view?.showFieldError("lastName", null)
        }
        if (!Validators.isValidEmail(emailValue)) {
            view?.showFieldError("email", "Enter a valid email address")
            valid = false
        } else {
            view?.showFieldError("email", null)
        }
        if (!Validators.isValidPhoneNumber(phoneValue)) {
            view?.showFieldError("phone", "Phone number must start with 63 and be 12 digits long")
            valid = false
        } else {
            view?.showFieldError("phone", null)
        }
        if (!Validators.isValidSignUpPassword(passwordValue)) {
            view?.showFieldError("password", "Password must meet all requirements")
            valid = false
        } else {
            view?.showFieldError("password", null)
        }
        if (passwordValue != confirmValue) {
            view?.showFieldError("confirmPassword", "Passwords do not match")
            valid = false
        } else {
            view?.showFieldError("confirmPassword", null)
        }

        if (!valid) {
            return
        }

        view?.showLoading(true)
        registrationJob = kotlinx.coroutines.MainScope().launch {
            val fullNameValue = listOf(firstNameValue, lastNameValue).filter { it.isNotBlank() }.joinToString(" ").trim()
            when (val result = repository.createUser(fullNameValue, emailValue, phoneValue, passwordValue)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: "Account created")
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