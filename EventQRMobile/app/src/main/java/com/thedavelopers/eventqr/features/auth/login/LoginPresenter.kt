package com.thedavelopers.eventqr.features.auth.login

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import retrofit2.HttpException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LoginPresenter(
    private var view: LoginContract.View?,
    private val repository: AuthRepository,
    private val strings: UiStrings,
    /** Shown for a 403 whose body carries no usable message (string resource login_account_disabled). */
    private val disabledAccountMessage: String,
    /** Shown for a 429 (string resource login_rate_limited). */
    private val rateLimitedMessage: String,
    /** Shown for a 400 whose body carries no usable message (string resource login_invalid_request). */
    private val invalidRequestMessage: String = "Please check your email and password and try again.",
    /** Shown for a 401 when the typed password starts or ends with a space (string login_check_spaces). */
    private val surroundingSpacesHintMessage: String = "Incorrect email or password. Check for extra spaces at the start or end of your password.",
    /** Builds the 429 message from a whole-minute wait (string plural login_rate_limited_minutes). */
    private val rateLimitedMinutesMessage: (Int) -> String = { rateLimitedMessage },
) {
    private var loginJob: Job? = null

    fun attach(view: LoginContract.View) {
        this.view = view
    }

    fun detach() {
        loginJob?.cancel()
        view = null
    }

    fun submitLogin(email: String, password: String) {
        val emailValue = email.trim()
        // Passwords are never trimmed; spaces can be part of the secret.
        val passwordValue = password

        var valid = true
        if (!Validators.isValidEmail(emailValue)) {
            view?.showEmailError(strings.get(R.string.create_admin_account_enter_a_valid_email_address))
            valid = false
        } else {
            view?.showEmailError(null)
        }

        // Only a not-blank check on-device: the length rule applies to new passwords, and an
        // existing account may predate it. The server decides whether the credentials are right.
        if (passwordValue.isBlank()) {
            view?.showPasswordError(strings.get(R.string.login_enter_your_password))
            valid = false
        } else {
            view?.showPasswordError(null)
        }

        if (!valid) {
            return
        }

        view?.showLoading(true)
        loginJob = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.login(emailValue, passwordValue)) {
                is NetworkResult.Success -> {
                    val loginResponse = result.data
                    repository.storeSession(loginResponse)
                    view?.onLoginSucceeded(emailValue)
                    var resolvedRole = loginResponse.role

                    if (resolvedRole == null) {
                        when (val meResult = repository.getAuthMe()) {
                            is NetworkResult.Success -> {
                                resolvedRole = meResult.data.role
                                repository.saveUserRole(resolvedRole)
                            }
                            else -> Unit
                        }
                    }

                    repository.saveUserRole(resolvedRole)
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: loginResponse.message ?: strings.get(R.string.login_login_successful))

                    if (resolvedRole == null) {
                        view?.showMessage(strings.get(R.string.login_unable_to_determine_account_role))
                    }

                    view?.navigateToDashboard(resolvedRole?.name)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(errorMessage(result, passwordValue))
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    /**
     * A disabled account answers 403 with the backend ErrorResponse message (parsed once by safeApiCall
     * into [NetworkResult.Error.serverMessage]); fall back to the string resource when it is missing or
     * blank. 429 shows the rate-limit message.
     */
    private fun errorMessage(error: NetworkResult.Error, password: String): String =
        when (statusCode(error)) {
            400 -> invalidRequestMessage
            // Passwords are sent exactly as typed (one request, one rate-limit attempt). A stray space that the
            // keyboard appended is the likeliest reason a correct password fails, so point at it.
            401 -> if (password != password.trim()) surroundingSpacesHintMessage else error.message
            403 -> error.serverMessage?.takeIf { it.isNotBlank() } ?: disabledAccountMessage
            429 -> error.retryAfterSeconds?.takeIf { it > 0 }
                ?.let { rateLimitedMinutesMessage(retryAfterMinutes(it)) }
                ?: rateLimitedMessage
            else -> error.message
        }

    private fun retryAfterMinutes(seconds: Long): Int =
        ((seconds + 59) / 60).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    private fun statusCode(result: NetworkResult<*>): Int? =
        ((result as? NetworkResult.Error)?.throwable as? HttpException)?.code()

    fun openRegistration() {
        view?.navigateToRegistration()
    }

    fun openForgotPassword() {
        view?.navigateToForgotPassword()
    }
}