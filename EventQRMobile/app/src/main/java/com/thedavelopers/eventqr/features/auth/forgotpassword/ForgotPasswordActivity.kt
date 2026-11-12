package com.thedavelopers.eventqr.features.auth.forgotpassword

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.core.util.Wordmark
import com.thedavelopers.eventqr.features.auth.AuthErrorStrip
import com.thedavelopers.eventqr.features.auth.AuthFieldErrors
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.auth.resetpassword.ResetPasswordActivity
import com.thedavelopers.eventqr.ui.icons.FieldIcons

open class ForgotPasswordActivity : AppCompatActivity(), ForgotPasswordContract.View {
    private lateinit var presenter: ForgotPasswordPresenter
    private lateinit var emailInput: EditText
    private lateinit var sendButton: Button
    private lateinit var formError: AuthErrorStrip
    private val fieldErrors = AuthFieldErrors()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        enableEdgeToEdge()
        ScreenInsets.padForStatusBar(findViewById(R.id.authTopBar))
        Wordmark.applyBarRules(this)
        ScreenInsets.padForNavigationBar(findViewById(R.id.authScroll))

        presenter = ForgotPasswordPresenter()
        emailInput = findViewById(R.id.editEmail)
        sendButton = findViewById(R.id.btnSendCode)
        formError = AuthErrorStrip(findViewById(R.id.errorForgotPassword), mapOf(AuthErrorStrip.SUBMIT to ::submit))
        fieldErrors.register(FIELD_EMAIL, emailInput, findViewById(R.id.txtEmailError))
        FieldIcons.apply(emailInput)
        presenter.attach(this, this)

        sendButton.setOnClickListener { submit() }

        findViewById<View>(R.id.tvBackToSignIn).setOnClickListener {
            presenter.backToSignIn()
        }
    }

    /** Every attempt, including a strip `Retry`, starts from a clean strip. */
    private fun submit() {
        formError.clear()
        presenter.submitRequest(emailInput.text.toString())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        fieldErrors.save(outState)
        formError.save(outState)
    }

    // After super: restoring the typed text runs the edit watchers that clear an error.
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        fieldErrors.restore(savedInstanceState)
        formError.restore(savedInstanceState)
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        sendButton.isEnabled = !isLoading
        sendButton.text = getString(if (isLoading) R.string.forgot_password_sending else R.string.forgot_password_send_code)
    }

    override fun showEmailError(message: String?) {
        fieldErrors.raise(FIELD_EMAIL, message)
    }

    override fun showFormError(message: String, retryable: Boolean) {
        formError.show(message, if (retryable) AuthErrorStrip.SUBMIT else null)
    }

    override fun navigateToResetPassword(email: String) {
        startActivity(Intent(this, ResetPasswordActivity::class.java).putExtra(ResetPasswordActivity.EXTRA_EMAIL, email))
    }

    override fun navigateBackToSignIn() {
        // Return to the Sign In beneath (opened from it) instead of stacking a second one.
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private companion object {
        const val FIELD_EMAIL = "email"
    }
}
