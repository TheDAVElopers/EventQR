package com.thedavelopers.eventqr.features.auth.resetpassword

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.core.util.Wordmark
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthErrorStrip
import com.thedavelopers.eventqr.features.auth.AuthFieldErrors
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.ui.icons.FieldIcons
import com.thedavelopers.eventqr.ui.icons.RuleMarks

/** Final step of the reset flow; receives the email and the already verified code from [ResetPasswordActivity]. */
open class NewPasswordActivity : AppCompatActivity(), NewPasswordContract.View {
    private lateinit var presenter: NewPasswordPresenter
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var newPasswordErrorText: TextView
    private lateinit var confirmPasswordErrorText: TextView
    private lateinit var resetButton: Button
    private lateinit var requirementsLayout: LinearLayout
    private lateinit var passwordLengthRequirement: TextView
    private lateinit var passwordCapitalRequirement: TextView
    private lateinit var passwordLowercaseRequirement: TextView
    private lateinit var passwordNumberRequirement: TextView
    private lateinit var passwordSpecialRequirement: TextView
    private lateinit var formError: AuthErrorStrip
    private val fieldErrors = AuthFieldErrors()
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_password)
        enableEdgeToEdge()
        ScreenInsets.padForStatusBar(findViewById(R.id.authTopBar))
        Wordmark.applyBarRules(this)
        ScreenInsets.padForNavigationBar(findViewById(R.id.authFooter))

        presenter = NewPasswordPresenter()
        presenter.attach(this, this)

        newPasswordInput = findViewById(R.id.edtNewPassword)
        confirmPasswordInput = findViewById(R.id.edtConfirmPassword)
        newPasswordErrorText = findViewById(R.id.txtNewPasswordError)
        confirmPasswordErrorText = findViewById(R.id.txtConfirmPasswordError)
        resetButton = findViewById(R.id.btnResetPassword)
        // No Retry on this screen (the reset code is one-time), so the strip has no actions to restore.
        formError = AuthErrorStrip(findViewById(R.id.errorNewPassword))
        fieldErrors.register(FIELD_NEW_PASSWORD, newPasswordInput, newPasswordErrorText)
        fieldErrors.register(FIELD_CONFIRM_PASSWORD, confirmPasswordInput, confirmPasswordErrorText)
        requirementsLayout = findViewById(R.id.layoutPasswordRequirements)
        passwordLengthRequirement = findViewById(R.id.txtPasswordLengthRequirement)
        passwordCapitalRequirement = findViewById(R.id.txtPasswordCapitalRequirement)
        passwordLowercaseRequirement = findViewById(R.id.txtPasswordLowercaseRequirement)
        passwordNumberRequirement = findViewById(R.id.txtPasswordNumberRequirement)
        passwordSpecialRequirement = findViewById(R.id.txtPasswordSpecialRequirement)

        FieldIcons.apply(newPasswordInput)
        FieldIcons.apply(confirmPasswordInput)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordRequirements(newPasswordInput.text.toString())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        newPasswordInput.addTextChangedListener(watcher)
        confirmPasswordInput.addTextChangedListener(watcher)

        resetButton.setOnClickListener {
            formError.clear()
            presenter.submit(
                newPasswordInput.text.toString(),
                confirmPasswordInput.text.toString()
            )
        }

        findViewById<Button>(R.id.btnBackToCode).setOnClickListener {
            finish()
        }

        updatePasswordRequirements(newPasswordInput.text.toString())
        presenter.start(intent.getStringExtra(EXTRA_EMAIL), intent.getStringExtra(EXTRA_CODE))
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
        this.isLoading = isLoading
        resetButton.text = getString(if (isLoading) R.string.reset_password_resetting else R.string.reset_password_reset_password)
        updatePasswordRequirements(newPasswordInput.text.toString())
    }

    override fun showPasswordError(message: String?) {
        fieldErrors.raise(
            FIELD_NEW_PASSWORD,
            if (message == Validators.PASSWORD_TOO_LONG_ERROR) getString(R.string.error_password_too_long) else message,
        )
    }

    override fun showConfirmPasswordError(message: String?) {
        fieldErrors.raise(FIELD_CONFIRM_PASSWORD, message)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /** No Retry: the one-time reset code may already be spent, so the user re-presses Reset password. */
    override fun showFormError(message: String) {
        formError.show(message, retryAction = null)
    }

    override fun navigateBackToExpiredCode(email: String) {
        // CLEAR_TOP + SINGLE_TOP reuses the existing code screen (keeping its resend cooldown/cap) via onNewIntent.
        startActivity(Intent(this, ResetPasswordActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ResetPasswordActivity.EXTRA_EMAIL, email)
            putExtra(ResetPasswordActivity.EXTRA_CODE_EXPIRED, true)
        })
        finish()
    }

    override fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun updatePasswordRequirements(password: String) {
        if (password.isEmpty()) {
            requirementsLayout.visibility = View.GONE
            resetButton.isEnabled = false
            return
        }
        requirementsLayout.visibility = View.VISIBLE

        val requirements = Validators.passwordRequirements(password)
        updateRequirement(passwordLengthRequirement, getString(R.string.password_req_length), requirements.hasMinLength)
        updateRequirement(passwordCapitalRequirement, getString(R.string.password_req_uppercase), requirements.hasCapital)
        updateRequirement(passwordLowercaseRequirement, getString(R.string.password_req_lowercase), requirements.hasLowercase)
        updateRequirement(passwordNumberRequirement, getString(R.string.password_req_number), requirements.hasNumber)
        updateRequirement(passwordSpecialRequirement, getString(R.string.password_req_special), requirements.hasSpecial)
        // Derived from the text on every keystroke: live, so the edit watcher leaves it to this check.
        val tooLong = getString(R.string.error_password_too_long)
        if (!requirements.withinMaxLength) {
            fieldErrors.raise(FIELD_NEW_PASSWORD, tooLong, live = true)
        } else if (fieldErrors.message(FIELD_NEW_PASSWORD) == tooLong) {
            fieldErrors.set(FIELD_NEW_PASSWORD, null)
        }

        resetButton.isEnabled = !isLoading && requirements.isValid &&
            newPasswordInput.text.toString() == confirmPasswordInput.text.toString()
    }

    /** One checklist row (§6.1): the 16dp mark carries the state, never colour alone. */
    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        RuleMarks.apply(view, label, isMet)
    }

    companion object {
        private const val FIELD_NEW_PASSWORD = "newPassword"
        private const val FIELD_CONFIRM_PASSWORD = "confirmPassword"

        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_CODE = "extra_code"
    }
}




