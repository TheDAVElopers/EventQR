package com.thedavelopers.eventqr.features.auth.resetpassword

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.forgotpassword.ForgotPasswordOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Code step: verifies the emailed code with the backend before the new-password screen opens. */
class ResetPasswordPresenter() {
    private var view: ResetPasswordContract.View? = null
    private val scope = MainScope()
    private var verifyJob: Job? = null
    private var resendJob: Job? = null
    private var cooldownJob: Job? = null
    private var appContext: Context? = null

    @VisibleForTesting
    internal var repository: AuthRepository? = null

    @VisibleForTesting
    internal var email: String? = null

    /** Resends made during this screen session; drives the escalating cooldown and the cap. */
    @VisibleForTesting
    internal var resendsDone: Int = 0

    fun attach(view: ResetPasswordContract.View, context: Context) {
        this.view = view
        this.appContext = context.applicationContext
        this.repository = AuthRepository(context)
    }

    fun detach() {
        verifyJob?.cancel()
        resendJob?.cancel()
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
        startCooldown(ResetResendPolicy.cooldownSeconds(resendsDone))
    }

    fun submitCode(code: String) {
        val currentEmail = email
        if (currentEmail.isNullOrBlank()) {
            view?.navigateToLogin()
            return
        }
        if (verifyJob?.isActive == true) return

        view?.showCodeError(null)

        val codeValue = code.trim()
        if (!CODE_REGEX.matches(codeValue)) {
            view?.showCodeError(string(R.string.reset_password_code_invalid))
            return
        }

        view?.showLoading(true)
        verifyJob = scope.launch {
            when (val result = repository?.verifyResetCode(currentEmail, codeValue)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.navigateToNewPassword(currentEmail, codeValue)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    when (ForgotPasswordOutcome.classify(result.throwable)) {
                        ForgotPasswordOutcome.NetworkFailure -> view?.showMessage(result.message)
                        ForgotPasswordOutcome.RateLimited -> view?.showMessage(string(R.string.forgot_password_rate_limited))
                        ForgotPasswordOutcome.ServerError -> view?.showMessage(string(R.string.reset_failed))
                        // 400: wrong, expired, or locked after too many attempts.
                        ForgotPasswordOutcome.Neutral -> view?.showCodeError(string(R.string.reset_password_code_incorrect))
                    }
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
        if (currentEmail.isNullOrBlank() || cooldownJob?.isActive == true || resendJob?.isActive == true) return
        if (!ResetResendPolicy.canResend(resendsDone)) {
            view?.showResendUnavailable()
            return
        }

        // Lock the button right away so a double tap can't fire two requests.
        view?.showResendCooldown(ResetResendPolicy.cooldownSeconds(resendsDone))
        resendJob = scope.launch {
            when (val result = repository?.forgotPassword(currentEmail)) {
                is NetworkResult.Error -> when (ForgotPasswordOutcome.classify(result.throwable)) {
                    ForgotPasswordOutcome.NetworkFailure -> {
                        view?.showResendCooldown(0)
                        view?.showMessage(result.message)
                    }
                    ForgotPasswordOutcome.RateLimited -> {
                        view?.showMessage(string(R.string.forgot_password_rate_limited))
                        // Not a successful resend: don't count it, just back off (honouring Retry-After).
                        val retryAfter = result.retryAfterSeconds?.coerceAtMost(ResetResendPolicy.MAX_COOLDOWN_SECONDS.toLong())?.toInt() ?: 0
                        startCooldown(maxOf(ResetResendPolicy.cooldownSeconds(resendsDone), retryAfter))
                    }
                    ForgotPasswordOutcome.ServerError -> {
                        view?.showResendCooldown(0)
                        view?.showMessage(string(R.string.forgot_password_server_error))
                    }
                    // Neutral 4xx: behave as if sent so account existence isn't revealed.
                    ForgotPasswordOutcome.Neutral -> onResent()
                }
                NetworkResult.Loading -> Unit
                else -> onResent()
            }
        }
    }

    /**
     * Returned from the password step because the code died. The user has no usable code, so the client-side
     * cooldown and cap are cleared to let them request a new one; the server's per-email limits still apply
     * (a 429 on resend starts the usual back-off).
     */
    fun onCodeExpired() {
        cooldownJob?.cancel()
        resendsDone = 0
        view?.showResendCooldown(0)
        view?.showCodeExpired()
    }

    fun navigateToLogin() {
        view?.navigateToLogin()
    }

    private fun onResent() {
        resendsDone++
        view?.showMessage(string(R.string.reset_password_code_resent))
        if (ResetResendPolicy.canResend(resendsDone)) {
            startCooldown(ResetResendPolicy.cooldownSeconds(resendsDone))
        } else {
            view?.showResendUnavailable()
            view?.showMessage(string(R.string.reset_password_resend_limit_reached))
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            for (left in seconds downTo 1) {
                view?.showResendCooldown(left)
                delay(1_000)
            }
            view?.showResendCooldown(0)
        }
    }

    private fun string(resId: Int): String = appContext?.getString(resId).orEmpty()

    private companion object {
        val CODE_REGEX = Regex("^\\d{6}$")
    }
}
