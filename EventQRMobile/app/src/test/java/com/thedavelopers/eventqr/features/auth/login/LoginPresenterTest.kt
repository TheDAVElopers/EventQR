package com.thedavelopers.eventqr.features.auth.login

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
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
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

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

    private fun strings() = UiStrings(ApplicationProvider.getApplicationContext())

    @Test
    fun submitLogin_invalidEmail_messageComesFromResources() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        presenter.submitLogin("bad-email", "password123")

        assertEquals(listOf(context.getString(R.string.create_admin_account_enter_a_valid_email_address)), view.emailErrors)
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        view = RecordingLoginView()
        presenter = LoginPresenter(view, AuthRepository(context), strings(), DISABLED, RATE_LIMITED)
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
        presenter.submitLogin("user@example.com", "   ")

        assertEquals(listOf(null), view.emailErrors)
        assertEquals(listOf("Enter your password"), view.passwordErrors)
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitLogin_bothInvalid_showsBothErrors() {
        presenter.submitLogin("", "")

        assertEquals(listOf("Enter a valid email address"), view.emailErrors)
        assertEquals(listOf("Enter your password"), view.passwordErrors)
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitLogin_validFieldClearsOnlyItsOwnError() {
        // Email alone valid: email error cleared, password still reported.
        presenter.submitLogin("user@example.com", "   ")

        assertEquals(listOf(null), view.emailErrors)
        assertEquals(listOf("Enter your password"), view.passwordErrors)
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitLogin_trimsEmailButNotPassword() {
        // Blank (whitespace-only) password is rejected; a short non-blank one is not rejected on-device.
        presenter.submitLogin("  user@example.com  ", "   ")

        assertEquals(listOf(null), view.emailErrors)
        assertEquals(listOf("Enter your password"), view.passwordErrors)
    }

    @Test
    fun submitLogin_shortNonBlankPassword_reachesServer() {
        val repo = FakeAuthRepository { _, _ -> NetworkResult.Error("Network problem") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED)

        p.submitLogin("user@example.com", "abc")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("abc"), repo.passwords)
        assertEquals(listOf(null), view.passwordErrors)
    }

    @Test
    fun submitLogin_rejectedWithoutSurroundingSpaces_doesNotRetry() {
        val repo = FakeAuthRepository { _, _ -> NetworkResult.Error("Invalid credentials", unauthorized()) }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED)

        p.submitLogin("user@example.com", "secret")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("secret"), repo.passwords)
    }

    @Test
    fun submitLogin_networkErrorWithSpaces_doesNotRetry() {
        val repo = FakeAuthRepository { _, _ -> NetworkResult.Error("Network problem", java.io.IOException()) }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED)

        p.submitLogin("user@example.com", " secret ")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(" secret "), repo.passwords)
    }

    @Test
    fun submitLogin_disabledAccount403_showsParsedServerMessageAndDoesNotRetry() {
        val repo = FakeAuthRepository { _, _ -> httpFailure(403, """{"message":"Account suspended by admin"}""") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED)

        p.submitLogin("user@example.com", "  secret  ")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("  secret  "), repo.passwords)
        assertEquals(listOf("Account suspended by admin"), view.messages)
        assertEquals(listOf(true, false), view.loadingStates)
    }

    @Test
    fun submitLogin_disabledAccount403WithUnparseableBody_usesResourceFallback() {
        val repo = FakeAuthRepository { _, _ -> httpFailure(403, "<html>forbidden</html>") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED)

        p.submitLogin("user@example.com", "secret")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(DISABLED), view.messages)
    }

    @Test
    fun submitLogin_disabledAccount403WithBlankMessage_usesResourceFallback() {
        val repo = FakeAuthRepository { _, _ -> httpFailure(403, """{"message":"  "}""") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED)

        p.submitLogin("user@example.com", "secret")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(DISABLED), view.messages)
    }

    @Test
    fun submitLogin_badRequest400_showsFixedMessageNotRawServerText() {
        val repo = FakeAuthRepository { _, _ -> httpFailure(400, """{"message":"Password is too long"}""") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED, INVALID_REQUEST)

        p.submitLogin("user@example.com", "secret")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(INVALID_REQUEST), view.messages)
    }

    @Test
    fun submitLogin_badRequest400BlankMessage_showsFixedMessage() {
        val repo = FakeAuthRepository { _, _ -> httpFailure(400, """{"message":"  "}""") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED, INVALID_REQUEST)

        p.submitLogin("user@example.com", "secret")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(INVALID_REQUEST), view.messages)
    }

    @Test
    fun submitLogin_rejectedWithSurroundingSpaces_sendsOneRequestAndShowsTheSpacesHint() {
        val repo = FakeAuthRepository { _, _ -> httpFailure(401, """{"message":"Invalid email or password"}""") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED, INVALID_REQUEST, SPACES_HINT)

        p.submitLogin("user@example.com", " secret ")
        shadowOf(Looper.getMainLooper()).idle()

        // Exactly one attempt reaches the server (one rate-limit hit); the password is never altered.
        assertEquals(listOf(" secret "), repo.passwords)
        assertEquals(listOf(SPACES_HINT), view.messages)
    }

    @Test
    fun submitLogin_rejectedWithoutSurroundingSpaces_showsTheServerMessage() {
        val repo = FakeAuthRepository { _, _ -> httpFailure(401, """{"message":"Invalid email or password"}""") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED, INVALID_REQUEST, SPACES_HINT)

        p.submitLogin("user@example.com", "secret")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("Invalid email or password"), view.messages)
    }

    @Test
    fun submitLogin_success_reportsTheTrimmedEmailToTheView() {
        val response = LoginResponse(
            accessToken = "access",
            userId = java.util.UUID.randomUUID(),
            email = "user@example.com",
            fullName = "User",
            role = com.thedavelopers.eventqr.core.api.dto.AccountRole.ATTENDEE,
        )
        val repo = FakeAuthRepository { _, _ -> NetworkResult.Success(response) }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED, INVALID_REQUEST, SPACES_HINT)

        p.submitLogin("  user@example.com ", "secret")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("user@example.com"), view.succeededEmails)
    }

    @Test
    fun submitLogin_failure_doesNotReportSuccess() {
        val repo = FakeAuthRepository { _, _ -> httpFailure(401, """{"message":"Invalid email or password"}""") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED, INVALID_REQUEST, SPACES_HINT)

        p.submitLogin("user@example.com", "wrong")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), view.succeededEmails)
    }

    @Test
    fun submitLogin_firstAttemptRateLimited_doesNotRetry() {
        val repo = FakeAuthRepository { _, _ -> httpFailure(429, "") }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED)

        p.submitLogin("user@example.com", " secret ")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(" secret "), repo.passwords)
        assertEquals(listOf(RATE_LIMITED), view.messages)
    }

    @Test
    fun submitLogin_429WithRetryAfter_roundsUpToMinutes() {
        fun msgFor(seconds: Long): String {
            val v = RecordingLoginView()
            val repo = FakeAuthRepository { _, _ -> NetworkResult.Error("x", rateLimit(), null, seconds) }
            val p = LoginPresenter(v, repo, strings(), DISABLED, RATE_LIMITED) { "mins=$it" }
            p.submitLogin("user@example.com", "secret")
            shadowOf(Looper.getMainLooper()).idle()
            return v.messages.single()
        }
        assertEquals("mins=1", msgFor(1))
        assertEquals("mins=1", msgFor(60))
        assertEquals("mins=2", msgFor(61))
        assertEquals("mins=15", msgFor(900))
    }

    @Test
    fun submitLogin_429WithoutRetryAfter_usesGenericMessage() {
        val repo = FakeAuthRepository { _, _ -> NetworkResult.Error("x", rateLimit(), null, null) }
        val p = LoginPresenter(view, repo, strings(), DISABLED, RATE_LIMITED) { "mins=$it" }

        p.submitLogin("user@example.com", "secret")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(RATE_LIMITED), view.messages)
    }

    private fun rateLimit() = HttpException(Response.error<Any>(429, "".toResponseBody()))

    /** Built through the real safeApiCall (so serverMessage is parsed) but resolved eagerly, off the IO hop. */
    private fun httpFailure(code: Int, body: String): NetworkResult<LoginResponse> =
        kotlinx.coroutines.runBlocking {
            com.thedavelopers.eventqr.core.api.safeApiCall<LoginResponse> {
                throw HttpException(Response.error<Any>(code, body.toResponseBody()))
            }
        }

    private fun unauthorized() = HttpException(Response.error<Any>(401, "".toResponseBody()))

    private class FakeAuthRepository(
        private val handler: suspend (String, String) -> NetworkResult<LoginResponse>,
    ) : AuthRepository(ApplicationProvider.getApplicationContext()) {
        val passwords = mutableListOf<String>()
        override suspend fun login(email: String, password: String): NetworkResult<LoginResponse> {
            passwords += password
            return handler(email, password)
        }
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

    private companion object {
        const val DISABLED = "Resource: account disabled"
        const val RATE_LIMITED = "Resource: too many attempts"
        const val INVALID_REQUEST = "Resource: invalid request"
        const val SPACES_HINT = "Resource: check spaces"
    }

    private class RecordingLoginView : LoginContract.View {
        val loadingStates = mutableListOf<Boolean>()
        val emailErrors = mutableListOf<String?>()
        val passwordErrors = mutableListOf<String?>()
        val messages = mutableListOf<String>()
        val dashboardRoles = mutableListOf<String?>()
        val succeededEmails = mutableListOf<String>()
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

        override fun onLoginSucceeded(email: String) {
            succeededEmails += email
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