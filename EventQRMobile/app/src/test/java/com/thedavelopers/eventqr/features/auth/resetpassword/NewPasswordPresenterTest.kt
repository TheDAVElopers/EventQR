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
class NewPasswordPresenterTest {

    private class RecordingView : NewPasswordContract.View {
        val passwordErrors = mutableListOf<String?>()
        val confirmErrors = mutableListOf<String?>()
        val loading = mutableListOf<Boolean>()
        val messages = mutableListOf<String>()
        var loginCount = 0
        val expiredEmails = mutableListOf<String>()
        override fun showLoading(isLoading: Boolean) { loading += isLoading }
        override fun showPasswordError(message: String?) { passwordErrors += message }
        override fun showConfirmPasswordError(message: String?) { confirmErrors += message }
        override fun showMessage(message: String) { messages += message }
        override fun navigateBackToExpiredCode(email: String) { expiredEmails += email }
        override fun navigateToLogin() { loginCount++ }
    }

    private class FakeRepo(var result: NetworkResult<Unit> = NetworkResult.Success(Unit)) :
        AuthRepository(ApplicationProvider.getApplicationContext()) {
        data class Call(val email: String, val code: String, val password: String, val confirm: String)
        val calls = mutableListOf<Call>()
        override suspend fun resetPassword(email: String, code: String, newPassword: String, confirmPassword: String): NetworkResult<Unit> {
            calls += Call(email, code, newPassword, confirmPassword)
            return result
        }
    }

    private lateinit var context: Context
    private lateinit var view: RecordingView
    private lateinit var repo: FakeRepo
    private lateinit var presenter: NewPasswordPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        view = RecordingView()
        repo = FakeRepo()
        presenter = NewPasswordPresenter()
        presenter.attach(view, context)
        presenter.repository = repo
        presenter.start("user@example.com", "123456")
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun httpFailure(code: Int, body: String = ""): NetworkResult<Unit> = runBlocking {
        safeApiCall<Unit> { throw HttpException(Response.error<Any>(code, body.toResponseBody())) }
    }

    @Test
    fun start_missingEmailOrCode_navigatesToLogin() {
        val p = NewPasswordPresenter()
        p.attach(view, context)

        p.start("user@example.com", null)
        p.start(null, "123456")

        assertEquals(2, view.loginCount)
    }

    @Test
    fun submit_noLowercase_rejectedWithoutNetworkCall() {
        presenter.submit("PASSWORD1!", "PASSWORD1!")
        idle()

        assertEquals(context.getString(R.string.password_policy_hint), view.passwordErrors.last())
        assertTrue(repo.calls.isEmpty())
        assertTrue(view.loading.isEmpty())
    }

    @Test
    fun submit_mismatch_rejectedWithoutNetworkCall() {
        presenter.submit("Password1!", "Password2!")
        idle()

        assertEquals(context.getString(R.string.password_error_mismatch), view.confirmErrors.last())
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun submit_success_resubmitsEmailAndCodeThenGoesToLogin() {
        presenter.submit("Password1!", "Password1!")
        idle()

        assertEquals(listOf(FakeRepo.Call("user@example.com", "123456", "Password1!", "Password1!")), repo.calls)
        assertEquals(listOf(context.getString(R.string.reset_password_success)), view.messages)
        assertEquals(1, view.loginCount)
        assertTrue(view.expiredEmails.isEmpty())
    }

    @Test
    fun submit_codeExpired400_returnsToCodeStepWithoutToast() {
        repo.result = httpFailure(400, "{\"message\":\"${NewPasswordPresenter.CODE_EXPIRED_MESSAGE}\"}")

        presenter.submit("Password1!", "Password1!")
        idle()

        assertTrue(view.messages.isEmpty())
        assertEquals(listOf("user@example.com"), view.expiredEmails)
        assertEquals(0, view.loginCount)
        assertEquals(listOf(true, false), view.loading)
    }

    @Test
    fun submit_otherPolicy400_staysOnScreenWithServerMessage() {
        repo.result = httpFailure(400, "{\"message\":\"Password is too weak\"}")

        presenter.submit("Password1!", "Password1!")
        idle()

        assertEquals(listOf("Password is too weak"), view.messages)
        assertTrue(view.expiredEmails.isEmpty())
        assertEquals(0, view.loginCount)
    }

    @Test
    fun submit_rateLimited429_staysOnScreen() {
        repo.result = httpFailure(429)

        presenter.submit("Password1!", "Password1!")
        idle()

        assertEquals(listOf(context.getString(R.string.forgot_password_rate_limited)), view.messages)
        assertTrue(view.expiredEmails.isEmpty())
        assertEquals(0, view.loginCount)
    }

    @Test
    fun submit_networkFailure_staysOnScreen() {
        repo.result = NetworkResult.Error("Network problem", java.io.IOException())

        presenter.submit("Password1!", "Password1!")
        idle()

        assertEquals(listOf("Network problem"), view.messages)
        assertTrue(view.expiredEmails.isEmpty())
    }
}
