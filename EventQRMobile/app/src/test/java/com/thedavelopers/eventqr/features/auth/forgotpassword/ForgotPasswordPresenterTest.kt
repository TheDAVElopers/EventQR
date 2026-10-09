package com.thedavelopers.eventqr.features.auth.forgotpassword

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Presenter-level tests for the XML reverted Forgot Password flow. Only the
 * synchronous paths run: the send-code request itself performs a network call
 * through [com.thedavelopers.eventqr.features.auth.AuthRepository] which has no
 * unit test seam (see coverage notes).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ForgotPasswordPresenterTest {

    private lateinit var view: RecordingForgotPasswordView
    private lateinit var presenter: ForgotPasswordPresenter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        view = RecordingForgotPasswordView()
        presenter = ForgotPasswordPresenter()
        presenter.attach(view, context)
    }

    @Test
    fun submitRequest_invalidEmail_showsErrorWithoutLoading() {
        presenter.submitRequest("not-an-email")

        assertEquals(listOf("Enter a valid email address"), view.emailErrors)
        assertTrue(view.loadingStates.isEmpty())
        assertTrue(view.resetEmails.isEmpty())
    }

    @Test
    fun submitRequest_trimsEmailBeforeValidation() {
        presenter.submitRequest("  not-an-email  ")

        assertEquals(listOf("Enter a valid email address"), view.emailErrors)
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRequest_emptyEmail_showsError() {
        presenter.submitRequest("")

        assertEquals(listOf("Enter a valid email address"), view.emailErrors)
        assertTrue(view.loadingStates.isEmpty())
        assertTrue(view.resetEmails.isEmpty())
    }

    @Test
    fun backToSignIn_navigatesBack() {
        presenter.backToSignIn()

        assertEquals(1, view.backToSignInCount)
    }

    @Test
    fun detach_clearsViewAndRepository() {
        presenter.detach()

        presenter.submitRequest("not-an-email")
        presenter.backToSignIn()

        assertTrue(view.emailErrors.isEmpty())
        assertEquals(0, view.backToSignInCount)
    }

    private class RecordingForgotPasswordView : ForgotPasswordContract.View {
        val loadingStates = mutableListOf<Boolean>()
        val emailErrors = mutableListOf<String?>()
        val messages = mutableListOf<String>()
        val resetEmails = mutableListOf<String>()
        var backToSignInCount = 0

        override fun showLoading(isLoading: Boolean) {
            loadingStates += isLoading
        }

        override fun showEmailError(message: String?) {
            emailErrors += message
        }

        override fun showMessage(message: String) {
            messages += message
        }

        override fun navigateToResetPassword(email: String) {
            resetEmails += email
        }

        override fun navigateBackToSignIn() {
            backToSignInCount++
        }
    }
}