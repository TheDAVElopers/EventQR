package com.thedavelopers.eventqr.features.auth.changepassword

import android.content.Context
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class ChangePasswordPresenter() {
    private var view: ChangePasswordContract.View? = null
    private var job: kotlinx.coroutines.Job? = null
    private var repository: AuthRepository? = null
    private var appContext: Context? = null

    fun attach(view: ChangePasswordContract.View, context: Context) {
        this.view = view
        this.appContext = context.applicationContext
        this.repository = AuthRepository(context)
    }

    fun detach() {
        job?.cancel()
        view = null
        repository = null
    }

    fun submitChange(currentPassword: String, newPassword: String, confirmPassword: String) {
        view?.showCurrentPasswordError(null)
        view?.showNewPasswordError(null)
        view?.showConfirmPasswordError(null)

        if (currentPassword.isBlank()) {
            view?.showCurrentPasswordError(string(R.string.password_error_current_required))
            return
        }

        val requirements = Validators.passwordRequirements(newPassword)
        if (!requirements.isValid) {
            if (requirements.isOtherwiseValid) {
                view?.showNewPasswordError(Validators.PASSWORD_TOO_LONG_ERROR)
                return
            }
            view?.showNewPasswordError(string(R.string.password_policy_hint))
            return
        }

        if (newPassword != confirmPassword) {
            view?.showConfirmPasswordError(string(R.string.password_error_mismatch))
            return
        }

        view?.showLoading(true)
        job = MainScope().launch {
            when (val result = repository?.changePassword(currentPassword, newPassword, confirmPassword)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showSuccess()
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: string(R.string.change_password_failed))
                }
                NetworkResult.Loading -> Unit
                null -> {
                    view?.showLoading(false)
                    view?.showMessage(string(R.string.change_password_failed))
                }
            }
        }
    }

    fun navigateBack() {
        view?.navigateBack()
    }

    private fun string(resId: Int): String = appContext?.getString(resId).orEmpty()
}
