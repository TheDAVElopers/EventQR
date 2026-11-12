package com.thedavelopers.eventqr.features.auth.changepassword

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.logSummary
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.HttpException

class ChangePasswordPresenter() {
    private var view: ChangePasswordContract.View? = null
    private val scope = MainScope()
    private var job: kotlinx.coroutines.Job? = null
    @VisibleForTesting
    internal var repository: AuthRepository? = null
    private var appContext: Context? = null

    fun attach(view: ChangePasswordContract.View, context: Context) {
        this.view = view
        this.appContext = context.applicationContext
        this.repository = AuthRepository(context)
    }

    fun detach() {
        job?.cancel()
        scope.cancel()
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
        job = scope.launch {
            when (val result = repository?.changePassword(currentPassword, newPassword, confirmPassword)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showSuccess()
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    Log.w(TAG, "change password failed: ${result.logSummary()}")
                    val status = (result.throwable as? HttpException)?.code()
                    when {
                        // The API names the field only for IncorrectCurrentPasswordException, so only that 400 is a
                        // field error (fixed copy, not the server text).
                        status == 400 && result.serverMessage == CURRENT_PASSWORD_INCORRECT_MESSAGE ->
                            view?.showCurrentPasswordError(string(R.string.ui_auth_current_password_incorrect))
                        // Any other 400 (the server's password policy, a mismatch) names no field: stay and say
                        // what to check. The client-side checks ran first, so this is a rule they do not mirror.
                        status == 400 -> view?.showFormError(string(R.string.ui_auth_change_password_rejected))
                        status == 429 -> view?.showFormError(string(R.string.ui_auth_error_rate_limited))
                        result.throwable is java.io.IOException -> view?.showFormError(string(R.string.ui_auth_error_network))
                        else -> view?.showFormError(string(R.string.ui_auth_change_password_failed))
                    }
                }
                NetworkResult.Loading -> Unit
                null -> {
                    view?.showLoading(false)
                    view?.showFormError(string(R.string.ui_auth_change_password_failed))
                }
            }
        }
    }

    fun navigateBack() {
        view?.navigateBack()
    }

    private fun string(resId: Int): String = appContext?.getString(resId).orEmpty()

    companion object {
        private const val TAG = "ChangePasswordPresenter"

        /** Backend 400 message meaning the current password is wrong. */
        const val CURRENT_PASSWORD_INCORRECT_MESSAGE = "Current password is incorrect"
    }
}
