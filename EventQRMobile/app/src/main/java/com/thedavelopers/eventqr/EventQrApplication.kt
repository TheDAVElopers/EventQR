package com.thedavelopers.eventqr

import android.app.Activity
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
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.ui.theme.applyRequestedEventQrSystemBarAppearance

class EventQrApplication : Application() {
    private var startedActivities = 0

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
                startedActivities++
                activity.applyRequestedEventQrSystemBarAppearance()
            }

            override fun onActivityResumed(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        SessionRefresher.onRoleChanged = { role ->
            // The background refresh found a different role than the one the dashboard launched with: re-route so
            // the user lands on the right portal. Skipped while backgrounded (the persisted role applies next launch).
            if (startedActivities > 0) {
                startActivity(
                    DashboardRouter.intentFor(this, role)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
        }
        SessionEvents.onSessionExpired = {
            // Called from an OkHttp thread once the server has refused the refresh token.
            Handler(Looper.getMainLooper()).post {
                RegistrationsCache.clear()
                Toast.makeText(this, this.getString(R.string.event_qr_application_your_session_has_expired_please_sign), Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
        }
    }
}
