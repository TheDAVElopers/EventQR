package com.thedavelopers.eventqr.features.auth.resetpassword

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ResetPasswordPresenterTest {

    private class RecordingView : ResetPasswordContract.View {
        val codeErrors = mutableListOf<String?>()
        val loading = mutableListOf<Boolean>()
        val emails = mutableListOf<String>()
        val messages = mutableListOf<String>()
        val cooldowns = mutableListOf<Int>()
        val newPasswordTargets = mutableListOf<Pair<String, String>>()
        var loginCount = 0
        var expiredCount = 0
        var resendUnavailableCount = 0
        override fun showLoading(isLoading: Boolean) { loading += isLoading }
        override fun showEmail(email: String) { emails += email }
        override fun showCodeError(message: String?) { codeErrors += message }
        override fun showCodeExpired() { expiredCount++ }
        override fun showMessage(message: String) { messages += message }
        override fun showResendCooldown(secondsLeft: Int) { cooldowns += secondsLeft }
        override fun showResendUnavailable() { resendUnavailableCount++ }
        override fun navigateToNewPassword(email: String, code: String) { newPasswordTargets += email to code }
        override fun navigateToLogin() { loginCount++ }
    }

    private class FakeRepo(
        var verifyResult: NetworkResult<Unit> = NetworkResult.Success(Unit),
        var forgotResult: NetworkResult<Unit> = NetworkResult.Success(Unit),
    ) : AuthRepository(ApplicationProvider.getApplicationContext()) {
        val verifyCalls = mutableListOf<Pair<String, String>>()
        var forgotCalls = 0
        override suspend fun verifyResetCode(email: String, code: String): NetworkResult<Unit> {
            verifyCalls += email to code
            return verifyResult
        }
        override suspend fun forgotPassword(email: String): NetworkResult<Unit> {
            forgotCalls++
            return forgotResult
        }
    }

    private lateinit var context: Context
    private lateinit var view: RecordingView
    private lateinit var repo: FakeRepo
    private lateinit var presenter: ResetPasswordPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        view = RecordingView()
        repo = FakeRepo()
        presenter = ResetPasswordPresenter()
        presenter.attach(view, context)
        presenter.repository = repo
        // The email normally arrives through start(); set it directly to skip the cooldown coroutine.
        presenter.email = "user@example.com"
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun httpFailure(code: Int, body: String = ""): NetworkResult<Unit> = runBlocking {
        safeApiCall<Unit> { throw HttpException(Response.error<Any>(code, body.toResponseBody())) }
    }

    @Test
    fun submitCode_codeNotSixDigits_rejectedWithoutNetworkCall() {
        presenter.submitCode("12a456")
        idle()

        assertEquals(context.getString(R.string.reset_password_code_invalid), view.codeErrors.last())
        assertTrue(repo.verifyCalls.isEmpty())
        assertTrue(view.loading.isEmpty())
    }

    @Test
    fun submitCode_success_navigatesToNewPasswordWithEmailAndCode() {
        presenter.submitCode(" 123456 ")
        idle()

        assertEquals(listOf("user@example.com" to "123456"), repo.verifyCalls)
        assertEquals(listOf("user@example.com" to "123456"), view.newPasswordTargets)
        assertEquals(listOf(true, false), view.loading)
    }

    @Test
    fun submitCode_wrongCode400_showsErrorUnderFieldAndStays() {
        repo.verifyResult = httpFailure(400, "{\"message\":\"Reset code is invalid or expired\"}")

        presenter.submitCode("123456")
        idle()

        assertEquals(context.getString(R.string.reset_password_code_incorrect), view.codeErrors.last())
        assertTrue(view.newPasswordTargets.isEmpty())
        assertEquals(listOf(true, false), view.loading)
    }

    @Test
    fun submitCode_rateLimited429_showsRateLimitMessageNotFieldError() {
        repo.verifyResult = httpFailure(429)

        presenter.submitCode("123456")
        idle()

        assertEquals(listOf(context.getString(R.string.forgot_password_rate_limited)), view.messages)
        assertEquals(listOf<String?>(null), view.codeErrors)
        assertTrue(view.newPasswordTargets.isEmpty())
    }

    @Test
    fun submitCode_networkFailure_showsMessageAndStays() {
        repo.verifyResult = NetworkResult.Error("Network problem", java.io.IOException())

        presenter.submitCode("123456")
        idle()

        assertEquals(listOf("Network problem"), view.messages)
        assertTrue(view.newPasswordTargets.isEmpty())
    }

    @Test
    fun onCodeExpired_clearsResendCooldownAndCapSoANewCodeCanBeRequested() {
        presenter.resendsDone = ResetResendPolicy.MAX_RESENDS

        presenter.onCodeExpired()

        assertEquals(1, view.expiredCount)
        assertEquals(0, presenter.resendsDone)
        assertEquals(0, view.cooldowns.last())
    }

    @Test
    fun submitCode_missingEmail_navigatesToLogin() {
        val p = ResetPasswordPresenter()
        p.attach(view, context)
        p.repository = repo

        p.submitCode("123456")

        assertEquals(1, view.loginCount)
        assertTrue(repo.verifyCalls.isEmpty())
    }

    @Test
    fun start_blankEmail_navigatesToLogin() {
        val p = ResetPasswordPresenter()
        p.attach(view, context)

        p.start(null)

        assertEquals(1, view.loginCount)
        assertTrue(view.emails.isEmpty())
    }

    @Test
    fun resend_success_escalatesCooldown() {
        presenter.resendCode()
        idle()

        assertEquals(1, repo.forgotCalls)
        assertEquals(1, presenter.resendsDone)
        // Locked straight away with the current wait, then the escalated wait for the next resend.
        assertEquals(listOf(30, 60), view.cooldowns.take(2))
    }

    @Test
    fun resend_atCap_isDisabledWithoutRequest() {
        presenter.resendsDone = ResetResendPolicy.MAX_RESENDS

        presenter.resendCode()
        idle()

        assertEquals(0, repo.forgotCalls)
        assertEquals(1, view.resendUnavailableCount)
    }

    @Test
    fun resend_lastAllowedResend_disablesFurtherResends() {
        presenter.resendsDone = ResetResendPolicy.MAX_RESENDS - 1

        presenter.resendCode()
        idle()

        assertEquals(ResetResendPolicy.MAX_RESENDS, presenter.resendsDone)
        assertEquals(1, view.resendUnavailableCount)
        assertTrue(view.messages.contains(context.getString(R.string.reset_password_resend_limit_reached)))
    }

    @Test
    fun resend_rateLimited429_showsMessageStartsCooldownAndDoesNotCount() {
        repo.forgotResult = httpFailure(429)

        presenter.resendCode()
        idle()

        assertEquals(listOf(context.getString(R.string.forgot_password_rate_limited)), view.messages)
        assertEquals(0, presenter.resendsDone)
        assertEquals(30, view.cooldowns.last())
    }

    @Test
    fun resend_serverError_releasesTheButton() {
        repo.forgotResult = httpFailure(500)

        presenter.resendCode()
        idle()

        assertEquals(0, view.cooldowns.last())
        assertEquals(0, presenter.resendsDone)
    }
}
