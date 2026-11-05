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
}
