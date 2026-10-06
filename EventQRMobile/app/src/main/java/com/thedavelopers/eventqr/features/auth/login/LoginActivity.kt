package com.thedavelopers.eventqr.features.auth.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.forgotpassword.ForgotPasswordActivity
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow

open class LoginActivity : AppCompatActivity(), LoginContract.View {
    private lateinit var presenter: LoginPresenter

    private val email = MutableStateFlow("")
    private val password = MutableStateFlow("")
    private val emailError = MutableStateFlow<String?>(null)
    private val passwordError = MutableStateFlow<String?>(null)
    private val isLoading = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter = LoginPresenter(this, AuthRepository(this))
        presenter.attach(this)

        setContent {
            EventQrTheme {
                LoginScreen(
                    email = email.collectAsStateWithLifecycle().value,
                    onEmailChange = { email.value = it },
                    password = password.collectAsStateWithLifecycle().value,
                    onPasswordChange = { password.value = it },
                    emailError = emailError.collectAsStateWithLifecycle().value,
                    passwordError = passwordError.collectAsStateWithLifecycle().value,
                    isLoading = isLoading.collectAsStateWithLifecycle().value,
                    onSignIn = { presenter.submitLogin(email.value, password.value) },
                    onRegister = { presenter.openRegistration() },
                    onForgotPassword = { presenter.openForgotPassword() },
                )
            }
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        this.isLoading.value = isLoading
    }

    override fun showEmailError(message: String?) {
        emailError.value = message
    }

    override fun showPasswordError(message: String?) {
        passwordError.value = message
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun navigateToDashboard(role: String?) {
        val normalizedRole = RoleMapper.normalizeRole(role)
        val destination = when (normalizedRole) {
            AccountRole.STAFF.name -> com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java
            AccountRole.ORGANIZER.name ->
                com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java
            AccountRole.ADMIN.name, AccountRole.SUPER_ADMIN.name ->
                com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java
            AccountRole.ATTENDEE.name, AccountRole.USER.name -> DashboardActivity::class.java
            "" -> {
                showMessage("Unable to determine account role")
                return
            }
            else -> {
                showMessage("Unsupported account role: $normalizedRole")
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
}
