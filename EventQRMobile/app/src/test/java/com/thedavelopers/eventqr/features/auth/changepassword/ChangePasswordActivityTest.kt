package com.thedavelopers.eventqr.features.auth.changepassword

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import com.thedavelopers.eventqr.features.auth.AuthStripTestKit
import org.robolectric.shadows.ShadowToast

/** The auth fields drive their 2dp error stroke and their inline line from one call (§5.2). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ChangePasswordActivityTest {

    @Test
    fun showCurrentPasswordError_turnsTheErrorStrokeAndUsesTheInlineLineInsteadOfTheTooltip() {
        val activity = Robolectric.buildActivity(ChangePasswordActivity::class.java).create().get()
        val current = activity.findViewById<EditText>(R.id.edtCurrentPassword)
        val errorLine = activity.findViewById<TextView>(R.id.txtCurrentPasswordError)

        activity.showCurrentPasswordError("Current password is incorrect")

        // bg_ui_field keys its 2dp ui_error stroke off state_activated; the platform bubble must stay off.
        assertNull(current.error)
        assertTrue(current.isActivated)
        assertEquals(View.VISIBLE, errorLine.visibility)
        assertEquals("Current password is incorrect", errorLine.text.toString())

        activity.showCurrentPasswordError(null)

        assertFalse(current.isActivated)
        assertEquals(View.GONE, errorLine.visibility)
    }

    @Test
    fun showNewAndConfirmErrors_useTheirOwnLineAndStroke() {
        val activity = Robolectric.buildActivity(ChangePasswordActivity::class.java).create().get()
        val newPassword = activity.findViewById<EditText>(R.id.edtNewPassword)
        val confirmPassword = activity.findViewById<EditText>(R.id.edtConfirmPassword)
        val newError = activity.findViewById<TextView>(R.id.txtNewPasswordError)
        val confirmError = activity.findViewById<TextView>(R.id.txtConfirmPasswordError)

        activity.showNewPasswordError("Passwords do not match")
        activity.showConfirmPasswordError("Passwords do not match")

        assertNull(newPassword.error)
        assertNull(confirmPassword.error)
        assertTrue(newPassword.isActivated)
        assertTrue(confirmPassword.isActivated)
        assertEquals(View.VISIBLE, newError.visibility)
        assertEquals(View.VISIBLE, confirmError.visibility)
        assertEquals("Passwords do not match", newError.text.toString())
        assertEquals("Passwords do not match", confirmError.text.toString())
    }

    // ---- §5.16 form failure strip (above the sticky footer; never a Retry: a password change is not safe to repeat)

    private fun build(): ChangePasswordActivity =
        Robolectric.buildActivity(ChangePasswordActivity::class.java).create().get()

    private fun strip(activity: ChangePasswordActivity) = AuthStripTestKit.strip(activity, R.id.errorChangePassword)

    @Test
    fun theStrip_isHiddenUntilAFailureAndSitsAboveTheFooter() {
        val activity = build()
        val strip = strip(activity)
        val root = strip.parent as android.view.ViewGroup

        assertEquals(View.GONE, strip.visibility)
        assertTrue(
            "the strip sits between the form and the footer",
            root.indexOfChild(activity.findViewById(R.id.formScroll)) < root.indexOfChild(strip) &&
                root.indexOfChild(strip) < root.indexOfChild(activity.findViewById(R.id.authFooter)),
        )
    }

    @Test
    fun aFailure_showsTheSentenceInTheStripWithoutRetryAndWithoutAToast() {
        val activity = build()

        activity.showFormError("Couldn't change your password. Your entries are kept, so try again.")

        assertEquals(View.VISIBLE, strip(activity).visibility)
        assertEquals(
            "Couldn't change your password. Your entries are kept, so try again.",
            AuthStripTestKit.message(strip(activity)),
        )
        assertEquals(View.GONE, AuthStripTestKit.retry(strip(activity)).visibility)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun nextSubmit_clearsTheStrip() {
        val activity = build()
        activity.showFormError("Couldn't change your password. Your entries are kept, so try again.")

        activity.findViewById<android.widget.Button>(R.id.btnChangePassword).performClick()

        assertEquals(View.GONE, strip(activity).visibility)
    }

    @Test
    fun everyFailure_isAnnounced_includingAnIdenticalRepeat() {
        AuthStripTestKit.enableAccessibility()
        val activity = AuthStripTestKit.visible(ChangePasswordActivity::class.java)
        val message = "Couldn't change your password. Your entries are kept, so try again."

        activity.showFormError(message)
        activity.findViewById<android.widget.Button>(R.id.btnChangePassword).performClick()
        activity.showFormError(message)

        // The click also fails validation on the empty form, which is a failure of its own.
        assertEquals(listOf(message, "Enter your current password", message), AuthStripTestKit.announcements())
    }

    // ---- Field errors: focus, announce once, clear on edit, rotation

    @Test
    fun severalFieldErrors_focusTheFirstAndAnnounceItOnce() {
        AuthStripTestKit.enableAccessibility()
        val activity = AuthStripTestKit.visible(ChangePasswordActivity::class.java)

        activity.showCurrentPasswordError("Enter your current password")
        activity.showConfirmPasswordError("Passwords do not match")

        assertTrue(activity.findViewById<EditText>(R.id.edtCurrentPassword).hasFocus())
        assertEquals(listOf("Enter your current password"), AuthStripTestKit.announcements())
    }

    @Test
    fun editingAField_clearsItsOwnErrorOnly() {
        val activity = build()
        activity.showCurrentPasswordError("Enter your current password")
        activity.showConfirmPasswordError("Passwords do not match")

        activity.findViewById<EditText>(R.id.edtCurrentPassword).setText("x")

        assertNull(AuthStripTestKit.helper(activity, R.id.txtCurrentPasswordError))
        assertFalse(activity.findViewById<EditText>(R.id.edtCurrentPassword).isActivated)
        assertEquals("Passwords do not match", AuthStripTestKit.helper(activity, R.id.txtConfirmPasswordError))
    }

    @Test
    fun rotation_keepsFieldErrors_andTheyStaySilent() {
        AuthStripTestKit.enableAccessibility()
        val controller = AuthStripTestKit.visibleController(ChangePasswordActivity::class.java)
        val before = controller.get()
        before.showCurrentPasswordError("Enter your current password")
        before.showConfirmPasswordError("Passwords do not match")
        before.findViewById<EditText>(R.id.edtCurrentPassword).setText("x")
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertNull("an edited field stays clear", AuthStripTestKit.helper(after, R.id.txtCurrentPasswordError))
        assertEquals("Passwords do not match", AuthStripTestKit.helper(after, R.id.txtConfirmPasswordError))
        assertTrue(after.findViewById<EditText>(R.id.edtConfirmPassword).isActivated)
        assertNull(after.findViewById<EditText>(R.id.edtConfirmPassword).error)
        assertEquals("a restore is not a new failure", announced, AuthStripTestKit.announcements().size)
    }

    @Test
    fun rotation_keepsTheStrip_withoutRetry_andWithoutAnnouncingAgain() {
        AuthStripTestKit.enableAccessibility()
        val controller = AuthStripTestKit.visibleController(ChangePasswordActivity::class.java)
        val message = "Couldn't change your password. Your entries are kept, so try again."
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
        val after = AuthStripTestKit.rotate(AuthStripTestKit.visibleController(ChangePasswordActivity::class.java))

        assertEquals(View.GONE, strip(after).visibility)
    }
}
