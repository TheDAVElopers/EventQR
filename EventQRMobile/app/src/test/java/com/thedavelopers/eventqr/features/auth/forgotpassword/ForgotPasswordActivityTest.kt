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
 * The reset-link network call is not unit-testable (see coverage notes).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ForgotPasswordActivityTest {

    private fun buildActivity(): ForgotPasswordActivity =
        Robolectric.buildActivity(ForgotPasswordActivity::class.java).create().get()

    @Test
    fun clickSend_resetLink_invalidEmail_showsErrorAndKeepsForm() {
        val activity = buildActivity()
        activity.findViewById<EditText>(R.id.editEmail).setText("not-an-email")

        activity.findViewById<android.widget.Button>(R.id.btnSendResetLink).performClick()

        assertEquals(
            "Enter a valid email address",
            activity.findViewById<EditText>(R.id.editEmail).error.toString(),
        )
        assertEquals(
            View.VISIBLE,
            activity.findViewById<LinearLayout>(R.id.layoutForm).visibility,
        )
        assertEquals(
            View.GONE,
            activity.findViewById<LinearLayout>(R.id.layoutConfirmation).visibility,
        )
    }

    @Test
    fun clickSend_resetLink_emptyEmail_showsErrorAndNoConfirmation() {
        val activity = buildActivity()

        activity.findViewById<android.widget.Button>(R.id.btnSendResetLink).performClick()

        assertEquals(
            "Enter a valid email address",
            activity.findViewById<EditText>(R.id.editEmail).error.toString(),
        )
        assertEquals(
            View.GONE,
            activity.findViewById<LinearLayout>(R.id.layoutConfirmation).visibility,
        )
        assertNull(shadowOf(activity).peekNextStartedActivity())
    }

    @Test
    fun clickBackButton_navigatesToLoginAndFinishes() {
        val activity = buildActivity()

        activity.findViewById<android.widget.ImageButton>(R.id.btnBackToSignIn).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(LoginActivity::class.java.name, intent.component?.className)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun clickBackTextLink_navigatesToLogin() {
        val activity = buildActivity()

        activity.findViewById<View>(R.id.tvBackToSignIn).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(LoginActivity::class.java.name, intent.component?.className)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun clickConfirmationBackButton_navigatesToLogin() {
        val activity = buildActivity()

        activity.findViewById<android.widget.Button>(R.id.btnBackToSignInConfirmation).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(LoginActivity::class.java.name, intent.component?.className)
        assertTrue(activity.isFinishing)
    }
}