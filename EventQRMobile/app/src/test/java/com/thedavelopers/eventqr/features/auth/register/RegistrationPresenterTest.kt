package com.thedavelopers.eventqr.features.auth.register

import android.os.Looper
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.robolectric.Shadows.shadowOf
import retrofit2.HttpException
import retrofit2.Response
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.core.util.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Presenter-level tests for the XML reverted Registration flow (synchronous
 * validation paths only; the create-account network call has no unit seam —
 * see coverage notes in the test report).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RegistrationPresenterTest {

    private lateinit var view: RecordingRegistrationView
    private lateinit var presenter: RegistrationPresenter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        view = RecordingRegistrationView()
        presenter = RegistrationPresenter(view, AuthRepository(context), UiStrings(context))
        presenter.attach(view)
    }

    @Test
    fun submitRegistration_emptyFields_showsAllRequiredErrors() {
        presenter.submitRegistration("", "", "", "", "", "")

        assertEquals(listOf("First name is required"), view.fieldErrors["firstName"])
        assertEquals(listOf("Last name is required"), view.fieldErrors["lastName"])
        assertEquals(listOf("Enter a valid email address"), view.fieldErrors["email"])
        assertEquals(listOf(Validators.PHONE_ERROR), view.fieldErrors["phone"])
        assertEquals(listOf("Password must meet all requirements"), view.fieldErrors["password"])
        // Both passwords are empty, so no mismatch error.
        assertEquals(listOf(null), view.fieldErrors["confirmPassword"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_passwordMismatch_showsMismatchErrorOnly() {
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "+639171234567", "Strong1!Pass", "Strong1!PasX",
        )

        assertEquals(listOf(null), view.fieldErrors["firstName"])
        assertEquals(listOf(null), view.fieldErrors["lastName"])
        assertEquals(listOf(null), view.fieldErrors["email"])
        assertEquals(listOf(null), view.fieldErrors["phone"])
        assertEquals(listOf(null), view.fieldErrors["password"])
        assertEquals(listOf("Passwords do not match"), view.fieldErrors["confirmPassword"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_weakPassword_showsRequirementError() {
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "+639171234567", "short", "short",
        )

        assertEquals(listOf("Password must meet all requirements"), view.fieldErrors["password"])
        assertEquals(listOf(null), view.fieldErrors["confirmPassword"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_noLowercasePassword_showsRequirementError() {
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "+639171234567", "PASSWORD1!", "PASSWORD1!",
        )

        assertEquals(listOf("Password must meet all requirements"), view.fieldErrors["password"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_invalidEmailAndPhone_showTheirOwnErrors() {
        presenter.submitRegistration(
            "John", "Doe", "not-an-email", "123", "Strong1!Pass", "Strong1!Pass",
        )

        assertEquals(listOf(null), view.fieldErrors["firstName"])
        assertEquals(listOf(null), view.fieldErrors["lastName"])
        assertEquals(listOf("Enter a valid email address"), view.fieldErrors["email"])
        assertEquals(listOf(Validators.PHONE_ERROR), view.fieldErrors["phone"])
        assertEquals(listOf(null), view.fieldErrors["password"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_trimsValuesBeforeValidation() {
        // Spaced-but-valid values pass; the mismatch keeps the call off the network.
        presenter.submitRegistration(
            "  John  ", "  Doe  ", "  user@example.com  ", "  +639171234567  ", "Strong1!Pass", "Strong1!PasX",
        )

        assertEquals(listOf(null), view.fieldErrors["firstName"])
        assertEquals(listOf(null), view.fieldErrors["lastName"])
        assertEquals(listOf(null), view.fieldErrors["email"])
        assertEquals(listOf(null), view.fieldErrors["phone"])
        assertEquals(listOf("Passwords do not match"), view.fieldErrors["confirmPassword"])
    }

    @Test
    fun submitRegistration_fullInternationalPhone_acceptedByValidator() {
        // The Activity normalizes pasted input to national digits and submits "+63" +
        // digits; the validator must accept that full E.164 value.
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "+639171234567", "Strong1!Pass", "Strong1!PasX",
        )

        assertEquals(listOf(null), view.fieldErrors["phone"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_legacy63Phone_acceptedByValidator() {
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "639171234567", "Strong1!Pass", "Strong1!PasX",
        )

        assertEquals(listOf(null), view.fieldErrors["phone"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_nationalDigitsWithoutPrefix_denied() {
        // 10 national digits alone are not a valid submission value.
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "9171234567", "Strong1!Pass", "Strong1!PasX",
        )

        assertEquals(listOf(Validators.PHONE_ERROR), view.fieldErrors["phone"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_rawLocal11Digit_deniedWithoutPresenterNormalization() {
        // Raw local form never reaches the presenter (the Activity normalizes first);
        // the presenter validates, it does not normalize.
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "09171234567", "Strong1!Pass", "Strong1!PasX",
        )

        assertEquals(listOf(Validators.PHONE_ERROR), view.fieldErrors["phone"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_overLengthPhone_denied() {
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "+639171234567890", "Strong1!Pass", "Strong1!PasX",
        )

        assertEquals(listOf(Validators.PHONE_ERROR), view.fieldErrors["phone"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun detach_stopsNotifyingView() {
        presenter.detach()

        presenter.submitRegistration("", "", "", "", "", "")

        assertTrue(view.fieldErrors.isEmpty())
        assertTrue(view.loadingStates.isEmpty())
    }

    // -- Combined full-name length (the API rejects fullName over 255 chars) ------

    @Test
    fun submitRegistration_combinedNameOver255_showsNameErrorAndSkipsNetwork() {
        // 150 + 1 + 150 = 301: the value that used to reach the API and surface a bogus
        // "user already exists" toast now fails validation before the request.
        presenter.submitRegistration(
            "a".repeat(150), "b".repeat(150), "user@example.com", "+639171234567", "Strong1!Pass", "Strong1!Pass",
        )

        assertEquals(
            listOf("First and last name together must be at most 255 characters."),
            view.fieldErrors["fullName"],
        )
        assertEquals(listOf(null), view.fieldErrors["firstName"])
        assertEquals(listOf(null), view.fieldErrors["lastName"])
        // No showLoading and no message: the create-account call never started.
        assertTrue(view.loadingStates.isEmpty())
        assertTrue(view.messages.isEmpty())
    }

    @Test
    fun submitRegistration_combinedNameExactly255_isAllowed() {
        // 127 + 1 + 127 = 255, the largest value the API accepts.
        presenter.submitRegistration(
            "a".repeat(127), "b".repeat(127), "user@example.com", "+639171234567", "Strong1!Pass", "Strong1!Pass",
        )

        assertEquals(listOf(null), view.fieldErrors["fullName"])
        assertEquals(listOf(null), view.fieldErrors["firstName"])
        assertEquals(listOf(null), view.fieldErrors["lastName"])
    }

    @Test
    fun submitRegistration_trailingSpacesTrimBeforeLengthCheck() {
        // Raw 100 + 1 + 155 = 256 would fail, but the trailing spaces are not part of
        // the value sent, so the trimmed 251 is accepted.
        presenter.submitRegistration(
            "a".repeat(100), "b".repeat(150) + " ".repeat(5), "user@example.com", "+639171234567", "Strong1!Pass", "Strong1!Pass",
        )

        assertEquals(listOf(null), view.fieldErrors["fullName"])
    }

    @Test
    fun submitRegistration_nameWithDigits_isAllowed() {
        // Names keep digits; this is not a character-class rule.
        presenter.submitRegistration(
            "Test Attendee 1", "Smith", "user@example.com", "+639171234567", "Strong1!Pass", "Strong1!Pass",
        )

        assertEquals(listOf(null), view.fieldErrors["fullName"])
        assertEquals(listOf(null), view.fieldErrors["firstName"])
        assertEquals(listOf(null), view.fieldErrors["lastName"])
    }

    // ---- §5.16: a failed create-account is one strip sentence; the API-named field stays a field error

    private fun string(id: Int) =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private class FakeRepo(private val result: NetworkResult<UserResponse>) :
        AuthRepository(ApplicationProvider.getApplicationContext()) {
        var calls = 0
        override suspend fun createUser(
            fullName: String,
            email: String,
            phoneNumber: String,
            password: String,
        ): NetworkResult<UserResponse> {
            calls++
            return result
        }
    }

    private fun httpFailure(code: Int, body: String = ""): NetworkResult<UserResponse> = runBlocking {
        safeApiCall<UserResponse> { throw HttpException(Response.error<Any>(code, body.toResponseBody())) }
    }

    private fun submitValid(result: NetworkResult<UserResponse>): FakeRepo {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = FakeRepo(result)
        val p = RegistrationPresenter(view, repo, UiStrings(context))
        p.submitRegistration("John", "Doe", "user@example.com", "+639171234567", "Strong1!Pass", "Strong1!Pass")
        shadowOf(Looper.getMainLooper()).idle()
        return repo
    }

    @Test
    fun submitRegistration_emailTaken409_isAFieldErrorOnEmailNotAStripOrToast() {
        submitValid(httpFailure(409, "{\"message\":\"User already exists for email user@example.com\"}"))

        assertEquals(
            listOf("email" to string(R.string.ui_auth_register_email_taken)),
            view.serverFieldErrors,
        )
        assertTrue(view.formErrors.isEmpty())
        assertTrue(view.messages.isEmpty())
        assertEquals(listOf(true, false), view.loadingStates)
    }

    @Test
    fun submitRegistration_badRequest400_showsFixedStripCopyNotTheServerText() {
        submitValid(httpFailure(400, "{\"message\":\"phoneNumber: must match pattern\"}"))

        assertEquals(listOf(string(R.string.ui_auth_register_rejected)), view.formErrors)
        assertTrue(view.serverFieldErrors.isEmpty())
        assertTrue(view.messages.isEmpty())
    }

    @Test
    fun submitRegistration_noConnection_showsTheNetworkSentence() {
        submitValid(NetworkResult.Error("Can't reach the server.", java.net.UnknownHostException("host")))

        assertEquals(listOf(string(R.string.ui_auth_error_network)), view.formErrors)
        assertTrue(view.messages.isEmpty())
    }

    @Test
    fun submitRegistration_serverError500_showsAFixedSentence() {
        submitValid(httpFailure(500, "{\"message\":\"java.lang.IllegalStateException: boom\"}"))

        assertEquals(listOf(string(R.string.ui_auth_register_failed)), view.formErrors)
    }

    @Test
    fun submitRegistration_success_staysAToastAndLeavesNoStrip() {
        submitValid(NetworkResult.Success(
            UserResponse(
                userId = java.util.UUID.randomUUID(),
                email = "user@example.com",
                fullName = "John Doe",
                role = com.thedavelopers.eventqr.core.api.dto.AccountRole.ATTENDEE,
                status = com.thedavelopers.eventqr.core.api.dto.AccountStatus.ACTIVE,
            ),
            "Account created",
        ))

        assertEquals(listOf("Account created"), view.messages)
        assertTrue(view.formErrors.isEmpty())
        assertEquals(1, view.navigateToSignInCount)
    }

    private class RecordingRegistrationView : RegistrationContract.View {
        val loadingStates = mutableListOf<Boolean>()
        val fieldErrors = mutableMapOf<String, MutableList<String?>>()
        val messages = mutableListOf<String>()
        val formErrors = mutableListOf<String>()
        val serverFieldErrors = mutableListOf<Pair<String, String>>()
        var navigateToSignInCount = 0

        override fun showLoading(isLoading: Boolean) {
            loadingStates += isLoading
        }

        override fun showFieldError(field: String, message: String?) {
            fieldErrors.getOrPut(field) { mutableListOf() }.add(message)
        }

        override fun showMessage(message: String) {
            messages += message
        }

        override fun showFormError(message: String) {
            formErrors += message
        }

        override fun showServerFieldError(field: String, message: String) {
            serverFieldErrors += field to message
        }

        override fun navigateToSignIn() {
            navigateToSignInCount++
        }
    }
}