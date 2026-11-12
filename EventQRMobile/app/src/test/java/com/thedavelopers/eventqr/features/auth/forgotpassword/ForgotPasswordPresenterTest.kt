package com.thedavelopers.eventqr.features.auth.forgotpassword

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.robolectric.Shadows.shadowOf
import retrofit2.HttpException
import retrofit2.Response
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

    // ---- §5.16: a failed send is one strip sentence; only a transient failure offers Retry

    private class FakeRepo(private val result: NetworkResult<Unit>) :
        AuthRepository(ApplicationProvider.getApplicationContext()) {
        override suspend fun forgotPassword(email: String): NetworkResult<Unit> = result
    }

    private fun httpFailure(code: Int, body: String = ""): NetworkResult<Unit> = runBlocking {
        safeApiCall<Unit> { throw HttpException(Response.error<Any>(code, body.toResponseBody())) }
    }

    private fun string(id: Int) =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun submitWith(result: NetworkResult<Unit>) {
        presenter.repository = FakeRepo(result)
        presenter.submitRequest("user@example.com")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun submitRequest_noConnection_showsTheNetworkSentenceWithRetry() {
        submitWith(NetworkResult.Error("Network problem", java.io.IOException()))

        assertEquals(listOf(string(R.string.ui_auth_error_network) to true), view.formErrors)
        assertTrue("never claims a code is on its way", view.resetEmails.isEmpty())
        assertEquals(listOf(true, false), view.loadingStates)
    }

    @Test
    fun submitRequest_serverError500_showsAFixedSentenceWithRetry() {
        submitWith(httpFailure(500, "{\"message\":\"java.lang.IllegalStateException: boom\"}"))

        assertEquals(listOf(string(R.string.ui_auth_forgot_failed) to true), view.formErrors)
        assertTrue(view.resetEmails.isEmpty())
    }

    @Test
    fun submitRequest_rateLimited429_hasNoRetry() {
        submitWith(httpFailure(429))

        assertEquals(listOf(string(R.string.forgot_password_rate_limited) to false), view.formErrors)
        assertTrue(view.resetEmails.isEmpty())
    }

    @Test
    fun submitRequest_unknownAccount4xx_staysNeutralAndRevealsNothing() {
        submitWith(httpFailure(404, "{\"message\":\"No account for that email\"}"))

        assertTrue(view.formErrors.isEmpty())
        assertEquals(listOf("user@example.com"), view.resetEmails)
    }

    @Test
    fun submitRequest_unlistedClientErrors_neverOfferRetry() {
        for (code in listOf(405, 415, 422)) {
            val recording = RecordingForgotPasswordView()
            presenter.attach(recording, ApplicationProvider.getApplicationContext())
            presenter.repository = FakeRepo(httpFailure(code))

            presenter.submitRequest("user@example.com")
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue("HTTP $code shows no strip, so no Retry", recording.formErrors.isEmpty())
            assertEquals("HTTP $code", listOf("user@example.com"), recording.resetEmails)
        }
    }

    private class RecordingForgotPasswordView : ForgotPasswordContract.View {
        val loadingStates = mutableListOf<Boolean>()
        val emailErrors = mutableListOf<String?>()
        val formErrors = mutableListOf<Pair<String, Boolean>>()
        val resetEmails = mutableListOf<String>()
        var backToSignInCount = 0

        override fun showLoading(isLoading: Boolean) {
            loadingStates += isLoading
        }

        override fun showEmailError(message: String?) {
            emailErrors += message
        }

        override fun showFormError(message: String, retryable: Boolean) {
            formErrors += message to retryable
        }

        override fun navigateToResetPassword(email: String) {
            resetEmails += email
        }

        override fun navigateBackToSignIn() {
            backToSignInCount++
        }
    }
}