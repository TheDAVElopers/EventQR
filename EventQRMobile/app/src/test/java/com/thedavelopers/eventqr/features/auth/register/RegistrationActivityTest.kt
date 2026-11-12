package com.thedavelopers.eventqr.features.auth.register

import android.text.method.PasswordTransformationMethod
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
import com.thedavelopers.eventqr.features.auth.AuthStripTestKit
import org.robolectric.shadows.ShadowToast

/**
 * Robolectric tests for the XML reverted RegistrationActivity: phone
 * normalization, password requirement UI, terms gating
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
    fun phoneInput_plainTenDigits_unchanged() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        phoneInput.setText("9123456789")

        assertEquals("9123456789", phoneInput.text.toString())
    }

    @Test
    fun phoneInput_shortEntry_unchanged() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        phoneInput.setText("123")
        assertEquals("123", phoneInput.text.toString())
    }

    @Test
    fun phoneInput_moreThanTenDigits_cappedToTen() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        phoneInput.setText("123456789012345")

        // No raw maxLength on the field: the full input reaches the normalizer, which
        // strips prefixes and caps the NATIONAL digits at 10 afterwards.
        assertEquals("1234567890", phoneInput.text.toString())
    }

    @Test
    fun phoneInput_fullInternationalPaste_normalizedToNationalDigits() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        phoneInput.setText("+639171234567")

        // +63 stripped on the FULL input, leaving the 10 national digits intact.
        assertEquals("9171234567", phoneInput.text.toString())
    }

    @Test
    fun phoneInput_0063PrefixPaste_normalizedToNationalDigits() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        phoneInput.setText("00639171234567")

        assertEquals("9171234567", phoneInput.text.toString())
    }

    @Test
    fun phoneInput_overLengthInternationalJunk_prefixStrippedThenCapped() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        // 63 + 14 digits: prefix removed first, then the remaining national digits
        // capped at 10 — cap runs after normalization, never on raw input.
        phoneInput.setText("6391712345678901")

        assertEquals("9171234567", phoneInput.text.toString())
    }

    @Test
    fun phoneInput_leadingZeroPastPaste_strippedByNormalizer() {
        val activity = buildActivity()
        val phoneInput = activity.findViewById<EditText>(R.id.edtPhoneNumber)

        // The full 11-digit local number reaches the normalizer; the leading 0 is
        // stripped and all 10 national digits survive.
        phoneInput.setText("09171234567")

        assertEquals("9171234567", phoneInput.text.toString())
    }

    // -- Password requirements UI ------------------------------------------------

    @Test
    fun passwordInput_updatesRequirementsUI() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)

        // Empty password hides the requirements panel.
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layoutPasswordRequirements).visibility)

        passwordInput.setText("Strong1!Pass")

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layoutPasswordRequirements).visibility)
        // The state lives in the mark (a compound drawable), never in a Unicode glyph.
        assertRequirementMet(activity, R.id.txtPasswordLengthRequirement, "At least 8 characters")
        assertRequirementMet(activity, R.id.txtPasswordCapitalRequirement, "One uppercase letter")
        assertRequirementMet(activity, R.id.txtPasswordLowercaseRequirement, "One lowercase letter")
        assertRequirementMet(activity, R.id.txtPasswordNumberRequirement, "One number")
        assertRequirementMet(activity, R.id.txtPasswordSpecialRequirement, "One special character")
    }

    @Test
    fun passwordInput_partialPassword_showsUnmetRequirements() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)

        // "abcdefgh" meets only the length requirement.
        passwordInput.setText("abcdefgh")

        assertRequirementMet(activity, R.id.txtPasswordLengthRequirement, "At least 8 characters")
        assertRequirementUnmet(activity, R.id.txtPasswordCapitalRequirement, "One uppercase letter")
        assertRequirementMet(activity, R.id.txtPasswordLowercaseRequirement, "One lowercase letter")
    }

    private fun assertRequirementMet(activity: RegistrationActivity, id: Int, label: String) {
        assertRequirement(activity, id, label, met = true)
    }

    private fun assertRequirementUnmet(activity: RegistrationActivity, id: Int, label: String) {
        assertRequirement(activity, id, label, met = false)
    }

    /**
     * A rule row carries its label as text, its state in the fixed 16dp leading slot (the
     * `ic_ui_check` tick when met, empty at rest) and a `{rule}, met` / `{rule}, not met`
     * description - no ✓/○ character is ever part of the string.
     */
    private fun assertRequirement(activity: RegistrationActivity, id: Int, label: String, met: Boolean) {
        val view = activity.findViewById<TextView>(id)
        assertEquals(label, view.text.toString())
        assertEquals(
            activity.getString(
                if (met) R.string.ui_password_rule_met else R.string.ui_password_rule_not_met,
                label,
            ),
            view.contentDescription.toString(),
        )
        assertEquals(
            activity.resources.getDimensionPixelSize(R.dimen.icon_sm),
            view.compoundDrawablesRelative[0].bounds.width(),
        )
    }

    @Test
    fun passwordInput_over72Bytes_showsInlineTooLongErrorAndBlocksSubmit() {
        val activity = buildActivity()
        enableSubmit(activity)
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)
        val registerButton = activity.findViewById<Button>(R.id.btnRegister)

        // 4 + 69 = 73 ASCII bytes: meets every class but exceeds BCrypt's 72-byte limit.
        passwordInput.setText("Aa1!" + "a".repeat(69))

        assertEquals(activity.getString(R.string.error_password_too_long), AuthStripTestKit.helper(activity, R.id.txtPasswordError))
        assertNull("no platform balloon", passwordInput.error)
        assertTrue(passwordInput.isActivated)
        assertFalse(registerButton.isEnabled)

        // Exactly 72 bytes is accepted again and the error clears.
        passwordInput.setText("Aa1!" + "a".repeat(68))

        assertNull(AuthStripTestKit.helper(activity, R.id.txtPasswordError))
        assertFalse(passwordInput.isActivated)
        assertTrue(registerButton.isEnabled)
    }

    @Test
    fun passwordInput_multibyteOver72Bytes_isRejectedEvenWhenShortInCharacters() {
        val activity = buildActivity()
        enableSubmit(activity)
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)

        // "é" is 2 bytes in UTF-8: 4 + 40 characters = 84 bytes.
        passwordInput.setText("Aa1!" + "é".repeat(40))

        assertEquals(activity.getString(R.string.error_password_too_long), AuthStripTestKit.helper(activity, R.id.txtPasswordError))
        assertFalse(activity.findViewById<Button>(R.id.btnRegister).isEnabled)
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

        // No lowercase letter: still disabled even with terms checked.
        passwordInput.setText("STRONG1!PASS")
        assertFalse(registerButton.isEnabled)

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
            activity.getString(R.string.error_invalid_phone_prefixed),
            AuthStripTestKit.helper(activity, R.id.txtPhoneError),
        )
        assertNull("no platform balloon", activity.findViewById<EditText>(R.id.edtPhoneNumber).error)
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

        assertEquals("First name is required", AuthStripTestKit.helper(activity, R.id.txtFirstNameError))
        assertNull("no platform balloon", activity.findViewById<EditText>(R.id.edtFirstName).error)
        assertTrue(activity.findViewById<EditText>(R.id.edtFirstName).isActivated)
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
    fun passwordToggle_switchesBothPasswordFieldTransformations() {
        val activity = buildActivity()
        val passwordInput = activity.findViewById<EditText>(R.id.edtPassword)
        val confirmPasswordInput = activity.findViewById<EditText>(R.id.edtConfirmPassword)

        assertTrue(passwordInput.transformationMethod is PasswordTransformationMethod)
        assertTrue(confirmPasswordInput.transformationMethod is PasswordTransformationMethod)

        // Material's password_toggle end icon owns each eye now; each tap unmasks only its field.
        toggleEndIcon(passwordInput)
        assertNull(passwordInput.transformationMethod)
        assertTrue(confirmPasswordInput.transformationMethod is PasswordTransformationMethod)

        toggleEndIcon(confirmPasswordInput)
        assertNull(confirmPasswordInput.transformationMethod)

        toggleEndIcon(passwordInput)
        assertTrue(passwordInput.transformationMethod is PasswordTransformationMethod)
    }

    /** Taps Material's password_toggle end icon on the field's [TextInputLayout] host. */
    private fun toggleEndIcon(input: EditText) {
        var parent: android.view.ViewParent? = input.parent
        while (parent is View) {
            if (parent is com.google.android.material.textfield.TextInputLayout) {
                assertEquals(
                    com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE,
                    parent.endIconMode,
                )
                val icon = parent.findViewById<View>(
                    com.google.android.material.R.id.text_input_end_icon,
                )
                requireNotNull(icon) { "password toggle end icon missing" }.performClick()
                return
            }
            parent = parent.parent
        }
        throw AssertionError("password field has no TextInputLayout host")
    }

    // ---- §5.16 form failure strip (above the sticky footer; never a Retry: creating an account is not safe to repeat)

    private fun strip(activity: RegistrationActivity) = AuthStripTestKit.strip(activity, R.id.errorRegister)

    @Test
    fun theStrip_isHiddenUntilAFailureAndSitsAboveTheFooter() {
        val activity = buildActivity()
        val strip = strip(activity)
        val root = strip.parent as android.view.ViewGroup

        assertEquals(View.GONE, strip.visibility)
        assertTrue(
            "the strip sits between the form and the footer",
            root.indexOfChild(activity.findViewById(R.id.authScroll)) < root.indexOfChild(strip) &&
                root.indexOfChild(strip) < root.indexOfChild(activity.findViewById(R.id.authFooter)),
        )
    }

    @Test
    fun aFailure_showsTheSentenceInTheStripWithoutRetryAndWithoutAToast() {
        val activity = buildActivity()

        activity.showFormError("Couldn't create your account. Your entries are kept, so try again.")

        assertEquals(View.VISIBLE, strip(activity).visibility)
        assertEquals(
            "Couldn't create your account. Your entries are kept, so try again.",
            AuthStripTestKit.message(strip(activity)),
        )
        assertEquals(View.GONE, AuthStripTestKit.retry(strip(activity)).visibility)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun nextSubmit_clearsTheStrip() {
        val activity = buildActivity()
        activity.showFormError("Couldn't create your account. Your entries are kept, so try again.")

        activity.findViewById<Button>(R.id.btnRegister).performClick()

        assertEquals(View.GONE, strip(activity).visibility)
    }

    @Test
    fun everyFailure_isAnnounced_includingAnIdenticalRepeat() {
        AuthStripTestKit.enableAccessibility()
        val activity = AuthStripTestKit.visible(RegistrationActivity::class.java)
        val message = "Couldn't create your account. Your entries are kept, so try again."

        activity.showFormError(message)
        activity.findViewById<Button>(R.id.btnRegister).performClick()
        activity.showFormError(message)

        // The click also fails the phone check on the empty form, which is a failure of its own.
        assertEquals(
            listOf(message, activity.getString(R.string.error_invalid_phone_prefixed), message),
            AuthStripTestKit.announcements(),
        )
    }

    @Test
    fun anApiNamedEmailConflict_isAFieldErrorThatIsFocusedAndAnnounced() {
        AuthStripTestKit.enableAccessibility()
        val activity = AuthStripTestKit.visible(RegistrationActivity::class.java)
        val email = activity.findViewById<EditText>(R.id.edtEmail)
        val message = "An account with this email already exists. Sign in or use a different email."

        activity.showServerFieldError("email", message)

        assertEquals(message, AuthStripTestKit.helper(activity, R.id.txtEmailError))
        assertNull(email.error)
        assertTrue(email.isActivated)
        assertTrue(email.hasFocus())
        assertEquals(View.GONE, strip(activity).visibility)
        assertEquals(listOf(message), AuthStripTestKit.announcements())
    }

    // ---- Field errors: first invalid field takes focus, one announcement, clear on edit

    @Test
    fun severalInvalidFields_focusTheFirstAndAnnounceItOnce() {
        AuthStripTestKit.enableAccessibility()
        val activity = AuthStripTestKit.visible(RegistrationActivity::class.java)
        enableSubmit(activity)
        activity.findViewById<EditText>(R.id.edtPhoneNumber).setText("9171234567")

        activity.findViewById<Button>(R.id.btnRegister).performClick()

        assertEquals("First name is required", AuthStripTestKit.helper(activity, R.id.txtFirstNameError))
        assertEquals("Last name is required", AuthStripTestKit.helper(activity, R.id.txtLastNameError))
        assertEquals("Enter a valid email address", AuthStripTestKit.helper(activity, R.id.txtEmailError))
        assertTrue(activity.findViewById<EditText>(R.id.edtFirstName).hasFocus())
        assertEquals(listOf("First name is required"), AuthStripTestKit.announcements())
    }

    @Test
    fun editingAField_clearsItsOwnErrorOnly() {
        val activity = buildActivity()
        enableSubmit(activity)
        activity.findViewById<EditText>(R.id.edtPhoneNumber).setText("9171234567")
        activity.findViewById<Button>(R.id.btnRegister).performClick()

        activity.findViewById<EditText>(R.id.edtFirstName).setText("Jo")

        assertNull(AuthStripTestKit.helper(activity, R.id.txtFirstNameError))
        assertFalse(activity.findViewById<EditText>(R.id.edtFirstName).isActivated)
        assertEquals("Last name is required", AuthStripTestKit.helper(activity, R.id.txtLastNameError))
    }

    // ---- Rotation

    @Test
    fun rotation_keepsFieldErrors_andTheyStaySilent() {
        AuthStripTestKit.enableAccessibility()
        val controller = AuthStripTestKit.visibleController(RegistrationActivity::class.java)
        val before = controller.get()
        enableSubmit(before)
        before.findViewById<EditText>(R.id.edtPhoneNumber).setText("9171234567")
        before.findViewById<Button>(R.id.btnRegister).performClick()
        before.findViewById<EditText>(R.id.edtFirstName).setText("Jo")
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertNull("an edited field stays clear", AuthStripTestKit.helper(after, R.id.txtFirstNameError))
        assertEquals("Last name is required", AuthStripTestKit.helper(after, R.id.txtLastNameError))
        assertEquals("Enter a valid email address", AuthStripTestKit.helper(after, R.id.txtEmailError))
        assertTrue(after.findViewById<EditText>(R.id.edtLastName).isActivated)
        assertNull(after.findViewById<EditText>(R.id.edtLastName).error)
        assertEquals("a restore is not a new failure", announced, AuthStripTestKit.announcements().size)
    }

    @Test
    fun rotation_keepsTheStrip_withoutRetry_andWithoutAnnouncingAgain() {
        AuthStripTestKit.enableAccessibility()
        val controller = AuthStripTestKit.visibleController(RegistrationActivity::class.java)
        val message = "Couldn't create your account. Your entries are kept, so try again."
        controller.get().showFormError(message)
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(View.VISIBLE, strip(after).visibility)
        assertEquals(message, AuthStripTestKit.message(strip(after)))
        assertEquals(View.GONE, AuthStripTestKit.retry(strip(after)).visibility)
        assertEquals(announced, AuthStripTestKit.announcements().size)
    }

    @Test
    fun rotation_withNoFailure_keepsTheStripHidden() {
        val after = AuthStripTestKit.rotate(AuthStripTestKit.visibleController(RegistrationActivity::class.java))

        assertEquals(View.GONE, strip(after).visibility)
    }
}
