package com.thedavelopers.eventqr.features.auth.forgotpassword

import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
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
 * Robolectric tests for the XML reverted ForgotPasswordActivity: invalid-email
 * handling (form stays, error shown) and the three back-to-sign-in entry points.
 * The send-code network call is not unit-testable (see coverage notes).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ForgotPasswordActivityTest {

    private fun buildActivity(): ForgotPasswordActivity =
        Robolectric.buildActivity(ForgotPasswordActivity::class.java).create().get()

    @Test
    fun clickSend_code_invalidEmail_showsErrorAndKeepsForm() {
        val activity = buildActivity()
        activity.findViewById<EditText>(R.id.editEmail).setText("not-an-email")

        activity.findViewById<android.widget.Button>(R.id.btnSendCode).performClick()

        assertEquals("Enter a valid email address", AuthStripTestKit.helper(activity, R.id.txtEmailError))
        assertNull("no platform balloon", activity.findViewById<EditText>(R.id.editEmail).error)
        assertTrue(activity.findViewById<EditText>(R.id.editEmail).isActivated)
        assertEquals(
            View.VISIBLE,
            activity.findViewById<LinearLayout>(R.id.layoutForm).visibility,
        )
        assertNull(shadowOf(activity).peekNextStartedActivity())
    }

    @Test
    fun clickSend_code_emptyEmail_showsErrorAndNoNavigation() {
        val activity = buildActivity()

        activity.findViewById<android.widget.Button>(R.id.btnSendCode).performClick()

        assertEquals("Enter a valid email address", AuthStripTestKit.helper(activity, R.id.txtEmailError))
        assertNull(shadowOf(activity).peekNextStartedActivity())
    }

    @Test
    fun clickBackTextLink_navigatesToLogin() {
        val activity = buildActivity()

        activity.findViewById<View>(R.id.tvBackToSignIn).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(LoginActivity::class.java.name, intent.component?.className)
        assertTrue(activity.isFinishing)
    }

    // ---- §5.16 form failure strip (directly above Send code; Retry only for a transient failure)

    private fun strip(activity: ForgotPasswordActivity) = AuthStripTestKit.strip(activity, R.id.errorForgotPassword)

    @Test
    fun theStrip_isHiddenUntilAFailureAndSitsDirectlyAboveSendCode() {
        val activity = buildActivity()
        val strip = strip(activity)
        val parent = strip.parent as android.view.ViewGroup

        assertEquals(View.GONE, strip.visibility)
        assertEquals(parent.indexOfChild(strip) + 1, parent.indexOfChild(activity.findViewById(R.id.btnSendCode)))
    }

    @Test
    fun aFailure_showsTheSentenceInTheStripNotAToast() {
        val activity = buildActivity()

        activity.showFormError("Couldn't send the code. Try again in a moment.", retryable = true)

        assertEquals(View.VISIBLE, strip(activity).visibility)
        assertEquals("Couldn't send the code. Try again in a moment.", AuthStripTestKit.message(strip(activity)))
        assertEquals(View.VISIBLE, AuthStripTestKit.retry(strip(activity)).visibility)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun aRateLimitedFailure_hidesRetry() {
        val activity = buildActivity()

        activity.showFormError("Too many requests. Wait a moment, then try again.", retryable = false)

        assertEquals(View.GONE, AuthStripTestKit.retry(strip(activity)).visibility)
    }

    @Test
    fun nextSubmit_clearsTheStrip() {
        val activity = buildActivity()
        activity.showFormError("Couldn't send the code. Try again in a moment.", retryable = true)

        activity.findViewById<android.widget.Button>(R.id.btnSendCode).performClick()

        assertEquals(View.GONE, strip(activity).visibility)
    }

    @Test
    fun retry_reRunsTheSubmitFromAFreshStrip() {
        val activity = buildActivity()
        activity.showFormError("Couldn't send the code. Try again in a moment.", retryable = true)

        AuthStripTestKit.retry(strip(activity)).performClick()

        assertEquals(View.GONE, strip(activity).visibility)
        assertEquals("Enter a valid email address", AuthStripTestKit.helper(activity, R.id.txtEmailError))
    }

    @Test
    fun everyFailure_isAnnounced_includingAnIdenticalRepeat() {
        AuthStripTestKit.enableAccessibility()
        val activity = AuthStripTestKit.visible(ForgotPasswordActivity::class.java)
        val message = "Couldn't send the code. Try again in a moment."

        activity.showFormError(message, retryable = true)
        activity.findViewById<android.widget.Button>(R.id.btnSendCode).performClick()
        activity.showFormError(message, retryable = true)

        // The click also fails validation on the empty field, which is a failure of its own.
        assertEquals(listOf(message, "Enter a valid email address", message), AuthStripTestKit.announcements())
    }

    // ---- Field error: helper line, focus, clear on edit, rotation

    @Test
    fun theEmailError_takesFocus_clearsOnEdit_andSurvivesRotation() {
        AuthStripTestKit.enableAccessibility()
        val controller = AuthStripTestKit.visibleController(ForgotPasswordActivity::class.java)
        val before = controller.get()
        before.findViewById<android.widget.Button>(R.id.btnSendCode).performClick()
        assertTrue(before.findViewById<EditText>(R.id.editEmail).hasFocus())
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertEquals("Enter a valid email address", AuthStripTestKit.helper(after, R.id.txtEmailError))
        assertTrue(after.findViewById<EditText>(R.id.editEmail).isActivated)
        assertNull(after.findViewById<EditText>(R.id.editEmail).error)
        assertEquals("a restore is not a new failure", announced, AuthStripTestKit.announcements().size)

        after.findViewById<EditText>(R.id.editEmail).setText("a")

        assertNull(AuthStripTestKit.helper(after, R.id.txtEmailError))
        assertFalse(after.findViewById<EditText>(R.id.editEmail).isActivated)
    }

    // ---- Rotation: the strip and its Retry

    @Test
    fun rotation_keepsTheStripAndItsRetry_withoutAnnouncingAgain() {
        AuthStripTestKit.enableAccessibility()
        val controller = AuthStripTestKit.visibleController(ForgotPasswordActivity::class.java)
        val message = "Couldn't send the code. Try again in a moment."
        controller.get().showFormError(message, retryable = true)
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(View.VISIBLE, strip(after).visibility)
        assertEquals(message, AuthStripTestKit.message(strip(after)))
        assertEquals(View.VISIBLE, AuthStripTestKit.retry(strip(after)).visibility)
        assertEquals(announced, AuthStripTestKit.announcements().size)

        AuthStripTestKit.retry(strip(after)).performClick()

        assertEquals(View.GONE, strip(after).visibility)
        assertEquals("Enter a valid email address", AuthStripTestKit.helper(after, R.id.txtEmailError))
    }

    @Test
    fun rotation_keepsAStripWithoutRetryHidingRetry() {
        val controller = AuthStripTestKit.visibleController(ForgotPasswordActivity::class.java)
        controller.get().showFormError("Too many requests. Wait a moment, then try again.", retryable = false)

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(View.VISIBLE, strip(after).visibility)
        assertEquals(View.GONE, AuthStripTestKit.retry(strip(after)).visibility)
    }

    @Test
    fun rotation_withNoFailure_keepsTheStripHidden() {
        val after = AuthStripTestKit.rotate(AuthStripTestKit.visibleController(ForgotPasswordActivity::class.java))

        assertEquals(View.GONE, strip(after).visibility)
    }
}
