package com.thedavelopers.eventqr.features.auth.forgotpassword

import com.thedavelopers.eventqr.core.util.Validators

class ForgotPasswordPresenter(
    private var view: ForgotPasswordContract.View?,
) {
    fun attach(view: ForgotPasswordContract.View) {
        this.view = view
    }

    fun detach() {
        view = null
    }

    fun submitRequest(email: String) {
        val emailValue = email.trim()
        if (!Validators.isValidEmail(emailValue)) {
            view?.showEmailError("Enter a valid email address")
            return
        }

        view?.showEmailError(null)
        view?.showLoading(true)
        view?.showLoading(false)
        view?.showMessage("Endpoint not available yet")
    }

    fun backToSignIn() {
        view?.navigateBackToSignIn()
    }
}