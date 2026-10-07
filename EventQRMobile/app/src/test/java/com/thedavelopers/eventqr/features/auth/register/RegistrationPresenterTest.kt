package com.thedavelopers.eventqr.features.auth.register

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
        presenter = RegistrationPresenter(view, AuthRepository(context))
        presenter.attach(view)
    }

    @Test
    fun submitRegistration_emptyFields_showsAllRequiredErrors() {
        presenter.submitRegistration("", "", "", "", "", "")

        assertEquals(listOf("First name is required"), view.fieldErrors["firstName"])
        assertEquals(listOf("Last name is required"), view.fieldErrors["lastName"])
        assertEquals(listOf("Enter a valid email address"), view.fieldErrors["email"])
        assertEquals(listOf("Enter valid 10-digit mobile number"), view.fieldErrors["phone"])
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
    fun submitRegistration_invalidEmailAndPhone_showTheirOwnErrors() {
        presenter.submitRegistration(
            "John", "Doe", "not-an-email", "123", "Strong1!Pass", "Strong1!Pass",
        )

        assertEquals(listOf(null), view.fieldErrors["firstName"])
        assertEquals(listOf(null), view.fieldErrors["lastName"])
        assertEquals(listOf("Enter a valid email address"), view.fieldErrors["email"])
        assertEquals(listOf("Enter valid 10-digit mobile number"), view.fieldErrors["phone"])
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

        assertEquals(listOf("Enter valid 10-digit mobile number"), view.fieldErrors["phone"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_rawLocal11Digit_deniedWithoutPresenterNormalization() {
        // Raw local form never reaches the presenter (the Activity normalizes first);
        // the presenter validates, it does not normalize.
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "09171234567", "Strong1!Pass", "Strong1!PasX",
        )

        assertEquals(listOf("Enter valid 10-digit mobile number"), view.fieldErrors["phone"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun submitRegistration_overLengthPhone_denied() {
        presenter.submitRegistration(
            "John", "Doe", "user@example.com", "+639171234567890", "Strong1!Pass", "Strong1!PasX",
        )

        assertEquals(listOf("Enter valid 10-digit mobile number"), view.fieldErrors["phone"])
        assertTrue(view.loadingStates.isEmpty())
    }

    @Test
    fun detach_stopsNotifyingView() {
        presenter.detach()

        presenter.submitRegistration("", "", "", "", "", "")

        assertTrue(view.fieldErrors.isEmpty())
        assertTrue(view.loadingStates.isEmpty())
    }

    private class RecordingRegistrationView : RegistrationContract.View {
        val loadingStates = mutableListOf<Boolean>()
        val fieldErrors = mutableMapOf<String, MutableList<String?>>()
        val messages = mutableListOf<String>()
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

        override fun navigateToSignIn() {
            navigateToSignInCount++
        }
    }
}