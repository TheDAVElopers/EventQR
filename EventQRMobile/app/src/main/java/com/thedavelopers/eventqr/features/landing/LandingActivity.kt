package com.thedavelopers.eventqr.features.landing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class LandingActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private var splashFadeStarted = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, this.getString(R.string.landing_camera_permission_is_required_for_sc), Toast.LENGTH_LONG).show()
        }
        showLandingContent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Only Android 12+ (API 31+) uses the native SplashScreen theme (values-v31).
        // On older versions the gradient is drawn directly as the window background
        // (android:windowBackground in Theme.App.Starting), so calling installSplashScreen()
        // there would override it with a solid color and reintroduce a launch flash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Drop the system splash the instant the app draws (it is just flat purple), then start the branded
            // splash's fade-in from that moment. Starting the fade any earlier would play it behind the system
            // splash, so on a slow start the logo would appear to pop in.
            installSplashScreen().setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
                startSplashFade()
            }
        }
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // The same branded splash (gradient + logo with the EventQR wordmark) for everyone. Signed-in users used
        // to skip it and saw only the system splash (icon on solid purple), so the launch looked different
        // depending on session state.
        setContentView(R.layout.activity_splash_modern)
        enableEdgeToEdge()
        val splashShownAt = SystemClock.elapsedRealtime()
        prepareSplashFade()
        if (savedInstanceState != null) {
            // Re-created (e.g. rotation): there is no system splash to wait for.
            startSplashFade()
        } else {
            // Safety net in case the system splash never reports back, so the logo can never stay hidden.
            window.decorView.postDelayed({ startSplashFade() }, SPLASH_FADE_FALLBACK_MS)
        }

        if (sessionManager.hasUsableToken()) {
            refreshSessionAndNavigate(splashShownAt)
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissionsAndProceed()
        }, SPLASH_MIN_MS)
    }

    /**
     * Android 12+ draws a flat-color system splash first (a gradient is not possible there). The layout's base is that
     * same flat color, so the hand-off is invisible; the gradient and logo then fade in on top ([startSplashFade]).
     * Before Android 12 the window background is already the gradient, so everything is shown immediately.
     */
    private fun prepareSplashFade() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            splashFadeStarted = true
            return
        }
        findViewById<View>(R.id.splashGradient).alpha = 0f
        findViewById<View>(R.id.splashLogo).apply {
            alpha = 0f
            scaleX = SPLASH_LOGO_START_SCALE
            scaleY = SPLASH_LOGO_START_SCALE
        }
    }

    private fun startSplashFade() {
        if (splashFadeStarted) return
        splashFadeStarted = true
        val easeOut = DecelerateInterpolator(1.5f)
        findViewById<View>(R.id.splashGradient).animate()
            .alpha(1f).setDuration(SPLASH_FADE_MS).setInterpolator(easeOut).start()
        findViewById<View>(R.id.splashLogo).animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(SPLASH_LOGO_DELAY_MS).setDuration(SPLASH_FADE_MS).setInterpolator(easeOut).start()
    }

    private fun checkPermissionsAndProceed() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            showLandingContent()
        }
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
        val normalizedRole = RoleMapper.normalizeRole(role)
        val destination = when (normalizedRole) {
            AccountRole.STAFF.name -> com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java
            AccountRole.ORGANIZER.name ->
                com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java
            AccountRole.ADMIN.name, AccountRole.SUPER_ADMIN.name ->
                com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java
            else -> DashboardActivity::class.java
        }
        startActivity(
            Intent(this, destination)
                .putExtra("extra_role", normalizedRole)
        )
        finish()
    }

    /** Keeps the splash on screen for at least [SPLASH_MIN_MS] in total, whatever the session refresh took. */
    private suspend fun awaitMinimumSplash(splashShownAt: Long) {
        val remaining = SPLASH_MIN_MS - (SystemClock.elapsedRealtime() - splashShownAt)
        if (remaining > 0) delay(remaining)
    }

    private fun refreshSessionAndNavigate(splashShownAt: Long) {
        lifecycleScope.launch {
            val repo = AuthRepository(this@LandingActivity)
            // Re-issue the access token so the client reflects the CURRENT role in the
            // database (e.g. an attendee upgraded to organizer after approval) instead of
            // the stale role embedded in the old JWT. Fall back to the cached session if
            // the refresh fails (e.g. offline).
            when (val refreshed = repo.refreshSessionToken()) {
                is NetworkResult.Success -> {
                    repo.getAuthMe().let { me ->
                        if (me is NetworkResult.Success) {
                            sessionManager.updateProfile(me.data.fullName, me.data.phoneNumber)
                        }
                    }
                    awaitMinimumSplash(splashShownAt)
                    navigateToDashboard(refreshed.data.role?.name)
                }
                is NetworkResult.Error -> {
                    awaitMinimumSplash(splashShownAt)
                    navigateToDashboard(sessionManager.getUserRole())
                }
                NetworkResult.Loading -> {
                    awaitMinimumSplash(splashShownAt)
                    navigateToDashboard(sessionManager.getUserRole())
                }
            }
        }
    }

    private companion object {
        /** One value for signed-in and signed-out launches so the splash always looks and lasts the same. */
        const val SPLASH_MIN_MS = 2000L
        const val SPLASH_FADE_MS = 700L
        const val SPLASH_LOGO_DELAY_MS = 120L
        const val SPLASH_FADE_FALLBACK_MS = 1500L
        const val SPLASH_LOGO_START_SCALE = 0.9f
    }
}
