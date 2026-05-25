package com.thedavelopers.eventqr.features.auth.register

import android.content.Intent
import android.os.Bundle
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
        if (!Validators.isValidPassword(passwordValue)) {
            view?.showFieldError("password", "Password must be at least 6 characters")
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

        registerButton.setOnClickListener {
            presenter.submitRegistration(
                firstNameInput.text.toString(),
                lastNameInput.text.toString(),
                emailInput.text.toString(),
                phoneInput.text.toString(),
                passwordInput.text.toString(),
                passwordInput.text.toString(),
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
        registerButton.isEnabled = !isLoading
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
}
