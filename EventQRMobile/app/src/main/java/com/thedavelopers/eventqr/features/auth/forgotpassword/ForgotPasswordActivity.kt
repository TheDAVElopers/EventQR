package com.thedavelopers.eventqr.features.auth.forgotpassword

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow

open class ForgotPasswordActivity : AppCompatActivity(), ForgotPasswordContract.View {
    private lateinit var presenter: ForgotPasswordPresenter

    private val email = MutableStateFlow("")
    private val emailError = MutableStateFlow<String?>(null)
    private val isLoading = MutableStateFlow(false)
    private val isConfirmationVisible = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter = ForgotPasswordPresenter()
        presenter.attach(this, this)

        setContent {
            EventQrTheme {
                ForgotPasswordScreen(
                    email = email.collectAsStateWithLifecycle().value,
                    onEmailChange = { email.value = it },
                    emailError = emailError.collectAsStateWithLifecycle().value,
                    isLoading = isLoading.collectAsStateWithLifecycle().value,
                    showConfirmation = isConfirmationVisible.collectAsStateWithLifecycle().value,
                    onSendResetLink = { presenter.submitRequest(email.value) },
                    onBackToSignIn = { presenter.backToSignIn() },
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

    override fun showEmailError(message: String?) {
        emailError.value = message
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showConfirmation() {
        isConfirmationVisible.value = true
    }

    override fun navigateBackToSignIn() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
