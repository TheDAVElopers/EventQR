package com.thedavelopers.eventqr.features.auth.register

import com.thedavelopers.eventqr.core.util.UiStrings
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
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
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.terms.TermsLink
import com.thedavelopers.eventqr.ui.icons.FieldIcons
import com.thedavelopers.eventqr.ui.icons.RuleMarks

open class RegistrationActivity : AppCompatActivity(), RegistrationContract.View {
    private lateinit var presenter: RegistrationPresenter
    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var termsCheckBox: CheckBox
    private lateinit var registerButton: Button
    private lateinit var formError: AuthErrorStrip
    private val fieldErrors = AuthFieldErrors()
    private lateinit var signInButton: android.view.View
    private lateinit var passwordLengthRequirement: TextView
    private lateinit var passwordCapitalRequirement: TextView
    private lateinit var passwordLowercaseRequirement: TextView
    private lateinit var passwordSpecialRequirement: TextView
    private lateinit var passwordNumberRequirement: TextView
    private lateinit var requirementsLayout: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Edge to edge: the bar's fill runs behind the status bar and the sticky footer's fill
        // behind the navigation bar, while their content stays inside the safe area.
        enableEdgeToEdge()
        ScreenInsets.padForStatusBar(findViewById(R.id.authTopBar))
        Wordmark.applyBarRules(this)
        ScreenInsets.padForNavigationBar(findViewById(R.id.authFooter))

        presenter = RegistrationPresenter(this, AuthRepository(this), UiStrings(this))
        firstNameInput = findViewById(R.id.edtFirstName)
        lastNameInput = findViewById(R.id.edtLastName)
        emailInput = findViewById(R.id.edtEmail)
        phoneInput = findViewById(R.id.edtPhoneNumber)
        passwordInput = findViewById(R.id.edtPassword)
        confirmPasswordInput = findViewById(R.id.edtConfirmPassword)
        termsCheckBox = findViewById(R.id.chkTerms)
        registerButton = findViewById(R.id.btnRegister)
        // No Retry on this screen, so the strip has no actions to restore.
        formError = AuthErrorStrip(findViewById(R.id.errorRegister))
        // Registered in the form's top-to-bottom order: that order decides which error takes focus.
        fieldErrors.register(FIELD_FIRST_NAME, firstNameInput, findViewById(R.id.txtFirstNameError))
        fieldErrors.register(FIELD_LAST_NAME, lastNameInput, findViewById(R.id.txtLastNameError))
        fieldErrors.register(FIELD_EMAIL, emailInput, findViewById(R.id.txtEmailError))
        fieldErrors.register(FIELD_PHONE, phoneInput, findViewById(R.id.txtPhoneError))
        fieldErrors.register(FIELD_PASSWORD, passwordInput, findViewById(R.id.txtPasswordError))
        fieldErrors.register(FIELD_CONFIRM_PASSWORD, confirmPasswordInput, findViewById(R.id.txtConfirmPasswordError))
        signInButton = findViewById(R.id.btnSignIn)
        passwordLengthRequirement = findViewById(R.id.txtPasswordLengthRequirement)
        passwordCapitalRequirement = findViewById(R.id.txtPasswordCapitalRequirement)
        passwordLowercaseRequirement = findViewById(R.id.txtPasswordLowercaseRequirement)
        passwordSpecialRequirement = findViewById(R.id.txtPasswordSpecialRequirement)
        passwordNumberRequirement = findViewById(R.id.txtPasswordNumberRequirement)
        requirementsLayout = findViewById(R.id.layoutPasswordRequirements)
        presenter.attach(this)

        FieldIcons.apply(emailInput)
        // The phone glyph sits on the "+63" prefix inside the shared field box, not on the EditText.
        FieldIcons.apply(findViewById(R.id.txtPhonePrefix))
        FieldIcons.apply(passwordInput)
        FieldIcons.apply(confirmPasswordInput)
        configurePhoneInput()
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordRequirements(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updatePasswordRequirements(passwordInput.text.toString())

        // Terms gating (TestFlow REG-9/REG-10): Create Account stays disabled
        // until the password meets all requirements AND the terms checkbox is checked.
        termsCheckBox.setOnCheckedChangeListener { _, _ ->
            updateRegisterButtonState()
        }
        TermsLink.apply(termsCheckBox, getString(R.string.terms_link_signup))

        registerButton.setOnClickListener {
            formError.clear()
            // EventQR - UI validation deviation beyond SRS UC-01 field spec
            // Field holds 10 local digits under a fixed "+63" prefix; the full E.164 value
            // (+63 + digits, e.g. +639171234567) is assembled only here at submit time.
            val phoneDigits = phoneInput.text.toString()
            if (phoneDigits.length != 10) {
                fieldErrors.raise(FIELD_PHONE, getString(R.string.error_invalid_phone_prefixed))
                return@setOnClickListener
            }
            presenter.submitRegistration(
                firstNameInput.text.toString(),
                lastNameInput.text.toString(),
                emailInput.text.toString(),
                "+63$phoneDigits",
                passwordInput.text.toString(),
                confirmPasswordInput.text.toString(),
            )
        }

        signInButton.setOnClickListener {
            navigateToSignIn()
        }
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
        registerButton.isEnabled = !isLoading && isRegistrationFormValid()
        registerButton.text = getString(if (isLoading) R.string.registration_creating_account else R.string.auth_create_account)
    }

    override fun showFieldError(field: String, message: String?) {
        // Each field's helper line carries the message and bg_ui_field draws the 2dp error stroke.
        when (field) {
            "firstName" -> fieldErrors.raise(FIELD_FIRST_NAME, message)
            "lastName" -> fieldErrors.raise(FIELD_LAST_NAME, message)
            "fullName" -> {
                fieldErrors.raise(FIELD_FIRST_NAME, message)
                fieldErrors.raise(FIELD_LAST_NAME, message)
            }
            "email" -> fieldErrors.raise(FIELD_EMAIL, message)
            "phone" -> fieldErrors.raise(
                FIELD_PHONE,
                if (message == Validators.PHONE_ERROR) getString(R.string.error_invalid_phone_prefixed) else message
            )
            "password" -> fieldErrors.raise(
                FIELD_PASSWORD,
                if (message == Validators.PASSWORD_TOO_LONG_ERROR) getString(R.string.error_password_too_long) else message
            )
            "confirmPassword" -> fieldErrors.raise(FIELD_CONFIRM_PASSWORD, message)
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /** No Retry: account creation is not safe to repeat when the first answer was lost; the user re-presses Create account. */
    override fun showFormError(message: String) {
        formError.show(message, retryAction = null)
    }

    override fun showServerFieldError(field: String, message: String) {
        // The field error takes focus and is announced by the shared helper.
        showFieldError(field, message)
    }

    /** Replaces Sign Up with Sign In so the two never stack; Back from Sign In returns to Landing. */
    override fun navigateToSignIn() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun updatePasswordRequirements(password: String) {
        if (password.isEmpty()) {
            requirementsLayout.visibility = android.view.View.GONE
            updateRegisterButtonState()
            return
        }
        requirementsLayout.visibility = android.view.View.VISIBLE

        val requirements = Validators.passwordRequirements(password)
        updateRequirement(passwordLengthRequirement, getString(R.string.password_req_length), requirements.hasMinLength)
        updateRequirement(passwordCapitalRequirement, getString(R.string.password_req_uppercase), requirements.hasCapital)
        updateRequirement(passwordLowercaseRequirement, getString(R.string.password_req_lowercase), requirements.hasLowercase)
        updateRequirement(passwordNumberRequirement, getString(R.string.password_req_number), requirements.hasNumber)
        updateRequirement(passwordSpecialRequirement, getString(R.string.password_req_special), requirements.hasSpecial)
        // Derived from the text on every keystroke: live, so the edit watcher leaves it to this check.
        val tooLong = getString(R.string.error_password_too_long)
        if (!requirements.withinMaxLength) {
            fieldErrors.raise(FIELD_PASSWORD, tooLong, live = true)
        } else if (fieldErrors.message(FIELD_PASSWORD) == tooLong) {
            fieldErrors.set(FIELD_PASSWORD, null)
        }

        updateRegisterButtonState()
    }

    private fun isRegistrationFormValid(): Boolean =
        Validators.isValidSignUpPassword(passwordInput.text.toString()) && termsCheckBox.isChecked

    private fun updateRegisterButtonState() {
        registerButton.isEnabled = isRegistrationFormValid()
    }

    /** One checklist row (§6.1): the 16dp tick slot carries the state, never colour alone. */
    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        RuleMarks.apply(view, label, isMet)
    }

    // EventQR - UI validation deviation beyond SRS UC-01 field spec
    // Phone format enforcement: numeric-only entry (inputType=number). No raw maxLength
    // cap — pasted full numbers ("0917...", "0063...", "63917...", "+63917...") must reach
    // the normalizer intact. Normalization strips +63/0063/0 prefixes on the full input and
    // caps the national digits at 10 AFTER stripping.
    private fun configurePhoneInput() {
        phoneInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(editable: Editable?) {
                val current = editable ?: return
                val normalized = normalizePhoneDigits(current.toString())
                if (normalized != current.toString()) {
                    current.replace(0, current.length, normalized)
                }
            }
        })
    }

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

    private companion object {
        const val FIELD_FIRST_NAME = "firstName"
        const val FIELD_LAST_NAME = "lastName"
        const val FIELD_EMAIL = "email"
        const val FIELD_PHONE = "phone"
        const val FIELD_PASSWORD = "password"
        const val FIELD_CONFIRM_PASSWORD = "confirmPassword"
    }
}
