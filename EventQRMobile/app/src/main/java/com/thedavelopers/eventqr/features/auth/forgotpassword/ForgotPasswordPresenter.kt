package com.thedavelopers.eventqr.features.auth.forgotpassword

import com.thedavelopers.eventqr.core.util.UiStrings
import android.content.Context
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ForgotPasswordPresenter() {
    private var view: ForgotPasswordContract.View? = null
    private val scope = MainScope()
    private var job: kotlinx.coroutines.Job? = null
    private var repository: AuthRepository? = null
    private var appContext: Context? = null

    fun attach(view: ForgotPasswordContract.View, context: Context) {
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

    fun submitRequest(email: String) {
        val emailValue = email.trim()
        if (!Validators.isValidEmail(emailValue)) {
            view?.showEmailError(appContext?.getString(R.string.create_admin_account_enter_a_valid_email_address))
            return
        }

        view?.showEmailError(null)
        view?.showLoading(true)
        job = scope.launch {
            when (val result = repository?.forgotPassword(emailValue)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.navigateToResetPassword(emailValue)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    when (ForgotPasswordOutcome.classify(result.throwable)) {
                        // Offline or timed out: the request never got an answer, so don't tell
                        // the user an email is on its way.
                        ForgotPasswordOutcome.NetworkFailure -> view?.showMessage(result.message)
                        // Rate limited / server failure: the code was NOT sent, say so.
                        ForgotPasswordOutcome.RateLimited -> appContext?.let { view?.showMessage(it.getString(R.string.forgot_password_rate_limited)) }
                        ForgotPasswordOutcome.ServerError -> appContext?.let { view?.showMessage(it.getString(R.string.forgot_password_server_error)) }
                        // 4xx validation-type answer: keep the neutral flow (go on to the code screen) so the screen
                        // doesn't reveal whether an account exists.
                        ForgotPasswordOutcome.Neutral -> view?.navigateToResetPassword(emailValue)
                    }
                }
                NetworkResult.Loading -> Unit
                null -> {
                    view?.showLoading(false)
                    view?.navigateToResetPassword(emailValue)
                }
            }
        }
    }

    fun backToSignIn() {
        view?.navigateBackToSignIn()
    }
}

/** Maps a failed forgot-password call to what the user should be told. Pure, so it is unit tested. */
sealed interface ForgotPasswordOutcome {
    data object NetworkFailure : ForgotPasswordOutcome
    data object RateLimited : ForgotPasswordOutcome
    data object ServerError : ForgotPasswordOutcome
    data object Neutral : ForgotPasswordOutcome

    companion object {
        fun classify(throwable: Throwable?): ForgotPasswordOutcome = when {
            throwable is java.io.IOException -> NetworkFailure
            throwable is retrofit2.HttpException && throwable.code() == 429 -> RateLimited
            throwable is retrofit2.HttpException && throwable.code() >= 500 -> ServerError
            else -> Neutral
        }
    }
}
