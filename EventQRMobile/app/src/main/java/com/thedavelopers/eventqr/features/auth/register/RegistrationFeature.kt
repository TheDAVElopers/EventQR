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
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.SignIn
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface RegistrationContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showFieldError(field: String, message: String?)
        fun showMessage(message: String)
        fun navigateToSignIn()
    }
}

class RegistrationPresenter(
    private var view: RegistrationContract.View?,
    private val repository: AuthRepository,
) {
    private var registrationJob: Job? = null

    fun attach(view: RegistrationContract.View) {
        this.view = view
    }

    fun detach() {
        registrationJob?.cancel()
        view = null
    }

    fun submitRegistration(
        firstName: String,
        lastName: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String,
    ) {
        val firstNameValue = firstName.trim()
        val lastNameValue = lastName.trim()
        val emailValue = email.trim()
        val phoneValue = phoneNumber.trim()
        val passwordValue = password.trim()
        val confirmValue = confirmPassword.trim()

        var valid = true
        if (!Validators.isNonEmpty(firstNameValue)) {
            view?.showFieldError("firstName", "First name is required")
            valid = false
        } else {
            view?.showFieldError("firstName", null)
        }
        if (!Validators.isNonEmpty(lastNameValue)) {
            view?.showFieldError("lastName", "Last name is required")
            valid = false
        } else {
            view?.showFieldError("lastName", null)
        }
        if (!Validators.isValidEmail(emailValue)) {
            view?.showFieldError("email", "Enter a valid email address")
            valid = false
        } else {
            view?.showFieldError("email", null)
        }
        if (!Validators.isValidPhoneNumber(phoneValue)) {
            view?.showFieldError("phone", "Enter a valid phone number")
            valid = false
        } else {
            view?.showFieldError("phone", null)
        }
        if (!Validators.isValidSignUpPassword(passwordValue)) {
            view?.showFieldError("password", "Password must meet all requirements")
            valid = false
        } else {
            view?.showFieldError("password", null)
        }
        if (passwordValue != confirmValue) {
            view?.showFieldError("confirmPassword", "Passwords do not match")
            valid = false
        } else {
            view?.showFieldError("confirmPassword", null)
        }

        if (!valid) {
            return
        }

        view?.showLoading(true)
        registrationJob = kotlinx.coroutines.MainScope().launch {
            val fullNameValue = listOf(firstNameValue, lastNameValue).filter { it.isNotBlank() }.joinToString(" ").trim()
            when (val result = repository.createUser(fullNameValue, emailValue, phoneValue, passwordValue, AccountRole.ATTENDEE)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: "Account created")
                    view?.navigateToSignIn()
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}

open class RegistrationActivity : AppCompatActivity(), RegistrationContract.View {
    private lateinit var presenter: RegistrationPresenter
    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var registerButton: Button
    private lateinit var signInButton: Button
    private lateinit var passwordLengthRequirement: TextView
    private lateinit var passwordCapitalRequirement: TextView
    private lateinit var passwordSpecialRequirement: TextView
    private lateinit var passwordSmallRequirement: TextView
    private lateinit var passwordNumberRequirement: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

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
        passwordSmallRequirement = findViewById(R.id.txtPasswordSmallRequirement)
        passwordNumberRequirement = findViewById(R.id.txtPasswordNumberRequirement)

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
        startActivity(Intent(this, SignIn::class.java))
        finish()
    }

    private fun updatePasswordRequirements(password: String) {
        val requirements = Validators.passwordRequirements(password)
        updateRequirement(passwordLengthRequirement, "8 characters long", requirements.hasMinLength)
        updateRequirement(passwordCapitalRequirement, "1 Capital", requirements.hasCapital)
        updateRequirement(passwordSpecialRequirement, "1 Special", requirements.hasSpecial)
        updateRequirement(passwordSmallRequirement, "1 Small", requirements.hasSmall)
        updateRequirement(passwordNumberRequirement, "1 Number", requirements.hasNumber)
        registerButton.isEnabled = requirements.isValid
    }

    private fun updateRequirement(view: TextView, label: String, isMet: Boolean) {
        view.text = "${if (isMet) "[x]" else "[ ]"} $label"
        view.setTextColor(getColor(if (isMet) R.color.eventqr_success else R.color.eventqr_muted))
    }

    private fun configurePasswordToggle(input: EditText) {
        input.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP && event.rawX >= input.right - input.compoundPaddingEnd) {
                val isHidden = input.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD
                input.inputType = if (isHidden) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
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
