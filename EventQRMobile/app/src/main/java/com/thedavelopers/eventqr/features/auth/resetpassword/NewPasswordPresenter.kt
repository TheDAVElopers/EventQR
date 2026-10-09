package com.thedavelopers.eventqr.features.auth.resetpassword

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.forgotpassword.ForgotPasswordOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException

class NewPasswordPresenter() {
    private var view: NewPasswordContract.View? = null
    private val scope = MainScope()
    private var job: Job? = null
    private var appContext: Context? = null

    @VisibleForTesting
    internal var repository: AuthRepository? = null

    @VisibleForTesting
    internal var email: String? = null

    @VisibleForTesting
    internal var code: String? = null

    fun attach(view: NewPasswordContract.View, context: Context) {
        this.view = view
        this.appContext = context.applicationContext
        this.repository = AuthRepository(context)
    }

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
        repository = null
        appContext = null
    }

    /** Email and the already verified code arrive from the code screen; without them there is nothing to reset. */
    fun start(email: String?, code: String?) {
        if (email.isNullOrBlank() || code.isNullOrBlank()) {
            view?.navigateToLogin()
            return
        }
        this.email = email
        this.code = code
    }

    fun submit(newPassword: String, confirmPassword: String) {
        val currentEmail = email
        val currentCode = code
        if (currentEmail.isNullOrBlank() || currentCode.isNullOrBlank()) {
            view?.navigateToLogin()
            return
        }
        if (job?.isActive == true) return

        view?.showPasswordError(null)
        view?.showConfirmPasswordError(null)

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
            when (val result = repository?.resetPassword(currentEmail, currentCode, newPassword, confirmPassword)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(string(R.string.reset_password_success))
                    view?.navigateToLogin()
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    val throwable = result.throwable
                    when {
                        throwable is HttpException && throwable.code() == 400 &&
                            result.serverMessage == CODE_EXPIRED_MESSAGE ->
                            // The code expired (or was used up) since it was verified.
                            view?.navigateBackToExpiredCode(currentEmail)
                        // Any other 400 (password policy, mismatch): stay and show why.
                        throwable is HttpException && throwable.code() == 400 ->
                            view?.showMessage(result.serverMessage ?: result.message)
                        ForgotPasswordOutcome.classify(throwable) == ForgotPasswordOutcome.RateLimited ->
                            view?.showMessage(string(R.string.forgot_password_rate_limited))
                        else -> view?.showMessage(result.message)
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

    private fun string(resId: Int): String = appContext?.getString(resId).orEmpty()

    companion object {
        /** Backend 400 message meaning the reset code is wrong, expired, or already used. */
        const val CODE_EXPIRED_MESSAGE = "Reset code is invalid or expired"
    }
}
