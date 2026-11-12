package com.thedavelopers.eventqr.features.auth.resetpassword

import android.content.Intent
import android.view.View
import android.widget.Button
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

/** Re-entry from the password step (CLEAR_TOP | SINGLE_TOP -> onNewIntent) when the code has died. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ResetPasswordActivityTest {

    @Test
    fun onNewIntent_codeExpired_clearsCodeShowsNoticeAndEnablesResend() {
        val intent = Intent().putExtra(ResetPasswordActivity.EXTRA_EMAIL, "a@b.com")
        val controller = Robolectric.buildActivity(ResetPasswordActivity::class.java, intent).create()
        val activity = controller.get()
        val code = activity.findViewById<EditText>(R.id.edtResetCode)
        code.setText("123456")

        controller.newIntent(
            Intent()
                .putExtra(ResetPasswordActivity.EXTRA_EMAIL, "a@b.com")
                .putExtra(ResetPasswordActivity.EXTRA_CODE_EXPIRED, true),
        )

        assertEquals("", code.text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<TextView>(R.id.txtCodeExpired).visibility)
        assertTrue(activity.findViewById<Button>(R.id.btnResendCode).isEnabled)
    }

    @Test
    fun showCodeError_turnsTheErrorStrokeAndUsesTheInlineLineInsteadOfTheTooltip() {
        val controller = Robolectric.buildActivity(
            ResetPasswordActivity::class.java,
            Intent().putExtra(ResetPasswordActivity.EXTRA_EMAIL, "a@b.com"),
        ).create()
        val activity = controller.get()
        val code = activity.findViewById<EditText>(R.id.edtResetCode)
        val errorLine = activity.findViewById<TextView>(R.id.txtCodeError)

        activity.showCodeError("That code is incorrect or has expired.")

        // bg_ui_field keys its 2dp ui_error stroke off state_activated; the platform bubble must stay off.
        assertNull(code.error)
        assertTrue(code.isActivated)
        assertEquals(View.VISIBLE, errorLine.visibility)
        assertEquals("That code is incorrect or has expired.", errorLine.text.toString())

        activity.showCodeError(null)

        assertFalse(code.isActivated)
        assertEquals(View.GONE, errorLine.visibility)
    }

    @Test
    fun ghostActions_areAtLeastTheTouchFloor() {
        val controller = Robolectric.buildActivity(
            ResetPasswordActivity::class.java,
            Intent().putExtra(ResetPasswordActivity.EXTRA_EMAIL, "a@b.com"),
        ).create()
        val activity = controller.get()
        val floor = activity.resources.getDimensionPixelSize(R.dimen.ui_touch_min)

        for (id in listOf(R.id.btnResendCode, R.id.btnGoToLogin)) {
            assertTrue(activity.findViewById<Button>(id).minimumHeight >= floor)
        }
    }

    // ---- §5.16 form failure strip (directly above Verify; Retry re-runs Verify, or Resend after a resend failure)

    private fun build(): ResetPasswordActivity = Robolectric.buildActivity(
        ResetPasswordActivity::class.java,
        Intent().putExtra(ResetPasswordActivity.EXTRA_EMAIL, "a@b.com"),
    ).create().get()

    private fun strip(activity: ResetPasswordActivity) = AuthStripTestKit.strip(activity, R.id.errorResetCode)

    @Test
    fun theStrip_isHiddenUntilAFailureAndSitsDirectlyAboveVerify() {
        val activity = build()
        val strip = strip(activity)
        val parent = strip.parent as android.view.ViewGroup

        assertEquals(View.GONE, strip.visibility)
        assertEquals(parent.indexOfChild(strip) + 1, parent.indexOfChild(activity.findViewById(R.id.btnVerifyCode)))
    }

    @Test
    fun aVerifyFailure_showsTheSentenceInTheStripNotAToast() {
        val activity = build()

        activity.showFormError("Couldn't check the code. Try again in a moment.", retryable = true)

        assertEquals(View.VISIBLE, strip(activity).visibility)
        assertEquals("Couldn't check the code. Try again in a moment.", AuthStripTestKit.message(strip(activity)))
        assertEquals(View.VISIBLE, AuthStripTestKit.retry(strip(activity)).visibility)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun aRateLimitedFailure_hidesRetry() {
        val activity = build()

        activity.showFormError("Too many requests. Wait a moment, then try again.", retryable = false)
        assertEquals(View.GONE, AuthStripTestKit.retry(strip(activity)).visibility)

        activity.showResendError("Too many requests. Wait a moment, then try again.", retryable = false)
        assertEquals(View.GONE, AuthStripTestKit.retry(strip(activity)).visibility)
    }

    @Test
    fun nextVerify_clearsTheStrip() {
        val activity = build()
        activity.showFormError("Couldn't check the code. Try again in a moment.", retryable = true)

        activity.findViewById<Button>(R.id.btnVerifyCode).performClick()

        assertEquals(View.GONE, strip(activity).visibility)
    }

    @Test
    fun nextResend_clearsTheStrip() {
        val activity = build()
        activity.showResendError("Couldn't send a new code. Try again in a moment.", retryable = true)

        activity.findViewById<Button>(R.id.btnResendCode).performClick()

        assertEquals(View.GONE, strip(activity).visibility)
    }

    @Test
    fun retryAfterAVerifyFailure_reRunsVerify() {
        val activity = build()
        activity.showFormError("Couldn't check the code. Try again in a moment.", retryable = true)

        AuthStripTestKit.retry(strip(activity)).performClick()

        assertEquals(View.GONE, strip(activity).visibility)
        // Verify ran against the empty field, so its own validation answered.
        assertEquals(View.VISIBLE, activity.findViewById<TextView>(R.id.txtCodeError).visibility)
    }

    @Test
    fun everyFailure_isAnnounced_includingAnIdenticalRepeat() {
        AuthStripTestKit.enableAccessibility()
        val activity = AuthStripTestKit.visible(ResetPasswordActivity::class.java)
        val message = "Couldn't check the code. Try again in a moment."

        activity.showFormError(message, retryable = true)
        activity.findViewById<Button>(R.id.btnVerifyCode).performClick()
        activity.showFormError(message, retryable = true)

        assertEquals(listOf(message, message), AuthStripTestKit.announcements())
    }

    // ---- Field error: helper line, focus, clear on edit, rotation

    private fun visibleController() = Robolectric.buildActivity(
        ResetPasswordActivity::class.java,
        Intent().putExtra(ResetPasswordActivity.EXTRA_EMAIL, "a@b.com"),
    ).create().start().resume().visible()

    @Test
    fun theCodeError_takesFocus_isAnnouncedOnce_andClearsOnEdit() {
        AuthStripTestKit.enableAccessibility()
        val activity = visibleController().get()
        val code = activity.findViewById<EditText>(R.id.edtResetCode)

        activity.findViewById<Button>(R.id.btnVerifyCode).performClick()

        assertEquals(activity.getString(R.string.reset_password_code_invalid), AuthStripTestKit.helper(activity, R.id.txtCodeError))
        assertNull("no platform balloon", code.error)
        assertTrue(code.isActivated)
        assertTrue(code.hasFocus())
        assertEquals(listOf(activity.getString(R.string.reset_password_code_invalid)), AuthStripTestKit.announcements())

        code.setText("1")

        assertNull(AuthStripTestKit.helper(activity, R.id.txtCodeError))
        assertFalse(code.isActivated)
    }

    @Test
    fun rotation_keepsTheCodeError_andItStaysSilent() {
        AuthStripTestKit.enableAccessibility()
        val controller = visibleController()
        controller.get().findViewById<Button>(R.id.btnVerifyCode).performClick()
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(after.getString(R.string.reset_password_code_invalid), AuthStripTestKit.helper(after, R.id.txtCodeError))
        assertTrue(after.findViewById<EditText>(R.id.edtResetCode).isActivated)
        assertEquals("a restore is not a new failure", announced, AuthStripTestKit.announcements().size)
    }

    @Test
    fun rotation_keepsTheCodeExpiredNotice_silently() {
        AuthStripTestKit.enableAccessibility()
        val controller = visibleController()
        controller.get().showCodeExpired()
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(View.VISIBLE, after.findViewById<TextView>(R.id.txtCodeExpired).visibility)
        assertEquals(announced, AuthStripTestKit.announcements().size)
    }

    // ---- Rotation: the strip, and which action its Retry runs

    @Test
    fun rotation_afterAVerifyFailure_retryStillReRunsVerify() {
        AuthStripTestKit.enableAccessibility()
        val controller = visibleController()
        val message = "Couldn't check the code. Try again in a moment."
        controller.get().showFormError(message, retryable = true)
        val announced = AuthStripTestKit.announcements().size

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(View.VISIBLE, strip(after).visibility)
        assertEquals(message, AuthStripTestKit.message(strip(after)))
        assertEquals(View.VISIBLE, AuthStripTestKit.retry(strip(after)).visibility)
        assertEquals("a restore is not a new failure", announced, AuthStripTestKit.announcements().size)

        AuthStripTestKit.retry(strip(after)).performClick()

        assertEquals(View.GONE, strip(after).visibility)
        // Verify ran (against the empty field), so its own validation answered; Resend would not have.
        assertEquals(after.getString(R.string.reset_password_code_invalid), AuthStripTestKit.helper(after, R.id.txtCodeError))
    }

    @Test
    fun rotation_afterAResendFailure_retryStillReRunsResend() {
        val controller = visibleController()
        controller.get().showResendError("Couldn't send a new code. Try again in a moment.", retryable = true)

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(View.VISIBLE, strip(after).visibility)
        assertEquals(View.VISIBLE, AuthStripTestKit.retry(strip(after)).visibility)

        AuthStripTestKit.retry(strip(after)).performClick()

        assertEquals(View.GONE, strip(after).visibility)
        // Resend ran: its cooldown locked the button. Verify would have raised the code error instead.
        assertFalse(after.findViewById<Button>(R.id.btnResendCode).isEnabled)
        assertNull(AuthStripTestKit.helper(after, R.id.txtCodeError))
    }

    @Test
    fun rotation_keepsAStripWithoutRetryHidingRetry() {
        val controller = visibleController()
        controller.get().showFormError("Too many requests. Wait a moment, then try again.", retryable = false)

        val after = AuthStripTestKit.rotate(controller)

        assertEquals(View.VISIBLE, strip(after).visibility)
        assertEquals(View.GONE, AuthStripTestKit.retry(strip(after)).visibility)
    }

    @Test
    fun rotation_withNoFailure_keepsTheStripHidden() {
        val after = AuthStripTestKit.rotate(visibleController())

        assertEquals(View.GONE, strip(after).visibility)
    }
}
