package com.thedavelopers.eventqr.features.landing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.Dashboard
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.Registration
import com.thedavelopers.eventqr.SignIn
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import kotlinx.coroutines.launch

open class LandingActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission is required for scanning features", Toast.LENGTH_LONG).show()
        }
        showLandingContent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        if (sessionManager.hasUsableToken()) {
            refreshSessionAndNavigate()
            return
        }

        setContentView(R.layout.activity_splash_modern)
        enableEdgeToEdge()

        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissionsAndProceed()
        }, 2000)
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
            startActivity(Intent(this, SignIn::class.java))
            finish()
        }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, Registration::class.java))
            finish()
        }
    }

    private fun navigateToDashboard(role: String?) {
        val normalizedRole = RoleMapper.normalizeRole(role)
        val destination = when (normalizedRole) {
            AccountRole.STAFF.name -> com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java
            AccountRole.ORGANIZER.name, AccountRole.ADMIN.name, AccountRole.SUPER_ADMIN.name ->
                com.thedavelopers.eventqr.features.organizer.OrganizerDashboardActivity::class.java
            else -> Dashboard::class.java
        }
        startActivity(
            Intent(this, destination)
                .putExtra("extra_role", normalizedRole)
        )
        finish()
    }

    private fun refreshSessionAndNavigate() {
        lifecycleScope.launch {
            when (val result = AuthRepository(this@LandingActivity).getAuthMe()) {
                is NetworkResult.Success -> {
                    sessionManager.saveRole(result.data.role)
                    sessionManager.updateProfile(result.data.fullName, result.data.phoneNumber)
                    navigateToDashboard(result.data.role?.name)
                }
                is NetworkResult.Error -> navigateToDashboard(sessionManager.getUserRole())
                NetworkResult.Loading -> navigateToDashboard(sessionManager.getUserRole())
            }
        }
    }
}
