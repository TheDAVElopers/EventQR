package com.thedavelopers.eventqr.features.auth.login

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LoginPresenter(
    private var view: LoginContract.View?,
    private val repository: AuthRepository,
) {
    private var loginJob: Job? = null

    fun attach(view: LoginContract.View) {
        this.view = view
    }

    fun detach() {
        loginJob?.cancel()
        view = null
    }

    fun submitLogin(email: String, password: String) {
        val emailValue = email.trim()
        val passwordValue = password.trim()

        var valid = true
        if (!Validators.isValidEmail(emailValue)) {
            view?.showEmailError("Enter a valid email address")
            valid = false
        } else {
            view?.showEmailError(null)
        }

        if (!Validators.isValidPassword(passwordValue)) {
            view?.showPasswordError("Password must be at least 6 characters")
            valid = false
        } else {
            view?.showPasswordError(null)
        }

        if (!valid) {
            return
        }

        loginJob = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.login(emailValue, passwordValue)) {
                is NetworkResult.Success -> {
                    val loginResponse = result.data
                    repository.storeSession(loginResponse)
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
                    view?.showMessage(result.message ?: loginResponse.message ?: "Login successful")

                    if (resolvedRole == null) {
                        view?.showMessage("Unable to determine account role")
                    }

                    view?.navigateToDashboard(resolvedRole?.name)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun openRegistration() {
        view?.navigateToRegistration()
    }

    fun openForgotPassword() {
        view?.navigateToForgotPassword()
    }
}