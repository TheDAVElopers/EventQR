package com.thedavelopers.eventqr.features.auth.login

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.MotionEvent
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.forgotpassword.ForgotPasswordActivity
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.thedavelopers.eventqr.features.auth.AuthStripTestKit
import org.robolectric.shadows.ShadowToast

/**
 * Robolectric tests for the XML reverted LoginActivity: field error wiring,
 * the password visibility toggle, and navigation links. The sign-in success
 * path performs a network call via AuthRepository and is not unit-testable
 * (see coverage notes).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class LoginActivityTest {

    private fun buildActivity(): LoginActivity =
        Robolectric.buildActivity(LoginActivity::class.java).create().get()

    @Test
    fun passwordField_isCappedAt128Characters() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)

        passwordInput.setText("a".repeat(200))

        // Matches the backend's @Size(max = 128) on the login password.
        assertEquals(128, passwordInput.text.length)
    }

    @Test
    fun noRememberedEmail_leavesEmailEmptyAndCheckboxUnchecked() {
        val activity = buildActivity()

        assertEquals("", activity.findViewById<EditText>(R.id.edtEmail).text.toString())
        assertFalse(activity.findViewById<android.widget.CheckBox>(R.id.chkRememberMe).isChecked)
    }

    @Test
    fun rememberedEmail_isPrefilledAndTheBoxIsChecked() {
        com.thedavelopers.eventqr.core.session.RememberedEmailStore
            .create(androidx.test.core.app.ApplicationProvider.getApplicationContext())
            .save("saved@example.com")

        val activity = buildActivity()

        assertEquals("saved@example.com", activity.findViewById<EditText>(R.id.edtEmail).text.toString())
        assertTrue(activity.findViewById<android.widget.CheckBox>(R.id.chkRememberMe).isChecked)
        assertEquals("", activity.findViewById<EditText>(R.id.edtPassword).text.toString())
    }

    @Test
    fun successfulLoginWithTheBoxChecked_savesTheEmailOnly() {
        val activity = buildActivity()
        activity.findViewById<android.widget.CheckBox>(R.id.chkRememberMe).isChecked = true

        activity.onLoginSucceeded("user@example.com")

        val store = com.thedavelopers.eventqr.core.session.RememberedEmailStore
            .create(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        assertEquals("user@example.com", store.get())
    }

    @Test
    fun successfulLoginWithTheBoxUnchecked_forgetsAPreviouslyRememberedEmail() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.thedavelopers.eventqr.core.session.RememberedEmailStore.create(context).save("old@example.com")
        val activity = buildActivity()
        activity.findViewById<android.widget.CheckBox>(R.id.chkRememberMe).isChecked = false

        activity.onLoginSucceeded("user@example.com")

        assertNull(com.thedavelopers.eventqr.core.session.RememberedEmailStore.create(context).get())
    }

    @Test
    fun clickSignIn_invalidCredentials_showsFieldErrors() {
        val activity = buildActivity()
        activity.findViewById<EditText>(R.id.edtEmail).setText("bad-email")
        activity.findViewById<EditText>(R.id.edtPassword).setText("")

        activity.findViewById<android.view.View>(R.id.btnSignIn).performClick()

        assertEquals("Enter a valid email address", AuthStripTestKit.helper(activity, R.id.txtEmailError))
        assertEquals("Enter your password", AuthStripTestKit.helper(activity, R.id.txtPasswordError))
        assertNull("no platform balloon", activity.findViewById<EditText>(R.id.edtEmail).error)
        assertNull("no platform balloon", activity.findViewById<EditText>(R.id.edtPassword).error)
        assertTrue(activity.findViewById<EditText>(R.id.edtEmail).isActivated)
        // No navigation on invalid input.
        assertNull(shadowOf(activity).peekNextStartedActivity())
        assertEquals("Sign in", activity.findViewById<android.widget.Button>(R.id.btnSignIn).text.toString())
    }

    @Test
    fun clickSignIn_validEmailInvalidPassword_clearsEmailErrorOnly() {
        val activity = buildActivity()
        activity.findViewById<EditText>(R.id.edtEmail).setText("user@example.com")
        activity.findViewById<EditText>(R.id.edtPassword).setText("")

        activity.findViewById<android.view.View>(R.id.btnSignIn).performClick()

        assertNull(AuthStripTestKit.helper(activity, R.id.txtEmailError))
        assertFalse(activity.findViewById<EditText>(R.id.edtEmail).isActivated)
        assertEquals("Enter your password", AuthStripTestKit.helper(activity, R.id.txtPasswordError))
    }

    @Test
    fun clickRegister_navigatesToRegistrationAndFinishes() {
        val activity = buildActivity()

        activity.findViewById<android.view.View>(R.id.btnRegister).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(RegistrationActivity::class.java.name, intent.component?.className)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun clickForgotPassword_navigatesWithoutFinishing() {
        val activity = buildActivity()

        activity.findViewById<android.widget.TextView>(R.id.txtForgotPassword).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(ForgotPasswordActivity::class.java.name, intent.component?.className)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun passwordToggle_toggleTheEyeUnmasksTheField() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)

        // The field carries the password input type, which TextView backs with a password
        // transformation; unmasking swaps that transformation, not the input type.
        assertTrue(passwordInput.transformationMethod is PasswordTransformationMethod)

        // Material's password_toggle end icon owns the eye now; tapping it reveals the text.
        toggleEndIcon(passwordInput)
        assertNull(passwordInput.transformationMethod)

        toggleEndIcon(passwordInput)
        assertTrue(passwordInput.transformationMethod is PasswordTransformationMethod)
    }

    @Test
    fun passwordToggle_touchOnTextZoneDoesNotToggle() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)
        layoutInput(passwordInput)
        val passwordInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        // rawX at 0, well inside the left padding/text zone.
        dispatchActionUp(passwordInput, 0f)

        assertEquals(passwordInputType, passwordInput.inputType)
    }

    private fun layoutInput(input: EditText) {
        input.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
        )
        input.layout(0, 0, 1000, 200)
    }

    /** Taps Material's password_toggle end icon on the field's [TextInputLayout] host. */
    private fun toggleEndIcon(input: EditText) {
        var parent: android.view.ViewParent? = input.parent
        while (parent is android.view.View) {
            if (parent is com.google.android.material.textfield.TextInputLayout) {
                assertEquals(
                    com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE,
                    parent.endIconMode,
                )
                val icon = parent.findViewById<android.view.View>(
                    com.google.android.material.R.id.text_input_end_icon,
                )
                requireNotNull(icon) { "password toggle end icon missing" }.performClick()
                return
            }
            parent = parent.parent
        }
        throw AssertionError("password field has no TextInputLayout host")
    }

    private fun dispatchActionUp(input: EditText, rawX: Float) {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, rawX, 100f, 0)
        try {
            input.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    // ---- §5.16 form failure strip (directly above Sign in; Retry only for a transient failure)

    private fun visibleActivity(): LoginActivity = AuthStripTestKit.visible(LoginActivity::class.java)

    private fun strip(activity: LoginActivity) = AuthStripTestKit.strip(activity, R.id.errorLogin)

    @Test
    fun theStrip_isHiddenUntilAFailureAndSitsDirectlyAboveSignIn() {
        val activity = buildActivity()
        val strip = strip(activity)
        val parent = strip.parent as android.view.ViewGroup

        assertEquals(android.view.View.GONE, strip.visibility)
        assertEquals(
            parent.indexOfChild(strip) + 1,
            parent.indexOfChild(activity.findViewById(R.id.btnSignIn)),
        )
    }

    @Test
    fun aFailure_showsTheSentenceInTheStripNotAToast() {
        val activity = buildActivity()

        activity.showFormError("Couldn't reach EventQR. Check your connection and try again.", retryable = true)

        val strip = strip(activity)
        assertEquals(android.view.View.VISIBLE, strip.visibility)
        assertEquals("Couldn't reach EventQR. Check your connection and try again.", AuthStripTestKit.message(strip))
        assertEquals(android.view.View.VISIBLE, AuthStripTestKit.retry(strip).visibility)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun aNonTransientFailure_hidesRetry() {
        val activity = buildActivity()

        activity.showFormError("Incorrect email or password. Check them and try again.", retryable = false)

        assertEquals(android.view.View.VISIBLE, strip(activity).visibility)
        assertEquals(android.view.View.GONE, AuthStripTestKit.retry(strip(activity)).visibility)
    }

    @Test
    fun nextSubmit_clearsTheStrip() {
        val activity = buildActivity()
        activity.showFormError("Couldn't sign you in. Try again in a moment.", retryable = true)

        activity.findViewById<android.widget.Button>(R.id.btnSignIn).performClick()

        assertEquals(android.view.View.GONE, strip(activity).visibility)
    }

    @Test
    fun retry_reRunsTheSubmitFromAFreshStrip() {
        val activity = buildActivity()
        activity.showFormError("Couldn't sign you in. Try again in a moment.", retryable = true)

        AuthStripTestKit.retry(strip(activity)).performClick()

        assertEquals(android.view.View.GONE, strip(activity).visibility)
        // The submit ran against the (empty) fields, so its own validation answered.
        assertEquals("Enter a valid email address", AuthStripTestKit.helper(activity, R.id.txtEmailError))
    }

    @Test
    fun everyFailure_isAnnounced_includingAnIdenticalRepeat() {
        AuthStripTestKit.enableAccessibility()
        val activity = visibleActivity()
        val message = "Couldn't sign you in. Try again in a moment."

        activity.showFormError(message, retryable = true)
        activity.findViewById<android.widget.Button>(R.id.btnSignIn).performClick()
        activity.showFormError(message, retryable = true)

        // The click also fails validation on the empty form, which is a failure of its own.
        assertEquals(listOf(message, "Enter a valid email address", message), AuthStripTestKit.announcements())
    }

    // ---- Field errors: helper line, focus, announce, clear on edit

    @Test
    fun aFieldError_takesFocusAndIsAnnouncedOnce_whenSeveralFieldsFail() {
        AuthStripTestKit.enableAccessibility()
        val activity = visibleActivity()

        activity.findViewById<android.view.View>(R.id.btnSignIn).performClick()

        assertTrue("focus on the first invalid field", activity.findViewById<EditText>(R.id.edtEmail).hasFocus())
        assertEquals(listOf("Enter a valid email address"), AuthStripTestKit.announcements())
    }

    @Test
    fun editingAField_clearsItsOwnErrorOnly() {
        val activity = buildActivity()
        activity.findViewById<android.view.View>(R.id.btnSignIn).performClick()

        activity.findViewById<EditText>(R.id.edtEmail).setText("u")

        assertNull(AuthStripTestKit.helper(activity, R.id.txtEmailError))
        assertFalse(activity.findViewById<EditText>(R.id.edtEmail).isActivated)
        assertEquals("Enter your password", AuthStripTestKit.helper(activity, R.id.txtPasswordError))
    }

    // ---- Rotation

    @Test
    fun rotation_keepsFieldErrors_andTheyStaySilent() {
        AuthStripTestKit.enableAccessibility()
        val controller = AuthStripTestKit.visibleController(LoginActivity::class.java)
        controller.get().findViewById<android.view.View>(R.id.btnSignIn).performClick()
        controller.get().findViewById<EditText>(R.id.edtEmail).setText("u")
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertNull("an edited field stays clear", AuthStripTestKit.helper(after, R.id.txtEmailError))
        assertEquals("Enter your password", AuthStripTestKit.helper(after, R.id.txtPasswordError))
        assertTrue(after.findViewById<EditText>(R.id.edtPassword).isActivated)
        assertNull(after.findViewById<EditText>(R.id.edtPassword).error)
        assertEquals("a restore is not a new failure", announced, AuthStripTestKit.announcements().size)
    }

    @Test
    fun rotation_keepsTheStripAndItsRetry_withoutAnnouncingAgain() {
        AuthStripTestKit.enableAccessibility()
        val controller = AuthStripTestKit.visibleController(LoginActivity::class.java)
        val message = "Couldn't sign you in. Try again in a moment."
        controller.get().showFormError(message, retryable = true)
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(android.view.View.VISIBLE, strip(after).visibility)
        assertEquals(message, AuthStripTestKit.message(strip(after)))
        assertEquals(android.view.View.VISIBLE, AuthStripTestKit.retry(strip(after)).visibility)
        assertEquals(announced, AuthStripTestKit.announcements().size)

        AuthStripTestKit.retry(strip(after)).performClick()

        assertEquals("Retry still re-runs the submit", android.view.View.GONE, strip(after).visibility)
        assertEquals("Enter a valid email address", AuthStripTestKit.helper(after, R.id.txtEmailError))
    }

    @Test
    fun rotation_keepsAStripWithoutRetryHidingRetry() {
        val controller = AuthStripTestKit.visibleController(LoginActivity::class.java)
        controller.get().showFormError("Incorrect email or password. Check them and try again.", retryable = false)

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(android.view.View.VISIBLE, strip(after).visibility)
        assertEquals(android.view.View.GONE, AuthStripTestKit.retry(strip(after)).visibility)
    }

    @Test
    fun rotation_withNoFailure_keepsTheStripHidden() {
        val controller = AuthStripTestKit.visibleController(LoginActivity::class.java)

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(android.view.View.GONE, strip(after).visibility)
    }
}
