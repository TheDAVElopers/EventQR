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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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

        assertEquals(
            "Enter a valid email address",
            activity.findViewById<EditText>(R.id.editEmail).error.toString(),
        )
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

        assertEquals(
            "Enter a valid email address",
            activity.findViewById<EditText>(R.id.editEmail).error.toString(),
        )
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
}