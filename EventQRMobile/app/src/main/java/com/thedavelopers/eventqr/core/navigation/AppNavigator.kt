package com.thedavelopers.eventqr.core.navigation

import android.content.Context
import android.content.Intent
import com.thedavelopers.eventqr.features.auth.changepassword.ChangePasswordActivity
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.landing.LandingActivity

object AppNavigator {
    fun changePassword(context: Context): Intent = Intent(context, ChangePasswordActivity::class.java)

    /**
     * After sign-out or an expired session: a fresh task with Sign In on top of Landing, so Back from Sign In
     * returns to Landing instead of leaving the app. Pass to [Context.startActivities]. The session must already
     * be cleared, otherwise Landing would route straight back to a dashboard.
     */
    fun signedOutStack(context: Context): Array<Intent> = arrayOf(
        Intent(context, LandingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        Intent(context, LoginActivity::class.java),
    )
}

