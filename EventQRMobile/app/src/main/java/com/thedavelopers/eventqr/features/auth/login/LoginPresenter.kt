package com.thedavelopers.eventqr.features.auth.login

import android.util.Log
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.logSummary
import retrofit2.HttpException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginPresenter(
    private var view: LoginContract.View?,
    private val repository: AuthRepository,
    private val strings: UiStrings,
    /** Shown for every 403; the server's own text goes to the log (string resource login_account_disabled). */
    private val disabledAccountMessage: String,
    /** Shown for a 429 (string resource login_rate_limited). */
    private val rateLimitedMessage: String,
    /** Shown for every 400; the server's own text goes to the log (string resource login_invalid_request). */
    private val invalidRequestMessage: String = "Check your email and password, then try again.",
    /** Shown for a 401 when the typed password starts or ends with a space (string login_check_spaces). */
    private val surroundingSpacesHintMessage: String = "Incorrect email or password. Check for extra spaces at the start or end of your password.",
    /** Builds the 429 message from a whole-minute wait (string plural login_rate_limited_minutes). */
    private val rateLimitedMinutesMessage: (Int) -> String = { rateLimitedMessage },
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var loginJob: Job? = null

    fun attach(view: LoginContract.View) {
        this.view = view
    }

    fun detach() {
        loginJob?.cancel()
        scope.cancel()
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
        loginJob = scope.launch {
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

                    if (resolvedRole == null) {
                        // The sign-in worked but the role lookup did not; repeating the sign-in can fix that.
                        view?.showFormError(strings.get(R.string.ui_auth_login_role_unknown), retryable = true)
                        return@launch
                    }

                    view?.showMessage(result.message ?: loginResponse.message ?: strings.get(R.string.login_login_successful))
                    view?.navigateToDashboard(resolvedRole.name)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    Log.w(TAG, "login failed: ${result.logSummary()}")
                    val failure = failure(result, passwordValue)
                    view?.showFormError(failure.message, failure.retryable)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    /** One form-level failure: the sentence for the strip and whether `Retry` may re-run the sign-in. */
    private class Failure(val message: String, val retryable: Boolean)

    /**
     * Server text never reaches the screen (it is logged above), and nothing here says whether the email
     * has an account: a wrong email and a wrong password read the same. Only a transient failure (no
     * connection, a 5xx) offers `Retry`; any 4xx would repeat the same rejected attempt (a 400, 401,
     * 403 or 429 against the rate limit), so the user fixes the input or waits instead.
     */
    private fun failure(error: NetworkResult.Error, password: String): Failure =
        when (statusCode(error)) {
            400 -> Failure(invalidRequestMessage, retryable = false)
            // Passwords are sent exactly as typed (one request, one rate-limit attempt). A stray space that the
            // keyboard appended is the likeliest reason a correct password fails, so point at it.
            401 -> Failure(
                if (password != password.trim()) surroundingSpacesHintMessage
                else strings.get(R.string.ui_auth_login_bad_credentials),
                retryable = false,
            )
            403 -> Failure(disabledAccountMessage, retryable = false)
            429 -> Failure(
                error.retryAfterSeconds?.takeIf { it > 0 }
                    ?.let { rateLimitedMinutesMessage(retryAfterMinutes(it)) }
                    ?: rateLimitedMessage,
                retryable = false,
            )
            // Transient only: no HTTP answer at all (offline, timeout) or a 5xx. Any other 4xx (404, 405,
            // 415, 422) is a rejection that repeating cannot fix.
            else -> {
                val code = statusCode(error)
                Failure(
                    strings.get(if (error.throwable is java.io.IOException) R.string.ui_auth_error_network else R.string.ui_auth_login_failed),
                    retryable = code == null || code >= 500,
                )
            }
        }

    private fun retryAfterMinutes(seconds: Long): Int =
        ((seconds + 59) / 60).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    private fun statusCode(result: NetworkResult<*>): Int? =
        ((result as? NetworkResult.Error)?.throwable as? HttpException)?.code()

    private companion object {
        const val TAG = "LoginPresenter"
    }

    fun openRegistration() {
        view?.navigateToRegistration()
    }

    fun openForgotPassword() {
        view?.navigateToForgotPassword()
    }
}