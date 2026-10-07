package com.thedavelopers.eventqr.features.auth.register

import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
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
 * Robolectric tests for the XML reverted RegistrationActivity: phone
 * normalization + live counter, password requirement UI, terms gating
 * (TestFlow REG-9/REG-10), presenter field-error wiring and navigation.
 * The successful account creation performs a network call via AuthRepository
 * and is not unit-testable (see coverage notes).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RegistrationActivityTest {

    private fun buildActivity(): RegistrationActivity =
        Robolectric.buildActivity(RegistrationActivity::class.java).create().get()

    private fun enableSubmit(activity: RegistrationActivity) {
        activity.findViewById<EditText>(R.id.edtPassword).setText("Strong1!Pass")
        activity.findViewById<CheckBox>(R.id.chkTerms).isChecked = true
    }

    // -- Phone normalization ------------------------------------------------------

    @Test
    fun phoneInput_plainTenDigits_unchangedWithFullCounter() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        phoneInput.setText("9123456789")

        assertEquals("9123456789", phoneInput.text.toString())
        assertEquals("10/10", activity.findViewById<TextView>(R.id.txtPhoneCounter).text.toString())
    }

    @Test
    fun phoneInput_shortEntry_updatesLiveCounter() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)
        val counter = activity.findViewById<TextView>(R.id.txtPhoneCounter)

        phoneInput.setText("123")
        assertEquals("123", phoneInput.text.toString())
        assertEquals("3/10", counter.text.toString())
    }

    @Test
    fun phoneInput_moreThanTenDigits_cappedToTen() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        phoneInput.setText("123456789012345")

        // android:maxLength="10" caps the field before the watcher runs.
        assertEquals("1234567890", phoneInput.text.toString())
        assertEquals("10/10", activity.findViewById<TextView>(R.id.txtPhoneCounter).text.toString())
    }

    @Test
    fun phoneInput_leadingZeroPastPaste_strippedByNormalizer() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        // maxLength caps "09171234567" to "0917123456"; the normalizer then drops
        // the leading 0 (asserted values reflect Robolectric's filter pipeline).
        phoneInput.setText("09171234567")

        assertEquals("917123456", phoneInput.text.toString())
        assertEquals("9/10", activity.findViewById<TextView>(R.id.txtPhoneCounter).text.toString())
    }

    // -- Password requirements UI ------------------------------------------------

    @Test
    fun passwordInput_updatesRequirementsAndStrengthUI() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)

        // Empty password hides the requirements panel.
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layoutPasswordRequirements).visibility)

        passwordInput.setText("Strong1!Pass")

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layoutPasswordRequirements).visibility)
        assertEquals("✓ At least 8 characters", activity.findViewById<TextView>(R.id.txtPasswordLengthRequirement).text.toString())
        assertEquals("✓ One uppercase letter", activity.findViewById<TextView>(R.id.txtPasswordCapitalRequirement).text.toString())
        assertEquals("✓ One number", activity.findViewById<TextView>(R.id.txtPasswordNumberRequirement).text.toString())
        assertEquals("✓ One special character", activity.findViewById<TextView>(R.id.txtPasswordSpecialRequirement).text.toString())
        assertEquals("Strong", activity.findViewById<TextView>(R.id.txtPasswordStrength).text.toString())
    }

    @Test
    fun passwordInput_partialPassword_showsUnmetRequirementsAndWeakStrength() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)

        // "abcdefgh" meets only the length requirement.
        passwordInput.setText("abcdefgh")

        assertEquals("✓ At least 8 characters", activity.findViewById<TextView>(R.id.txtPasswordLengthRequirement).text.toString())
        assertEquals("○ One uppercase letter", activity.findViewById<TextView>(R.id.txtPasswordCapitalRequirement).text.toString())
        assertEquals("Weak", activity.findViewById<TextView>(R.id.txtPasswordStrength).text.toString())
    }

    // -- Terms gating (TestFlow REG-9/REG-10) -------------------------------------

    @Test
    fun registerButton_disabledUntilPasswordMeetsAllRequirementsAndTermsChecked() {
        val activity = buildActivity()
        val registerButton = activity.findViewById<Button>(R.id.btnRegister)
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)
        val termsCheckBox = activity.findViewById<CheckBox>(R.id.chkTerms)

        assertFalse(registerButton.isEnabled)

        // Password meets all requirements, terms still unchecked.
        passwordInput.setText("Strong1!Pass")
        assertFalse(registerButton.isEnabled)

        // Terms checked with a valid password: enabled.
        termsCheckBox.isChecked = true
        assertTrue(registerButton.isEnabled)

        // Weakening the password disables it again even with terms checked.
        passwordInput.setText("weak")
        assertFalse(registerButton.isEnabled)

        // Restoring the full-strength password re-enables it.
        passwordInput.setText("Strong1!Pass")
        assertTrue(registerButton.isEnabled)
    }

    // -- Field error wiring -------------------------------------------------------

    @Test
    fun clickRegister_shortPhone_showsActivityLevelErrorWithoutSubmitting() {
        val activity = buildActivity()
        enableSubmit(activity)
        activity.findViewById<EditText>(R.id.edtPhoneNumber).setText("12345")

        activity.findViewById<Button>(R.id.btnRegister).performClick()

        assertEquals(
            "Enter valid 10-digit mobile number",
            activity.findViewById<EditText>(R.id.edtPhoneNumber).error.toString(),
        )
        assertNull(shadowOf(activity).peekNextStartedActivity())
    }

    @Test
    fun clickRegister_missingFirstName_showsPresenterErrorFieldWiring() {
        val activity = buildActivity()
        enableSubmit(activity)
        activity.findViewById<EditText>(R.id.edtPhoneNumber).setText("9171234567")
        activity.findViewById<EditText>(R.id.edtLastName).setText("Doe")
        activity.findViewById<EditText>(R.id.edtEmail).setText("user@example.com")
        activity.findViewById<EditText>(R.id.edtConfirmPassword).setText("Strong1!Pass")
        // firstName left blank on purpose.

        val registerButton = activity.findViewById<Button>(R.id.btnRegister)
        assertTrue(registerButton.isEnabled)
        registerButton.performClick()

        assertEquals(
            "First name is required",
            activity.findViewById<EditText>(R.id.edtFirstName).error.toString(),
        )
        assertNull(shadowOf(activity).peekNextStartedActivity())
    }

    // -- Navigation ---------------------------------------------------------------

    @Test
    fun clickSignIn_navigatesToLoginAndFinishes() {
        val activity = buildActivity()

        activity.findViewById<View>(R.id.btnSignIn).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(LoginActivity::class.java.name, intent.component?.className)
        assertTrue(activity.isFinishing)
    }

    // -- Password visibility toggle -----------------------------------------------

    @Test
    fun passwordToggle_switchesBothPasswordFieldInputTypes() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)
        val confirmPasswordInput = activity.findViewById<EditText>(R.id.edtConfirmPassword)
        layoutInput(passwordInput)
        layoutInput(confirmPasswordInput)
        val hidden = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val visible = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        assertEquals(hidden, passwordInput.inputType)
        assertEquals(hidden, confirmPasswordInput.inputType)

        dispatchActionUp(passwordInput, 10_000f)
        assertEquals(visible, passwordInput.inputType)
        assertEquals(hidden, confirmPasswordInput.inputType)

        dispatchActionUp(confirmPasswordInput, 10_000f)
        assertEquals(visible, confirmPasswordInput.inputType)

        dispatchActionUp(passwordInput, 10_000f)
        assertEquals(hidden, passwordInput.inputType)
    }

    private fun layoutInput(input: EditText) {
        input.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
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