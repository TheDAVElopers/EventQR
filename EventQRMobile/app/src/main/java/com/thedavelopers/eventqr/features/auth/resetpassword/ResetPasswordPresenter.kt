package com.thedavelopers.eventqr.features.auth.resetpassword

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class ResetPasswordPresenter() {
    private var view: ResetPasswordContract.View? = null
    private var job: kotlinx.coroutines.Job? = null
    private var repository: AuthRepository? = null
    private var appContext: Context? = null

    @VisibleForTesting
    internal var token: String? = null

    fun attach(view: ResetPasswordContract.View, context: Context) {
        this.view = view
        this.appContext = context.applicationContext
        this.repository = AuthRepository(context)
    }

    fun detach() {
        job?.cancel()
        view = null
        repository = null
        appContext = null
    }

    fun validateToken(token: String?) {
        if (token.isNullOrBlank()) {
            view?.showTokenInvalid()
            return
        }
        this.token = token
        view?.showLoading(true)
        job = MainScope().launch {
            when (val result = repository?.validateResetToken(token)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    val data = result.data
                    if (data != null && data.valid) {
                        view?.showForm()
                    } else {
                        view?.showTokenInvalid()
                    }
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showTokenInvalid()
                }
                NetworkResult.Loading -> Unit
                null -> {
                    view?.showLoading(false)
                    view?.showTokenInvalid()
                }
            }
        }
    }

    fun submitReset(newPassword: String, confirmPassword: String) {
        val currentToken = token
        if (currentToken.isNullOrBlank()) {
            view?.showMessage(string(R.string.reset_token_missing))
            return
        }

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
        job = MainScope().launch {
            when (val result = repository?.resetPassword(currentToken, newPassword, confirmPassword)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showSuccess()
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

    fun navigateToLogin() {
        view?.navigateToLogin()
    }

    private fun string(resId: Int): String = appContext?.getString(resId).orEmpty()
}
