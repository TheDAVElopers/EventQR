package com.thedavelopers.eventqr.features.auth.resetpassword

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.auth.login.LoginActivity

/** Code step of the reset flow; the password is chosen on [NewPasswordActivity] once the code checks out. */
open class ResetPasswordActivity : AppCompatActivity(), ResetPasswordContract.View {
    private lateinit var presenter: ResetPasswordPresenter
    private lateinit var verifyButton: Button
    private lateinit var codeInput: EditText
    private lateinit var resendButton: Button
    private lateinit var codeSentToText: TextView
    private lateinit var codeExpiredText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        presenter = ResetPasswordPresenter()
        presenter.attach(this, this)

        verifyButton = findViewById(R.id.btnVerifyCode)
        codeInput = findViewById(R.id.edtResetCode)
        resendButton = findViewById(R.id.btnResendCode)
        codeSentToText = findViewById(R.id.txtCodeSentTo)
        codeExpiredText = findViewById(R.id.txtCodeExpired)

        verifyButton.setOnClickListener {
            presenter.submitCode(codeInput.text.toString())
        }

        findViewById<Button>(R.id.btnGoToLogin).setOnClickListener {
            presenter.navigateToLogin()
        }

        resendButton.setOnClickListener {
            presenter.resendCode()
        }

        presenter.start(intent.getStringExtra(EXTRA_EMAIL))
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_CODE_EXPIRED, false)) {
            presenter.onCodeExpired()
        }
    }

    /** Re-entered from the password step; keep this instance so the resend cooldown and cap survive. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_CODE_EXPIRED, false)) presenter.onCodeExpired()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        verifyButton.isEnabled = !isLoading
        verifyButton.text = getString(if (isLoading) R.string.reset_password_verifying else R.string.reset_password_verify)
    }

    override fun showEmail(email: String) {
        codeSentToText.text = getString(R.string.reset_password_code_sent_to, email)
    }

    override fun showCodeError(message: String?) {
        codeExpiredText.visibility = View.GONE
        codeInput.error = message
    }

    override fun showCodeExpired() {
        codeInput.text.clear()
        codeInput.error = null
        codeExpiredText.text = getString(R.string.reset_password_code_expired_resend)
        codeExpiredText.visibility = View.VISIBLE
        // The presenter re-enables Resend before this, so it can take focus.
        resendButton.requestFocus()
    }

    override fun showResendCooldown(secondsLeft: Int) {
        resendButton.isEnabled = secondsLeft <= 0
        resendButton.text = if (secondsLeft > 0) {
            getString(R.string.reset_password_resend_code_in, secondsLeft)
        } else {
            getString(R.string.reset_password_resend_code)
        }
    }

    override fun showResendUnavailable() {
        resendButton.isEnabled = false
        resendButton.text = getString(R.string.reset_password_resend_unavailable)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun navigateToNewPassword(email: String, code: String) {
        startActivity(
            Intent(this, NewPasswordActivity::class.java)
                .putExtra(NewPasswordActivity.EXTRA_EMAIL, email)
                .putExtra(NewPasswordActivity.EXTRA_CODE, code)
        )
    }

    override fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"

        /** Set when the password step bounced back because the code expired; never carries the code itself. */
        const val EXTRA_CODE_EXPIRED = "extra_code_expired"
    }
}
