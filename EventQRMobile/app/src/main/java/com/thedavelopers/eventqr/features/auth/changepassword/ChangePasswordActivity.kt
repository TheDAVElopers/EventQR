package com.thedavelopers.eventqr.features.auth.changepassword

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader

open class ChangePasswordActivity : AppCompatActivity(), ChangePasswordContract.View {
    private lateinit var presenter: ChangePasswordPresenter
    private lateinit var currentPasswordInput: TextInputEditText
    private lateinit var newPasswordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var changeButton: MaterialButton
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

        presenter = ChangePasswordPresenter()
        presenter.attach(this, this)

        currentPasswordInput = findViewById(R.id.edtCurrentPassword)
        newPasswordInput = findViewById(R.id.edtNewPassword)
        confirmPasswordInput = findViewById(R.id.edtConfirmPassword)
        changeButton = findViewById(R.id.btnChangePassword)
        progressBar = findViewById(R.id.progressChange)
        requirementsLayout = findViewById(R.id.layoutPasswordRequirements)
        passwordLengthRequirement = findViewById(R.id.txtPasswordLengthRequirement)
        passwordCapitalRequirement = findViewById(R.id.txtPasswordCapitalRequirement)
        passwordLowercaseRequirement = findViewById(R.id.txtPasswordLowercaseRequirement)
        passwordNumberRequirement = findViewById(R.id.txtPasswordNumberRequirement)
        passwordSpecialRequirement = findViewById(R.id.txtPasswordSpecialRequirement)

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
        currentPasswordInput.error = message
    }

    override fun showNewPasswordError(message: String?) {
        newPasswordInput.error = if (message == Validators.PASSWORD_TOO_LONG_ERROR) getString(R.string.error_password_too_long) else message
    }

    override fun showConfirmPasswordError(message: String?) {
        confirmPasswordInput.error = message
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
        newPasswordInput.error = if (!requirements.withinMaxLength) getString(R.string.error_password_too_long) else null

        validateButtonState()
    }

    private fun validateButtonState() {
        val requirements = Validators.passwordRequirements(newPasswordInput.text.toString())
        changeButton.isEnabled = requirements.isValid
                && newPasswordInput.text.toString() == confirmPasswordInput.text.toString()
                && currentPasswordInput.text.toString().isNotBlank()
    }

    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        view.text = "${if (isMet) "\u2713" else "\u25CB"} $label"
        view.setTextColor(getColor(if (isMet) R.color.success else R.color.text_disabled))
    }
}
