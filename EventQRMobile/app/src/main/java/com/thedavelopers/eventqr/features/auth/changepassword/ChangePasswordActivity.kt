package com.thedavelopers.eventqr.features.auth.changepassword

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthErrorStrip
import com.thedavelopers.eventqr.features.auth.AuthFieldErrors
import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader
import com.thedavelopers.eventqr.ui.icons.FieldIcons
import com.thedavelopers.eventqr.ui.icons.RuleMarks

open class ChangePasswordActivity : AppCompatActivity(), ChangePasswordContract.View {
    private lateinit var presenter: ChangePasswordPresenter
    private lateinit var currentPasswordInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var currentPasswordErrorText: TextView
    private lateinit var newPasswordErrorText: TextView
    private lateinit var confirmPasswordErrorText: TextView
    private lateinit var changeButton: Button
    private lateinit var formError: AuthErrorStrip
    private val fieldErrors = AuthFieldErrors()
    private lateinit var progressBar: ProgressBar
    private lateinit var requirementsLayout: LinearLayout
    private lateinit var passwordLengthRequirement: TextView
    private lateinit var passwordCapitalRequirement: TextView
    private lateinit var passwordLowercaseRequirement: TextView
    private lateinit var passwordNumberRequirement: TextView
    private lateinit var passwordSpecialRequirement: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        // Edge to edge (EventQrApplication): the footer's fill runs behind the navigation bar,
        // its content (the 52dp submit button) stays inside the safe area.
        ScreenInsets.padForNavigationBar(findViewById(R.id.authFooter))

        presenter = ChangePasswordPresenter()
        presenter.attach(this, this)

        currentPasswordInput = findViewById(R.id.edtCurrentPassword)
        newPasswordInput = findViewById(R.id.edtNewPassword)
        confirmPasswordInput = findViewById(R.id.edtConfirmPassword)
        currentPasswordErrorText = findViewById(R.id.txtCurrentPasswordError)
        newPasswordErrorText = findViewById(R.id.txtNewPasswordError)
        confirmPasswordErrorText = findViewById(R.id.txtConfirmPasswordError)
        changeButton = findViewById(R.id.btnChangePassword)
        // No Retry on this screen (a repeat could send a replaced password), so the strip has no actions.
        formError = AuthErrorStrip(findViewById(R.id.errorChangePassword))
        fieldErrors.register(FIELD_CURRENT_PASSWORD, currentPasswordInput, currentPasswordErrorText)
        fieldErrors.register(FIELD_NEW_PASSWORD, newPasswordInput, newPasswordErrorText)
        fieldErrors.register(FIELD_CONFIRM_PASSWORD, confirmPasswordInput, confirmPasswordErrorText)
        progressBar = findViewById(R.id.progressChange)
        requirementsLayout = findViewById(R.id.layoutPasswordRequirements)
        passwordLengthRequirement = findViewById(R.id.txtPasswordLengthRequirement)
        passwordCapitalRequirement = findViewById(R.id.txtPasswordCapitalRequirement)
        passwordLowercaseRequirement = findViewById(R.id.txtPasswordLowercaseRequirement)
        passwordNumberRequirement = findViewById(R.id.txtPasswordNumberRequirement)
        passwordSpecialRequirement = findViewById(R.id.txtPasswordSpecialRequirement)

        FieldIcons.apply(currentPasswordInput)
        FieldIcons.apply(newPasswordInput)
        FieldIcons.apply(confirmPasswordInput)

        // Requirements only show when new password field is focused or has text
        newPasswordInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus || newPasswordInput.text.toString().isNotEmpty()) {
                updateRequirements(newPasswordInput.text.toString())
            } else {
                requirementsLayout.visibility = View.GONE
            }
        }

        newPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateRequirements(newPasswordInput.text.toString())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        confirmPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateButtonState()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        currentPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateButtonState()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        changeButton.setOnClickListener {
            formError.clear()
            presenter.submitChange(
                currentPasswordInput.text.toString(),
                newPasswordInput.text.toString(),
                confirmPasswordInput.text.toString()
            )
        }

        findViewById<EventQrDetailHeader>(R.id.toolbarChangePassword)
            .backButton.setOnClickListener { presenter.navigateBack() }
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
        changeButton.isEnabled = !isLoading
        changeButton.text = getString(if (isLoading) R.string.change_password_changing else R.string.common_change_password)
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showCurrentPasswordError(message: String?) {
        fieldErrors.raise(FIELD_CURRENT_PASSWORD, message)
    }

    override fun showNewPasswordError(message: String?) {
        fieldErrors.raise(
            FIELD_NEW_PASSWORD,
            if (message == Validators.PASSWORD_TOO_LONG_ERROR) getString(R.string.error_password_too_long) else message,
        )
    }

    override fun showConfirmPasswordError(message: String?) {
        fieldErrors.raise(FIELD_CONFIRM_PASSWORD, message)
    }

    /** No Retry: a repeat after a lost answer would send a current password the server already replaced. */
    override fun showFormError(message: String) {
        formError.show(message, retryAction = null)
    }

    override fun showSuccess() {
        Toast.makeText(this, this.getString(R.string.change_password_password_changed_successfully), Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun navigateBack() {
        finish()
    }

    private fun updateRequirements(password: String) {
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

        validateButtonState()
    }

    private fun validateButtonState() {
        val requirements = Validators.passwordRequirements(newPasswordInput.text.toString())
        changeButton.isEnabled = requirements.isValid
                && newPasswordInput.text.toString() == confirmPasswordInput.text.toString()
                && currentPasswordInput.text.toString().isNotBlank()
    }

    /** One checklist row (§6.1): the 16dp mark carries the state, never colour alone. */
    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        RuleMarks.apply(view, label, isMet)
    }

    private companion object {
        const val FIELD_CURRENT_PASSWORD = "currentPassword"
        const val FIELD_NEW_PASSWORD = "newPassword"
        const val FIELD_CONFIRM_PASSWORD = "confirmPassword"
    }
}
