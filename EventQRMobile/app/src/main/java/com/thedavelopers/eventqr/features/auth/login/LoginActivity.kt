package com.thedavelopers.eventqr.features.auth.login

import com.thedavelopers.eventqr.core.util.UiStrings
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.RememberedEmailStore
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.forgotpassword.ForgotPasswordActivity
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity

open class LoginActivity : AppCompatActivity(), LoginContract.View {
    private lateinit var presenter: LoginPresenter
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var signInButton: Button
    private lateinit var registerButton: android.view.View
    private lateinit var forgotPasswordLink: TextView
    private lateinit var rememberEmailCheckBox: CheckBox
    private lateinit var rememberedEmailStore: RememberedEmailStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)

        presenter = LoginPresenter(this, AuthRepository(this), UiStrings(this), getString(R.string.login_account_disabled), getString(R.string.login_rate_limited), getString(R.string.login_invalid_request), getString(R.string.login_check_spaces)) { minutes ->
            resources.getQuantityString(R.plurals.login_rate_limited_minutes, minutes, minutes)
        }
        emailInput = findViewById(R.id.edtEmail)
        passwordInput = findViewById(R.id.edtPassword)
        signInButton = findViewById(R.id.btnSignIn)
        registerButton = findViewById(R.id.btnRegister)
        forgotPasswordLink = findViewById(R.id.txtForgotPassword)
        rememberEmailCheckBox = findViewById(R.id.chkRememberMe)
        rememberedEmailStore = RememberedEmailStore.create(this)
        rememberedEmailStore.get()?.let { remembered ->
            emailInput.setText(remembered)
            rememberEmailCheckBox.isChecked = true
            passwordInput.requestFocus()
        }
        presenter.attach(this)
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
        signInButton.text = getString(if (isLoading) R.string.login_signing_in else R.string.common_sign_in)
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

    override fun onLoginSucceeded(email: String) {
        if (rememberEmailCheckBox.isChecked) rememberedEmailStore.save(email) else rememberedEmailStore.clear()
    }

    override fun navigateToDashboard(role: String?) {
        val normalizedRole = RoleMapper.normalizeRole(role)
        val destination = when (normalizedRole) {
            AccountRole.STAFF.name -> com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java
            AccountRole.ORGANIZER.name ->
                com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java
            AccountRole.ADMIN.name, AccountRole.SUPER_ADMIN.name ->
                com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java
            AccountRole.ATTENDEE.name -> DashboardActivity::class.java
            "" -> {
                showMessage(getString(R.string.login_unable_to_determine_account_role))
                return
            }
            else -> {
                showMessage(getString(R.string.login_unsupported_role, normalizedRole))
                return
            }
        }
        startActivity(
            Intent(this, destination)
                .putExtra("extra_role", normalizedRole)
        )
        finish()
    }

    override fun navigateToRegistration() {
        startActivity(Intent(this, RegistrationActivity::class.java))
        finish()
    }

    override fun navigateToForgotPassword() {
        startActivity(Intent(this, ForgotPasswordActivity::class.java))
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
