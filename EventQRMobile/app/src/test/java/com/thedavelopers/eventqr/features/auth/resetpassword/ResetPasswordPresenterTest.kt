package com.thedavelopers.eventqr.features.auth.resetpassword

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ResetPasswordPresenterTest {

    private class RecordingView : ResetPasswordContract.View {
        val passwordErrors = mutableListOf<String?>()
        val codeErrors = mutableListOf<String?>()
        val loading = mutableListOf<Boolean>()
        val emails = mutableListOf<String>()
        var loginCount = 0
        override fun showLoading(isLoading: Boolean) { loading += isLoading }
        override fun showEmail(email: String) { emails += email }
        override fun showCodeError(message: String?) { codeErrors += message }
        override fun showPasswordError(message: String?) { passwordErrors += message }
        override fun showConfirmPasswordError(message: String?) = Unit
        override fun showMessage(message: String) = Unit
        override fun showResendCooldown(secondsLeft: Int) = Unit
        override fun navigateToLogin() { loginCount++ }
    }

    private fun presenterWith(view: RecordingView): ResetPasswordPresenter {
        val presenter = ResetPasswordPresenter()
        presenter.attach(view, ApplicationProvider.getApplicationContext())
        // The email normally arrives through start(); set it directly to skip the cooldown coroutine.
        presenter.email = "user@example.com"
        return presenter
    }

    @Test
    fun submitReset_noLowercase_rejectedWithoutNetworkCall() {
        val view = RecordingView()
        val presenter = presenterWith(view)

        presenter.submitReset("123456", "PASSWORD1!", "PASSWORD1!")

        assertEquals(
            "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number, and a special character",
            view.passwordErrors.last(),
        )
        assertTrue(view.loading.isEmpty())
    }

    @Test
    fun submitReset_codeNotSixDigits_rejectedWithoutNetworkCall() {
        val view = RecordingView()
        val presenter = presenterWith(view)

        presenter.submitReset("12a456", "Password1!", "Password1!")

        assertEquals("Enter the 6-digit code from your email", view.codeErrors.last())
        assertTrue(view.loading.isEmpty())
    }

    @Test
    fun submitReset_missingEmail_navigatesToLogin() {
        val view = RecordingView()
        val presenter = ResetPasswordPresenter()
        presenter.attach(view, ApplicationProvider.getApplicationContext())

        presenter.submitReset("123456", "Password1!", "Password1!")

        assertEquals(1, view.loginCount)
        assertTrue(view.loading.isEmpty())
    }

    @Test
    fun start_blankEmail_navigatesToLogin() {
        val view = RecordingView()
        val presenter = ResetPasswordPresenter()
        presenter.attach(view, ApplicationProvider.getApplicationContext())

        presenter.start(null)

        assertEquals(1, view.loginCount)
        assertTrue(view.emails.isEmpty())
    }
}
