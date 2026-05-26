package com.thedavelopers.eventqr.features.auth.login

import android.content.Intent
import android.text.InputType
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.Dashboard
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface LoginContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showEmailError(message: String?)
        fun showPasswordError(message: String?)
        fun showMessage(message: String)
        fun navigateToDashboard(role: String?)
        fun navigateToRegistration()
        fun navigateToForgotPassword()
    }
}

class LoginPresenter(
    private var view: LoginContract.View?,
    private val repository: AuthRepository,
) {
    private var loginJob: Job? = null

    fun attach(view: LoginContract.View) {
        this.view = view
    }

    fun detach() {
        loginJob?.cancel()
        view = null
    }

    fun submitLogin(email: String, password: String) {
        val emailValue = email.trim()
        val passwordValue = password.trim()

        var valid = true
        if (!Validators.isValidEmail(emailValue)) {
            view?.showEmailError("Enter a valid email address")
            valid = false
        } else {
            view?.showEmailError(null)
        }

        if (!Validators.isValidPassword(passwordValue)) {
            view?.showPasswordError("Password must be at least 6 characters")
            valid = false
        } else {
            view?.showPasswordError(null)
        }

        if (!valid) {
            return
        }

        view?.showLoading(true)
        loginJob = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.login(emailValue, passwordValue)) {
                is NetworkResult.Success -> {
                    val loginResponse = result.data
                    repository.storeSession(loginResponse)
                    repository.saveUserRole(loginResponse.role)
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: loginResponse.message ?: "Login successful")
                    view?.navigateToDashboard(loginResponse.role?.name ?: AccountRole.ATTENDEE.name)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun openRegistration() {
        view?.navigateToRegistration()
    }

    fun openForgotPassword() {
        view?.navigateToForgotPassword()
    }
}

open class LoginActivity : AppCompatActivity(), LoginContract.View {
    private lateinit var presenter: LoginPresenter
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var signInButton: Button
    private lateinit var registerButton: android.view.View
    private lateinit var forgotPasswordLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)

        presenter = LoginPresenter(this, AuthRepository(this))
        presenter.attach(this)

        emailInput = findViewById(R.id.edtEmail)
        passwordInput = findViewById(R.id.edtPassword)
        signInButton = findViewById(R.id.btnSignIn)
        registerButton = findViewById(R.id.btnRegister)
        forgotPasswordLink = findViewById(R.id.txtForgotPassword)
        configurePasswordToggle(passwordInput)

        signInButton.setOnClickListener {
            presenter.submitLogin(emailInput.text.toString(), passwordInput.text.toString())
        }

        registerButton.setOnClickListener {
            presenter.openRegistration()
        }

        forgotPasswordLink.setOnClickListener {
            presenter.openForgotPassword()
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        signInButton.isEnabled = !isLoading
        signInButton.text = if (isLoading) "Signing in..." else "Sign In"
    }

    override fun showEmailError(message: String?) {
        emailInput.error = message
    }

    override fun showPasswordError(message: String?) {
        passwordInput.error = message
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun navigateToDashboard(role: String?) {
        val normalizedRole = role?.uppercase()
        val destination = when (normalizedRole) {
            AccountRole.STAFF.name -> com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java
            AccountRole.ORGANIZER.name, AccountRole.ADMIN.name -> com.thedavelopers.eventqr.features.organizer.OrganizerDashboardActivity::class.java
            else -> Dashboard::class.java
        }
        startActivity(
            Intent(this, destination)
                .putExtra("extra_role", role)
        )
        finish()
    }

    override fun navigateToRegistration() {
        startActivity(Intent(this, com.thedavelopers.eventqr.Registration::class.java))
        finish()
    }

    override fun navigateToForgotPassword() {
        startActivity(Intent(this, com.thedavelopers.eventqr.ForgotPassword::class.java))
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
