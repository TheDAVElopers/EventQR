package com.thedavelopers.eventqr.features.auth.register

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow

private const val FIELD_PHONE = "phone"

open class RegistrationActivity : AppCompatActivity(), RegistrationContract.View {
    private lateinit var presenter: RegistrationPresenter

    private val firstName = MutableStateFlow("")
    private val lastName = MutableStateFlow("")
    private val email = MutableStateFlow("")
    private val phoneDigits = MutableStateFlow("")
    private val password = MutableStateFlow("")
    private val confirmPassword = MutableStateFlow("")
    private val termsAccepted = MutableStateFlow(false)
    private val fieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    private val isLoading = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter = RegistrationPresenter(this, AuthRepository(this))
        presenter.attach(this)

        setContent {
            EventQrTheme {
                RegistrationScreen(
                    firstName = firstName.collectAsStateWithLifecycle().value,
                    onFirstNameChange = { firstName.value = it },
                    lastName = lastName.collectAsStateWithLifecycle().value,
                    onLastNameChange = { lastName.value = it },
                    email = email.collectAsStateWithLifecycle().value,
                    onEmailChange = { email.value = it },
                    phoneDigits = phoneDigits.collectAsStateWithLifecycle().value,
                    onPhoneDigitsChange = { phoneDigits.value = normalizePhoneDigits(it) },
                    password = password.collectAsStateWithLifecycle().value,
                    onPasswordChange = { password.value = it },
                    confirmPassword = confirmPassword.collectAsStateWithLifecycle().value,
                    onConfirmPasswordChange = { confirmPassword.value = it },
                    termsAccepted = termsAccepted.collectAsStateWithLifecycle().value,
                    onTermsAcceptedChange = { termsAccepted.value = it },
                    fieldErrors = fieldErrors.collectAsStateWithLifecycle().value,
                    isLoading = isLoading.collectAsStateWithLifecycle().value,
                    onRegister = { submitRegistration() },
                    onSignIn = { navigateToSignIn() },
                )
            }
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        this.isLoading.value = isLoading
    }

    /**
     * Exposes the terms checkbox state (plain public method; the contract
     * interface stays minimal since the presenter gates nothing on it —
     * button enablement is enforced entirely in the view).
     */
    fun isTermsAccepted(): Boolean = termsAccepted.value

    override fun showFieldError(field: String, message: String?) {
        fieldErrors.value = if (message.isNullOrBlank()) {
            fieldErrors.value - field
        } else {
            fieldErrors.value + (field to message)
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun navigateToSignIn() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun submitRegistration() {
        // EventQR - UI validation deviation beyond SRS UC-01 field spec
        // Field holds 10 local digits under a fixed "+63" prefix; the full E.164 value
        // (+63 + digits, e.g. +639171234567) is assembled only here at submit time.
        val digits = phoneDigits.value
        if (digits.length != 10) {
            showFieldError(FIELD_PHONE, "Enter valid 10-digit mobile number")
            return
        }
        showFieldError(FIELD_PHONE, null)

        presenter.submitRegistration(
            firstName.value,
            lastName.value,
            email.value,
            "+63$digits",
            password.value,
            confirmPassword.value,
        )
    }

    // EventQR - UI validation deviation beyond SRS UC-01 field spec
    // Phone format enforcement: numeric-only entry capped at 10 digits (PH mobile without
    // leading 0). Pasted full numbers ("0917...", "63917...", "+63917...") are auto-normalized
    // to the last 10 digits; a live n/10 counter mirrors the field state.
    private fun normalizePhoneDigits(input: String): String {
        var digits = input.filter { it.isDigit() }
        while (digits.length > 10 && digits.startsWith("0")) {
            digits = digits.removePrefix("0")
        }
        while (digits.length > 10 && digits.startsWith("63")) {
            digits = digits.removePrefix("63")
        }
        if (digits.startsWith("0")) {
            digits = digits.removePrefix("0")
        }
        return digits.take(10)
    }
}
