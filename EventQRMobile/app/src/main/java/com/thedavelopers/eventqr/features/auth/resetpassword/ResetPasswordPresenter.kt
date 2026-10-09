package com.thedavelopers.eventqr.features.auth.resetpassword

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.forgotpassword.ForgotPasswordOutcome
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ResetPasswordPresenter() {
    private var view: ResetPasswordContract.View? = null
    private val scope = MainScope()
    private var job: kotlinx.coroutines.Job? = null
    private var cooldownJob: kotlinx.coroutines.Job? = null
    private var repository: AuthRepository? = null
    private var appContext: Context? = null

    @VisibleForTesting
    internal var email: String? = null

    fun attach(view: ResetPasswordContract.View, context: Context) {
        this.view = view
        this.appContext = context.applicationContext
        this.repository = AuthRepository(context)
    }

    fun detach() {
        job?.cancel()
        cooldownJob?.cancel()
        scope.cancel()
        view = null
        repository = null
        appContext = null
    }

    /** Called once with the address the code was sent to; the code was just sent, so the cooldown starts now. */
    fun start(email: String?) {
        if (email.isNullOrBlank()) {
            view?.navigateToLogin()
            return
        }
        this.email = email
        view?.showEmail(email)
        startCooldown()
    }

    fun submitReset(code: String, newPassword: String, confirmPassword: String) {
        val currentEmail = email
        if (currentEmail.isNullOrBlank()) {
            view?.navigateToLogin()
            return
        }

        view?.showCodeError(null)
        view?.showPasswordError(null)
        view?.showConfirmPasswordError(null)

        val codeValue = code.trim()
        if (!CODE_REGEX.matches(codeValue)) {
            view?.showCodeError(string(R.string.reset_password_code_invalid))
            return
        }

        val requirements = Validators.passwordRequirements(newPassword)
        if (!requirements.isValid) {
            if (requirements.isOtherwiseValid) {
                view?.showPasswordError(Validators.PASSWORD_TOO_LONG_ERROR)
                return
            }
            view?.showPasswordError(string(R.string.password_policy_hint))
            return
        }

        if (newPassword != confirmPassword) {
            view?.showConfirmPasswordError(string(R.string.password_error_mismatch))
            return
        }

        view?.showLoading(true)
        job = scope.launch {
            when (val result = repository?.resetPassword(currentEmail, codeValue, newPassword, confirmPassword)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(string(R.string.reset_password_success))
                    view?.navigateToLogin()
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: string(R.string.reset_failed))
                }
                NetworkResult.Loading -> Unit
                null -> {
                    view?.showLoading(false)
                    view?.showMessage(string(R.string.reset_failed))
                }
            }
        }
    }

    fun resendCode() {
        val currentEmail = email
        if (currentEmail.isNullOrBlank() || cooldownJob?.isActive == true) return

        // Lock the button right away so a double tap can't fire two requests.
        view?.showResendCooldown(RESEND_COOLDOWN_SECONDS)
        job = scope.launch {
            when (val result = repository?.forgotPassword(currentEmail)) {
                is NetworkResult.Error -> when (ForgotPasswordOutcome.classify(result.throwable)) {
                    ForgotPasswordOutcome.NetworkFailure -> {
                        view?.showResendCooldown(0)
                        view?.showMessage(result.message)
                    }
                    ForgotPasswordOutcome.RateLimited -> {
                        view?.showMessage(string(R.string.forgot_password_rate_limited))
                        startCooldown()
                    }
                    ForgotPasswordOutcome.ServerError -> {
                        view?.showResendCooldown(0)
                        view?.showMessage(string(R.string.forgot_password_server_error))
                    }
                    // Neutral 4xx: behave as if sent so account existence isn't revealed.
                    ForgotPasswordOutcome.Neutral -> {
                        view?.showMessage(string(R.string.reset_password_code_resent))
                        startCooldown()
                    }
                }
                NetworkResult.Loading -> Unit
                else -> {
                    view?.showMessage(string(R.string.reset_password_code_resent))
                    startCooldown()
                }
            }
        }
    }

    fun navigateToLogin() {
        view?.navigateToLogin()
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            for (left in RESEND_COOLDOWN_SECONDS downTo 1) {
                view?.showResendCooldown(left)
                delay(1_000)
            }
            view?.showResendCooldown(0)
        }
    }

    private fun string(resId: Int): String = appContext?.getString(resId).orEmpty()

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 30
        val CODE_REGEX = Regex("^\\d{6}$")
    }
}
