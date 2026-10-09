package com.thedavelopers.eventqr

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.thedavelopers.eventqr.core.session.SessionEvents
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.session.SessionRefresher
import com.thedavelopers.eventqr.features.dashboard.DashboardRouter
import com.thedavelopers.eventqr.features.dashboard.RoleReroutePolicy
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import com.thedavelopers.eventqr.ui.theme.applyRequestedEventQrSystemBarAppearance

class EventQrApplication : Application() {
    // The activity currently in the resumed state, or null while the app is backgrounded.
    private var resumedActivity: Activity? = null

    // A role change that arrived while no dashboard was in front; applied when the user next lands on one.
    private var pendingRole: String? = null

    override fun onCreate() {
        super.onCreate()
        // Start the slow Keystore/EncryptedSharedPreferences init now, in parallel with activity creation.
        SessionManager.warmUp(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Pre-35 devices only draw behind the status bar once asked to; 35+ enforces it.
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            }

            override fun onActivityStarted(activity: Activity) {
                activity.applyRequestedEventQrSystemBarAppearance()
            }

            override fun onActivityResumed(activity: Activity) {
                resumedActivity = activity
                if (activity is LoginActivity || activity is RegistrationActivity) {
                    // Signed-out screens: a pending role belongs to the session that just ended.
                    pendingRole = null
                } else if (activity::class.java in DashboardRouter.dashboardClasses) {
                    pendingRole?.let { role ->
                        pendingRole = null
                        rerouteIfNeeded(activity, role)
                    }
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (resumedActivity === activity) resumedActivity = null
            }

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        SessionRefresher.onRoleChanged = { role ->
            // The background refresh found a different role than the one the dashboard launched with. Re-route only
            // while a dashboard is in front (never on login/registration or a sub-screen); otherwise remember it for
            // the next dashboard visit. The persisted role already applies to the next cold launch.
            val foreground = resumedActivity
            when {
                foreground != null && foreground::class.java in DashboardRouter.dashboardClasses ->
                    rerouteIfNeeded(foreground, role)
                foreground is LoginActivity || foreground is RegistrationActivity -> Unit
                else -> pendingRole = role
            }
        }
        SessionEvents.onSessionExpired = {
            // Called from an OkHttp thread once the server has refused the refresh token.
            Handler(Looper.getMainLooper()).post {
                pendingRole = null
                RegistrationsCache.clear()
                Toast.makeText(this, this.getString(R.string.event_qr_application_your_session_has_expired_please_sign), Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
        }
    }

    private fun rerouteIfNeeded(foreground: Activity, role: String) {
        if (!RoleReroutePolicy.shouldReroute(foreground::class.java, role)) return
        Toast.makeText(this, getString(R.string.event_qr_application_your_role_was_updated), Toast.LENGTH_SHORT).show()
        // Cross-fade into the new dashboard instead of the app's default instant swap, so the change reads as an
        // update rather than a crash-and-relaunch.
        val fade = ActivityOptions.makeCustomAnimation(foreground, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(
            DashboardRouter.intentFor(this, role)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            fade.toBundle()
        )
    }
}
