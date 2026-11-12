package com.thedavelopers.eventqr.features.auth.changepassword

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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ChangePasswordPresenterTest {

    private class RecordingView : ChangePasswordContract.View {
        val newPasswordErrors = mutableListOf<String?>()
        val loading = mutableListOf<Boolean>()
        val currentErrors = mutableListOf<String?>()
        val formErrors = mutableListOf<String>()
        var successCount = 0
        override fun showLoading(isLoading: Boolean) { loading += isLoading }
        override fun showCurrentPasswordError(message: String?) { currentErrors += message }
        override fun showNewPasswordError(message: String?) { newPasswordErrors += message }
        override fun showConfirmPasswordError(message: String?) = Unit
        override fun showFormError(message: String) { formErrors += message }
        override fun showSuccess() { successCount++ }
        override fun navigateBack() = Unit
    }

    // ---- §5.16: a failed change is one strip sentence with no Retry; the API-named field stays a field error

    private class FakeRepo(private val result: NetworkResult<Unit>) :
        AuthRepository(ApplicationProvider.getApplicationContext()) {
        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
            confirmPassword: String,
        ): NetworkResult<Unit> = result
    }

    private fun httpFailure(code: Int, body: String = ""): NetworkResult<Unit> = runBlocking {
        safeApiCall<Unit> { throw HttpException(Response.error<Any>(code, body.toResponseBody())) }
    }

    private fun string(id: Int) =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun submitWith(view: RecordingView, result: NetworkResult<Unit>) {
        val presenter = ChangePasswordPresenter()
        presenter.attach(view, ApplicationProvider.getApplicationContext())
        presenter.repository = FakeRepo(result)
        presenter.submitChange("OldPass1!", "NewPass1!x", "NewPass1!x")
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun submitChange_wrongCurrentPassword400_isAFieldErrorWithFixedCopy() {
        val view = RecordingView()

        submitWith(view, httpFailure(400, "{\"message\":\"Current password is incorrect\"}"))

        assertEquals(string(R.string.ui_auth_current_password_incorrect), view.currentErrors.last())
        assertTrue(view.formErrors.isEmpty())
    }

    @Test
    fun submitChange_otherBadRequests_areAFormErrorNotAWrongCurrentPassword() {
        // The server's own rules the client does not mirror: a policy or mismatch rejection.
        for (body in listOf(
            "{\"message\":\"Passwords do not match\"}",
            "{\"message\":\"Password must be at most 128 characters\"}",
            "",
        )) {
            val view = RecordingView()

            submitWith(view, httpFailure(400, body))

            assertEquals(body, listOf(string(R.string.ui_auth_change_password_rejected)), view.formErrors)
            assertTrue("never blames the current password", view.currentErrors.none { it != null })
        }
    }

    @Test
    fun submitChange_rateLimited429_isAFixedStripSentenceNotAFieldError() {
        val view = RecordingView()

        submitWith(view, httpFailure(429))

        assertEquals(listOf(string(R.string.ui_auth_error_rate_limited)), view.formErrors)
        assertTrue(view.currentErrors.none { it != null })
        assertEquals(listOf(true, false), view.loading)
    }

    @Test
    fun submitChange_noConnection_showsTheNetworkSentence() {
        val view = RecordingView()

        submitWith(view, NetworkResult.Error("Network problem", java.io.IOException()))

        assertEquals(listOf(string(R.string.ui_auth_error_network)), view.formErrors)
        assertEquals(listOf(true, false), view.loading)
        assertEquals(0, view.successCount)
    }

    @Test
    fun submitChange_serverError500_showsAFixedSentence() {
        val view = RecordingView()

        submitWith(view, httpFailure(500, "{\"message\":\"java.lang.IllegalStateException: boom\"}"))

        assertEquals(listOf(string(R.string.ui_auth_change_password_failed)), view.formErrors)
    }

    @Test
    fun submitChange_success_leavesNoStrip() {
        val view = RecordingView()

        submitWith(view, NetworkResult.Success(Unit))

        assertEquals(1, view.successCount)
        assertTrue(view.formErrors.isEmpty())
    }

    @Test
    fun submitChange_noLowercase_rejectedWithoutNetworkCall() {
        val view = RecordingView()
        val presenter = ChangePasswordPresenter()
        presenter.attach(view, ApplicationProvider.getApplicationContext())

        presenter.submitChange("OldPass1!", "PASSWORD1!", "PASSWORD1!")

        assertEquals(
            "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number, and a special character",
            view.newPasswordErrors.last(),
        )
        assertTrue(view.loading.isEmpty())
    }
}
