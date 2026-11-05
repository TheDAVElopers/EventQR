package com.thedavelopers.eventqr.features.auth.changepassword

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
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
import com.thedavelopers.eventqr.core.util.setInlineError
import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.icons.FieldIcons

open class ChangePasswordActivity : AppCompatActivity(), ChangePasswordContract.View {
    private lateinit var presenter: ChangePasswordPresenter
    private lateinit var currentPasswordInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var currentPasswordErrorText: TextView
    private lateinit var newPasswordErrorText: TextView
    private lateinit var confirmPasswordErrorText: TextView
    private lateinit var changeButton: Button
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
        progressBar = findViewById(R.id.progressChange)
        requirementsLayout = findViewById(R.id.layoutPasswordRequirements)
        passwordLengthRequirement = findViewById(R.id.txtPasswordLengthRequirement)
        passwordCapitalRequirement = findViewById(R.id.txtPasswordCapitalRequirement)
        passwordLowercaseRequirement = findViewById(R.id.txtPasswordLowercaseRequirement)
        passwordNumberRequirement = findViewById(R.id.txtPasswordNumberRequirement)
        passwordSpecialRequirement = findViewById(R.id.txtPasswordSpecialRequirement)

        configurePasswordToggle(currentPasswordInput)
        configurePasswordToggle(newPasswordInput)
        configurePasswordToggle(confirmPasswordInput)

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
            presenter.submitChange(
                currentPasswordInput.text.toString(),
                newPasswordInput.text.toString(),
                confirmPasswordInput.text.toString()
            )
        }

        findViewById<EventQrDetailHeader>(R.id.toolbarChangePassword)
            .backButton.setOnClickListener { presenter.navigateBack() }
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
        currentPasswordInput.setInlineError(message, currentPasswordErrorText)
    }

    override fun showNewPasswordError(message: String?) {
        newPasswordInput.setInlineError(
            if (message == Validators.PASSWORD_TOO_LONG_ERROR) getString(R.string.error_password_too_long) else message,
            newPasswordErrorText
        )
    }

    override fun showConfirmPasswordError(message: String?) {
        confirmPasswordInput.setInlineError(message, confirmPasswordErrorText)
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
        newPasswordInput.setInlineError(
            if (!requirements.withinMaxLength) getString(R.string.error_password_too_long) else null,
            newPasswordErrorText
        )

        validateButtonState()
    }

    private fun validateButtonState() {
        val requirements = Validators.passwordRequirements(newPasswordInput.text.toString())
        changeButton.isEnabled = requirements.isValid
                && newPasswordInput.text.toString() == confirmPasswordInput.text.toString()
                && currentPasswordInput.text.toString().isNotBlank()
    }

    /**
     * One checklist row, same mark as Registration: the tokenised ui_rule_on / ui_rule_off drawable
     * carries the state (lime only when the rule is met). The label is never tinted by the state:
     * ui_text_primary when met, ui_text_secondary (6.0:1) while pending - never the disabled colour.
     */
    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        view.text = label
        view.setTextColor(getColor(if (isMet) R.color.ui_text_primary else R.color.ui_text_secondary))
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (isMet) R.drawable.ui_rule_on else R.drawable.ui_rule_off,
            0,
            0,
            0
        )
    }

    private fun configurePasswordToggle(input: EditText) {
        FieldIcons.apply(input)
        input.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP && event.rawX >= input.right - input.compoundPaddingEnd) {
                val isVisible = input.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                if (isVisible) {
                    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    FieldIcons.setTrailing(input, AppIcons.Visibility)
                } else {
                    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    FieldIcons.setTrailing(input, AppIcons.VisibilityOff)
                }
                input.setSelection(input.text.length)
                view.performClick()
                true
            } else {
                false
            }
        }
    }
}

