package com.thedavelopers.eventqr.features.auth.resetpassword

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.AuthStripTestKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * §5.16 form failure strip on the new-password step: above the sticky footer, never a Retry (the
 * one-time reset code may already be spent), announced on every failure.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class NewPasswordActivityTest {

    private val intent = Intent()
        .putExtra(NewPasswordActivity.EXTRA_EMAIL, "a@b.com")
        .putExtra(NewPasswordActivity.EXTRA_CODE, "123456")

    private fun build(): NewPasswordActivity =
        Robolectric.buildActivity(NewPasswordActivity::class.java, intent).create().get()

    private fun strip(activity: NewPasswordActivity) = AuthStripTestKit.strip(activity, R.id.errorNewPassword)

    @Test
    fun theStrip_isHiddenUntilAFailureAndSitsAboveTheFooter() {
        val activity = build()
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
        val activity = build()

        activity.showFormError("Couldn't reset your password. Your entries are kept, so try again.")

        assertEquals(View.VISIBLE, strip(activity).visibility)
        assertEquals(
            "Couldn't reset your password. Your entries are kept, so try again.",
            AuthStripTestKit.message(strip(activity)),
        )
        assertEquals(View.GONE, AuthStripTestKit.retry(strip(activity)).visibility)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun nextSubmit_clearsTheStrip() {
        val activity = build()
        activity.showFormError("Couldn't reset your password. Your entries are kept, so try again.")

        activity.findViewById<Button>(R.id.btnResetPassword).performClick()

        assertEquals(View.GONE, strip(activity).visibility)
    }

    @Test
    fun everyFailure_isAnnounced_includingAnIdenticalRepeat() {
        AuthStripTestKit.enableAccessibility()
        val activity = Robolectric.buildActivity(NewPasswordActivity::class.java, intent)
            .create().start().resume().visible().get()
        val message = "Couldn't reset your password. Your entries are kept, so try again."

        activity.showFormError(message)
        activity.findViewById<Button>(R.id.btnResetPassword).performClick()
        activity.showFormError(message)

        // The click also fails validation on the empty form, which is a failure of its own.
        assertEquals(
            listOf(message, activity.getString(R.string.password_policy_hint), message),
            AuthStripTestKit.announcements(),
        )
    }

    // ---- Field errors: focus, announce once, clear on edit, rotation

    private fun visibleController() =
        Robolectric.buildActivity(NewPasswordActivity::class.java, intent).create().start().resume().visible()

    @Test
    fun severalFieldErrors_focusTheFirstAndAnnounceItOnce() {
        AuthStripTestKit.enableAccessibility()
        val activity = visibleController().get()

        activity.showPasswordError("Password policy")
        activity.showConfirmPasswordError("Passwords do not match")

        assertTrue(activity.findViewById<EditText>(R.id.edtNewPassword).hasFocus())
        assertEquals(listOf("Password policy"), AuthStripTestKit.announcements())
        assertNull(activity.findViewById<EditText>(R.id.edtNewPassword).error)
    }

    @Test
    fun editingTheConfirmField_clearsOnlyItsOwnError() {
        val activity = build()
        activity.showPasswordError("Password policy")
        activity.showConfirmPasswordError("Passwords do not match")

        activity.findViewById<EditText>(R.id.edtConfirmPassword).setText("x")

        assertNull(AuthStripTestKit.helper(activity, R.id.txtConfirmPasswordError))
        assertEquals("Password policy", AuthStripTestKit.helper(activity, R.id.txtNewPasswordError))
    }

    @Test
    fun rotation_keepsFieldErrors_andTheyStaySilent() {
        AuthStripTestKit.enableAccessibility()
        val controller = visibleController()
        val before = controller.get()
        before.showPasswordError("Password policy")
        before.showConfirmPasswordError("Passwords do not match")
        before.findViewById<EditText>(R.id.edtConfirmPassword).setText("x")
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertNull("an edited field stays clear", AuthStripTestKit.helper(after, R.id.txtConfirmPasswordError))
        assertEquals("Password policy", AuthStripTestKit.helper(after, R.id.txtNewPasswordError))
        assertTrue(after.findViewById<EditText>(R.id.edtNewPassword).isActivated)
        assertEquals("a restore is not a new failure", announced, AuthStripTestKit.announcements().size)
    }

    @Test
    fun rotation_keepsTheStrip_withoutRetry_andWithoutAnnouncingAgain() {
        AuthStripTestKit.enableAccessibility()
        val controller = visibleController()
        val message = "Couldn't reset your password. Your entries are kept, so try again."
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
        val after = AuthStripTestKit.rotate(visibleController())

        assertEquals(View.GONE, strip(after).visibility)
    }
}
