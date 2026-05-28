package com.thedavelopers.eventqr.features.auth.changepassword

import com.thedavelopers.eventqr.core.util.Validators

class ChangePasswordPresenter(
    private var view: ChangePasswordContract.View?,
) {
    fun attach(view: ChangePasswordContract.View) {
        this.view = view
    }

    fun detach() {
        view = null
    }

    fun submitChange(currentPassword: String, newPassword: String, confirmPassword: String) {
        val currentValue = currentPassword.trim()
        val newValue = newPassword.trim()
        val confirmValue = confirmPassword.trim()

        var valid = true
        if (!Validators.isValidPassword(currentValue)) {
            view?.showCurrentPasswordError("Enter your current password")
            valid = false
        } else {
            view?.showCurrentPasswordError(null)
        }
        if (!Validators.isValidPassword(newValue)) {
            view?.showNewPasswordError("Password must be at least 6 characters")
            valid = false
        } else {
            view?.showNewPasswordError(null)
        }
        if (newValue != confirmValue) {
            view?.showConfirmPasswordError("Passwords do not match")
            valid = false
        } else {
            view?.showConfirmPasswordError(null)
        }

        if (!valid) {
            return
        }

        view?.showLoading(true)
        view?.showLoading(false)
        view?.showMessage("Endpoint not available yet")
    }

    fun backToSignIn() {
        view?.navigateBackToSignIn()
    }
}