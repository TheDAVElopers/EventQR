package com.thedavelopers.eventqr.features.attendee

import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the edit-profile phone field: the layout must not truncate
 * pasted input before normalizePhoneDigits() runs (same bug as activity_signup.xml),
 * validation reuses Validators on the assembled E.164 value, and the Material counter
 * (counterMaxLength=10) reflects the post-normalization national length.
 * The save network call is not unit-testable (see registration test coverage notes).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AttendeeEditProfileActivityTest {

    private fun buildActivity(): AttendeeEditProfileActivity =
        Robolectric.buildActivity(AttendeeEditProfileActivity::class.java).create().get()

    private fun phoneInput(activity: AttendeeEditProfileActivity): EditText =
        activity.findViewById(R.id.edtPhone)

    // -- Phone normalization ------------------------------------------------------

    @Test
    fun phoneInput_fullInternationalPaste_normalizedToNationalDigits() {
        val activity = buildActivity()

        phoneInput(activity).setText("+639171234567")

        assertEquals("9171234567", phoneInput(activity).text.toString())
    }

    @Test
    fun phoneInput_localElevenDigitPaste_normalizedToNationalDigits() {
        val activity = buildActivity()

        phoneInput(activity).setText("09171234567")

        assertEquals("9171234567", phoneInput(activity).text.toString())
    }

    @Test
    fun phoneInput_0063PrefixPaste_normalizedToNationalDigits() {
        val activity = buildActivity()

        phoneInput(activity).setText("00639171234567")

        assertEquals("9171234567", phoneInput(activity).text.toString())
    }

    @Test
    fun phoneInput_overLengthJunk_cappedAfterNormalization() {
        val activity = buildActivity()

        // No raw maxLength: full input reaches the normalizer, which caps the national
        // digits at 10 after prefix stripping.
        phoneInput(activity).setText("123456789012345")

        assertEquals("1234567890", phoneInput(activity).text.toString())
    }

    @Test
    fun phoneInput_plainTenDigits_unchanged() {
        val activity = buildActivity()

        phoneInput(activity).setText("9123456789")

        assertEquals("9123456789", phoneInput(activity).text.toString())
    }

    // -- Counter / limit UI -------------------------------------------------------

    @Test
    fun phoneCounter_enabledWithTenAndNeverExceedsNationalLength() {
        val activity = buildActivity()
        val tilPhone = activity.findViewById<TextInputLayout>(R.id.tilPhone)

        assertEquals(true, tilPhone.isCounterEnabled)
        assertEquals(10, tilPhone.counterMaxLength)

        // The counter mirrors the field text; the normalizer keeps it at <= 10 digits
        // so it can never report a value above 10/10.
        phoneInput(activity).setText("+639171234567")
        assertEquals(10, phoneInput(activity).text.length)

        phoneInput(activity).setText("123456789012345")
        assertEquals(10, phoneInput(activity).text.length)
    }

    // -- Validation wiring --------------------------------------------------------

    @Test
    fun save_shortPhone_showsValidationErrorWithoutSubmitting() {
        val activity = buildActivity()
        phoneInput(activity).setText("123")

        activity.findViewById<android.widget.Button>(R.id.btnSaveChanges).performClick()

        assertEquals(
            activity.getString(R.string.error_invalid_phone_prefixed),
            phoneInput(activity).error.toString(),
        )
    }

    @Test
    fun save_blankPhone_showsRequiredError() {
        val activity = buildActivity()

        activity.findViewById<android.widget.Button>(R.id.btnSaveChanges).performClick()

        assertEquals("Phone number is required.", phoneInput(activity).error.toString())
    }

    @Test
    fun save_localPasteMatchingSessionPhone_passesValidationWithoutChanges() {
        // Session phone prefills the field normalized; pasting the local 11-digit form
        // must normalize to the same value so validation passes and no change (and no
        // network save) is detected.
        val sessionManager = SessionManager(ApplicationProvider.getApplicationContext())
        sessionManager.updateProfile("John Doe", "+639171234567", "user@example.com")
        val activity = buildActivity()

        assertEquals("9171234567", phoneInput(activity).text.toString())

        phoneInput(activity).setText("09171234567")
        activity.findViewById<android.widget.Button>(R.id.btnSaveChanges).performClick()

        assertNull(phoneInput(activity).error)
        assertEquals("9171234567", phoneInput(activity).text.toString())
    }
}
