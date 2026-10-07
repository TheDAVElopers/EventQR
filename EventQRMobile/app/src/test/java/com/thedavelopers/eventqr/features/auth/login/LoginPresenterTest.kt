package com.thedavelopers.eventqr.features.auth.login

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Presenter-level tests for the XML reverted Login flow. Only the synchronous
 * validation and navigation paths are exercised: `AuthRepository` is a final class
 * backed by a fixed [com.thedavelopers.eventqr.core.api.ApiConfig.BASE_URL], so the
 * network happy path is covered at a different layer (no unit seam exists).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class LoginPresenterTest {

    private lateinit var view: RecordingLoginView
    private lateinit var presenter: LoginPresenter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        view = RecordingLoginView()
        presenter = LoginPresenter(view, AuthRepository(context))
        presenter.attach(view)
    }

    @Test
    fun submitLogin_invalidEmail_showsErrorWithoutLoading() {
        presenter.submitLogin("bad-email", "password123")

        assertEquals(listOf("Enter a valid email address"), view.emailErrors)
        assertEquals(listOf(null), view.passwordErrors)
        assertTrue(view.loadingStates.isEmpty())
        assertTrue(view.messages.isEmpty())
    }

    @Test
    fun submitLogin_invalidPassword_showsErrorWithoutLoading() {
        presenter.submitLogin("user@example.com", "123")

        assertEquals(listOf(null), view.emailErrors)
        assertEquals(listOf("Password must be at least 8 characters"), view.passwordErrors)
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitLogin_bothInvalid_showsBothErrors() {
        presenter.submitLogin("", "")

        assertEquals(listOf("Enter a valid email address"), view.emailErrors)
        assertEquals(listOf("Password must be at least 8 characters"), view.passwordErrors)
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitLogin_validFieldClearsOnlyItsOwnError() {
        // Email alone valid: email error cleared, password still reported.
        presenter.submitLogin("user@example.com", "123")

        assertEquals(listOf(null), view.emailErrors)
        assertEquals(listOf("Password must be at least 8 characters"), view.passwordErrors)
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitLogin_trimsBeforeValidation() {
        presenter.submitLogin("  user@example.com  ", "  short  ")

        assertEquals(listOf(null), view.emailErrors)
        assertEquals(listOf("Password must be at least 8 characters"), view.passwordErrors)
    }

    @Test
    fun openRegistration_navigatesToRegistration() {
        presenter.openRegistration()

        assertEquals(1, view.registrationCount)
        assertEquals(0, view.forgotPasswordCount)
    }

    @Test
    fun openForgotPassword_navigatesToForgotPassword() {
        presenter.openForgotPassword()

        assertEquals(1, view.forgotPasswordCount)
        assertEquals(0, view.registrationCount)
    }

    @Test
    fun detach_stopsNotifyingView() {
        presenter.detach()

        presenter.submitLogin("bad-email", "password123")
        presenter.openRegistration()

        assertTrue(view.emailErrors.isEmpty())
        assertEquals(0, view.registrationCount)
    }

    private class RecordingLoginView : LoginContract.View {
        val loadingStates = mutableListOf<Boolean>()
        val emailErrors = mutableListOf<String?>()
        val passwordErrors = mutableListOf<String?>()
        val messages = mutableListOf<String>()
        val dashboardRoles = mutableListOf<String?>()
        var registrationCount = 0
        var forgotPasswordCount = 0

        override fun showLoading(isLoading: Boolean) {
            loadingStates += isLoading
        }

        override fun showEmailError(message: String?) {
            emailErrors += message
        }

        override fun showPasswordError(message: String?) {
            passwordErrors += message
        }

        override fun showMessage(message: String) {
            messages += message
        }

        override fun navigateToDashboard(role: String?) {
            dashboardRoles += role
        }

        override fun navigateToRegistration() {
            registrationCount++
        }

        override fun navigateToForgotPassword() {
            forgotPasswordCount++
        }
    }
}