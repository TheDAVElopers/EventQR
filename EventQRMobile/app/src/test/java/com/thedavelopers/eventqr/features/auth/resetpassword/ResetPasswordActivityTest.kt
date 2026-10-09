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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

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
}
