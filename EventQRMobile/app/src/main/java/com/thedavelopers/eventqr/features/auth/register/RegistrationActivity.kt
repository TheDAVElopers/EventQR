package com.thedavelopers.eventqr.features.auth.register

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.login.LoginActivity

open class RegistrationActivity : AppCompatActivity(), RegistrationContract.View {
    private lateinit var presenter: RegistrationPresenter
    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var registerButton: Button
    private lateinit var signInButton: android.view.View
    private lateinit var passwordLengthRequirement: TextView
    private lateinit var passwordCapitalRequirement: TextView
    private lateinit var passwordSpecialRequirement: TextView
    private lateinit var passwordNumberRequirement: TextView
    private lateinit var passwordStrengthText: TextView
    private lateinit var requirementsLayout: android.view.View
    private lateinit var strengthBars: List<android.view.View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        presenter = RegistrationPresenter(this, AuthRepository(this))
        presenter.attach(this)

        firstNameInput = findViewById(R.id.edtFirstName)
        lastNameInput = findViewById(R.id.edtLastName)
        emailInput = findViewById(R.id.edtEmail)
        phoneInput = findViewById(R.id.edtPhoneNumber)
        passwordInput = findViewById(R.id.edtPassword)
        confirmPasswordInput = findViewById(R.id.edtConfirmPassword)
        registerButton = findViewById(R.id.btnRegister)
        signInButton = findViewById(R.id.btnSignIn)
        passwordLengthRequirement = findViewById(R.id.txtPasswordLengthRequirement)
        passwordCapitalRequirement = findViewById(R.id.txtPasswordCapitalRequirement)
        passwordSpecialRequirement = findViewById(R.id.txtPasswordSpecialRequirement)
        passwordNumberRequirement = findViewById(R.id.txtPasswordNumberRequirement)
        passwordStrengthText = findViewById(R.id.txtPasswordStrength)
        requirementsLayout = findViewById(R.id.layoutPasswordRequirements)
        strengthBars = listOf(
            findViewById(R.id.viewStrength1),
            findViewById(R.id.viewStrength2),
            findViewById(R.id.viewStrength3),
            findViewById(R.id.viewStrength4)
        )

        configurePasswordToggle(passwordInput)
        configurePasswordToggle(confirmPasswordInput)
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordRequirements(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updatePasswordRequirements(passwordInput.text.toString())

        registerButton.setOnClickListener {
            presenter.submitRegistration(
                firstNameInput.text.toString(),
                lastNameInput.text.toString(),
                emailInput.text.toString(),
                phoneInput.text.toString(),
                passwordInput.text.toString(),
                confirmPasswordInput.text.toString(),
            )
        }

        signInButton.setOnClickListener {
            navigateToSignIn()
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        registerButton.isEnabled = !isLoading && Validators.isValidSignUpPassword(passwordInput.text.toString())
        registerButton.text = if (isLoading) "Creating account..." else "Create Account"
    }

    override fun showFieldError(field: String, message: String?) {
        when (field) {
            "firstName" -> firstNameInput.error = message
            "lastName" -> lastNameInput.error = message
            "email" -> emailInput.error = message
            "phone" -> phoneInput.error = message
            "password" -> passwordInput.error = message
            "confirmPassword" -> confirmPasswordInput.error = message
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun navigateToSignIn() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun updatePasswordRequirements(password: String) {
        if (password.isEmpty()) {
            requirementsLayout.visibility = android.view.View.GONE
            registerButton.isEnabled = false
            return
        }
        requirementsLayout.visibility = android.view.View.VISIBLE

        val requirements = Validators.passwordRequirements(password)
        updateRequirement(passwordLengthRequirement, "At least 8 characters", requirements.hasMinLength)
        updateRequirement(passwordCapitalRequirement, "One uppercase letter", requirements.hasCapital)
        updateRequirement(passwordNumberRequirement, "One number", requirements.hasNumber)
        updateRequirement(passwordSpecialRequirement, "One special character", requirements.hasSpecial)

        val metCount = listOf(
            requirements.hasMinLength,
            requirements.hasCapital,
            requirements.hasNumber,
            requirements.hasSpecial
        ).count { it }

        updateStrengthUI(metCount)
        registerButton.isEnabled = requirements.isValid
    }

    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        view.text = "${if (isMet) "✓" else "○"} $label"
        view.setTextColor(getColor(if (isMet) R.color.eventqr_success else R.color.eventqr_muted))
    }

    private fun updateStrengthUI(metCount: Int) {
        val (colorRes, label) = when (metCount) {
            0 -> R.color.eventqr_muted to ""
            1 -> R.color.eventqr_error to "Weak"
            2 -> R.color.eventqr_warning to "Fair"
            3 -> R.color.eventqr_info to "Good"
            4 -> R.color.eventqr_success to "Strong"
            else -> R.color.eventqr_muted to ""
        }

        passwordStrengthText.text = label
        passwordStrengthText.setTextColor(if (metCount > 0) getColor(colorRes) else getColor(R.color.eventqr_muted))

        strengthBars.forEachIndexed { index, view ->
            view.background.mutate().setTint(
                if (index < metCount) getColor(colorRes) else getColor(R.color.eventqr_border)
            )
        }
    }

    private fun configurePasswordToggle(input: EditText) {
        input.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP && event.rawX >= input.right - input.compoundPaddingEnd) {
                val isVisible = input.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                if (isVisible) {
                    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    input.setCompoundDrawablesWithIntrinsicBounds(
                        input.compoundDrawables[0],
                        null,
                        ContextCompat.getDrawable(this, R.drawable.ic_visibility_on),
                        null
                    )
                } else {
                    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    input.setCompoundDrawablesWithIntrinsicBounds(
                        input.compoundDrawables[0],
                        null,
                        ContextCompat.getDrawable(this, R.drawable.ic_visibility_off),
                        null
                    )
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