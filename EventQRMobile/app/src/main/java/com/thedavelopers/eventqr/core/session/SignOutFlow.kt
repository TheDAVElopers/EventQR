package com.thedavelopers.eventqr.core.session

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import kotlinx.coroutines.launch
import com.thedavelopers.eventqr.R

/**
 * Sign-out shared by every portal: confirm, revoke the token on the server, clear the local
 * session, and return to the login screen with a fresh back stack.
 */
object SignOutFlow {

    fun confirmAndSignOut(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.common_sign_out))
            .setMessage(activity.getString(R.string.attendee_profile_are_you_sure_you_want_to_sign_out))
            .setPositiveButton(activity.getString(R.string.common_sign_out)) { dialog, _ ->
                dialog.dismiss()
                signOut(activity)
            }
            .setNegativeButton(activity.getString(R.string.request_event_cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun signOut(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            SessionLogout.signOut(activity)
            activity.startActivity(
                Intent(activity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            activity.finish()
        }
    }
}
