package com.thedavelopers.eventqr.features.auth.login

import android.text.InputType
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
    fun clickSignIn_invalidCredentials_showsFieldErrors() {
        val activity = buildActivity()
        activity.findViewById<EditText>(R.id.edtEmail).setText("bad-email")
        activity.findViewById<EditText>(R.id.edtPassword).setText("123")

        activity.findViewById<android.view.View>(R.id.btnSignIn).performClick()

        assertEquals("Enter a valid email address", activity.findViewById<EditText>(R.id.edtEmail).error.toString())
        assertEquals("Password must be at least 8 characters", activity.findViewById<EditText>(R.id.edtPassword).error.toString())
        // No navigation on invalid input.
        assertNull(shadowOf(activity).peekNextStartedActivity())
        assertEquals("Sign In", activity.findViewById<android.widget.Button>(R.id.btnSignIn).text.toString())
    }

    @Test
    fun clickSignIn_validEmailInvalidPassword_clearsEmailErrorOnly() {
        val activity = buildActivity()
        activity.findViewById<EditText>(R.id.edtEmail).setText("user@example.com")
        activity.findViewById<EditText>(R.id.edtPassword).setText("123")

        activity.findViewById<android.view.View>(R.id.btnSignIn).performClick()

        assertNull(activity.findViewById<EditText>(R.id.edtEmail).error)
        assertEquals("Password must be at least 8 characters", activity.findViewById<EditText>(R.id.edtPassword).error.toString())
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
    fun passwordToggle_toggleTheEyeSwitchesInputType() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)
        layoutInput(passwordInput)
        val passwordInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val visibleInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        assertEquals(passwordInputType, passwordInput.inputType)

        // Touch the drawable-end zone (rawX beyond right - compoundPaddingEnd).
        dispatchActionUp(passwordInput, 10_000f)
        assertEquals(visibleInputType, passwordInput.inputType)

        dispatchActionUp(passwordInput, 10_000f)
        assertEquals(passwordInputType, passwordInput.inputType)
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

    private fun dispatchActionUp(input: EditText, rawX: Float) {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, rawX, 100f, 0)
        try {
            input.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}