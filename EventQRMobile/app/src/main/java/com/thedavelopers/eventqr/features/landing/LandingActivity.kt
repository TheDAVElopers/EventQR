package com.thedavelopers.eventqr.features.landing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.session.SessionRefresher
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import com.thedavelopers.eventqr.features.dashboard.DashboardRouter

open class LandingActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Only Android 12+ (API 31+) uses the native SplashScreen theme (values-v31).
        // On older versions the gradient is drawn directly as the window background
        // (android:windowBackground in Theme.App.Starting), so calling installSplashScreen()
        // there would override it with a solid color and reintroduce a launch flash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The system splash is just flat purple; drop it the instant the app draws so the static branded
            // splash replaces it in one frame, without the default exit animation.
            installSplashScreen().setOnExitAnimationListener { it.remove() }
        }
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // The same static branded splash (gradient + logo with the EventQR wordmark) for everyone.
        setContentView(R.layout.activity_splash_modern)
        enableEdgeToEdge()

        if (sessionManager.hasUsableToken()) {
            // Route on the cached session right away; the refresh runs in the background.
            SessionRefresher.refresh(this)
            navigateToDashboard(sessionManager.getUserRole())
            return
        }

        // The camera permission is requested by the scanner screen, only for staff who actually scan.
        showLandingContent()
    }

    private fun showLandingContent() {
        setContentView(R.layout.activity_landing)

        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnCreateAccount = findViewById<Button>(R.id.btnCreateAccount)

        btnSignIn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java))
            finish()
        }
    }

    private fun navigateToDashboard(role: String?) {
        startActivity(DashboardRouter.intentFor(this, role))
        finish()
    }
}
