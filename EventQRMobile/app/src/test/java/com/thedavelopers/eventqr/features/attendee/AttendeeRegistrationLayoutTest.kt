package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Item 10: the registration screen shows the registrant's details as a read-only spec table with
 * one route to fix them (`Edit in profile`). The disabled EditTexts are gone - a control the user
 * cannot use is a dead instruction.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AttendeeRegistrationLayoutTest {

    private fun context(): Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_EventQR)

    private fun layout() = LayoutInflater.from(context())
        .inflate(R.layout.activity_event_registration, LinearLayout(context()), false)

    @Test
    fun editLinkIsTheOnlyRouteToChangeDetails() {
        val context = context()
        val root = layout()
        val link = root.findViewById<TextView>(R.id.btnEditDetailsInProfile)

        assertEquals(context.getString(R.string.ui_attendee_edit_in_profile), link.text.toString())
        // WCAG 2.5.3: the accessible name is the visible label, so voice control can say it.
        // No separate contentDescription overrides the text.
        assertEquals(null, link.contentDescription)
        assertEquals(true, link.isClickable)
        assertEquals(
            "the link keeps the 48dp touch target",
            context.resources.getDimensionPixelSize(R.dimen.ui_touch_min),
            link.layoutParams.height,
        )
    }

    @Test
    fun detailsAreDisplayRowsNotDisabledFields() {
        val root = layout()

        listOf(
            R.id.rowRegistrationFullName,
            R.id.rowRegistrationEmail,
            R.id.rowRegistrationPhone,
        ).forEach { rowId -> assertNotNull("missing detail row $rowId", root.findViewById<LinearLayout>(rowId)) }

        listOf(
            R.id.dividerRegistrationFullName,
            R.id.dividerRegistrationEmail,
        ).forEach { dividerId -> assertNotNull("missing divider $dividerId", root.findViewById<TextView>(dividerId)) }

        listOf(
            R.id.txtRegistrationFullName,
            R.id.txtRegistrationEmail,
            R.id.txtRegistrationPhone,
        ).forEach { valueId ->
            val value = root.findViewById<TextView>(valueId)
            assertNotNull("missing detail value $valueId", value)
            assertFalse("$valueId must be a value, not an editable field", value is EditText)
        }

        listOf(
            "edtRegistrationFullName",
            "edtRegistrationEmail",
            "edtRegistrationPhone",
        ).forEach { gone ->
            assertEquals(
                "$gone must be deleted: the screen cannot edit the profile",
                0,
                root.resources.getIdentifier(gone, "id", root.context.packageName),
            )
        }
    }

    // A phone added on the edit screen has to reach this form: the prefill is reloaded on every
    // return, not only on the first create.
    @Test
    fun returningToTheFormRefreshesTheProfilePrefillAgain() {
        val controller = Robolectric.buildActivity(AttendeeRegistrationActivity::class.java)
            .create().start().resume()
        val activity = controller.get()

        assertEquals(1, activity.profilePrefillRefreshes)

        controller.pause().resume()

        assertEquals(2, activity.profilePrefillRefreshes)
    }

    // The form must not open with an error: the gate stays silent until the user touches something
    // that can fix it (terms, submit, edit-profile).
    @Test
    fun theGateStaysSilentUntilTheFirstInteraction() {
        val activity = Robolectric.buildActivity(AttendeeRegistrationActivity::class.java).create().get()
        val inline = activity.findViewById<TextView>(R.id.txtRegistrationInlineError)
        val required = activity.findViewById<TextView>(R.id.txtRegistrationPhoneRequired)

        activity.applyProfilePrefill("Ana Cruz", "ana@test.com", "")

        assertEquals(View.GONE, inline.visibility)
        assertEquals("no red marker before the first interaction", View.GONE, required.visibility)

        activity.findViewById<View>(R.id.rowRegistrationTerms).performClick()

        assertEquals(View.VISIBLE, inline.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_add_phone_to_continue), inline.text.toString())
    }

    // The primary is disabled with no explanation otherwise: the inline line names the first thing
    // the gate is waiting for, next to the control that is blocked (§5.11).
    @Test
    fun theDisabledSubmitNamesTheFirstMissingFieldInline() {
        val activity = Robolectric.buildActivity(AttendeeRegistrationActivity::class.java).create().get()
        val inline = activity.findViewById<TextView>(R.id.txtRegistrationInlineError)
        val required = activity.findViewById<TextView>(R.id.txtRegistrationPhoneRequired)
        val submit = activity.findViewById<Button>(R.id.btnSubmitRegistration)

        activity.applyProfilePrefill("Ana Cruz", "ana@test.com", "")
        activity.findViewById<View>(R.id.rowRegistrationTerms).performClick()

        assertEquals(View.VISIBLE, inline.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_add_phone_to_continue), inline.text.toString())
        assertEquals("the phone row carries the Required marker", View.VISIBLE, required.visibility)
        assertFalse("a missing phone blocks submit", submit.isEnabled)
    }

    // Tapping the profile shortcut is an interaction too: the form answers with the next missing
    // field instead of staying silent after the user has tried to fix the gate.
    @Test
    fun tappingEditInProfileRevealsTheGate() {
        val activity = Robolectric.buildActivity(AttendeeRegistrationActivity::class.java).create().get()
        val inline = activity.findViewById<TextView>(R.id.txtRegistrationInlineError)

        activity.applyProfilePrefill("Ana Cruz", "ana@test.com", "")
        activity.findViewById<View>(R.id.btnEditDetailsInProfile).performClick()

        assertEquals(View.VISIBLE, inline.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_add_phone_to_continue), inline.text.toString())
    }

    // One 48dp row: the sentence or the row toggles the mark, and checking it is the last gate.
    @Test
    fun theTermsRowTogglesTheMarkAndUnblocksSubmit() {
        val activity = Robolectric.buildActivity(AttendeeRegistrationActivity::class.java).create().get()
        val inline = activity.findViewById<TextView>(R.id.txtRegistrationInlineError)
        val checkbox = activity.findViewById<CheckBox>(R.id.chkRegistrationTerms)
        val submit = activity.findViewById<Button>(R.id.btnSubmitRegistration)

        activity.applyProfilePrefill("Ana Cruz", "ana@test.com", "+639171234567")

        assertEquals("no error before the user acts", View.GONE, inline.visibility)
        assertFalse(submit.isEnabled)
        assertFalse(checkbox.isChecked)

        activity.findViewById<View>(R.id.rowRegistrationTerms).performClick()

        assertTrue(checkbox.isChecked)
        assertTrue(submit.isEnabled)
        assertEquals(View.GONE, inline.visibility)
    }
}
