package com.thedavelopers.eventqr.features.profile

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.thedavelopers.eventqr.R

class ProfileActivity : com.thedavelopers.eventqr.core.ui.BaseNavActivity() {
    private lateinit var sessionManager: com.thedavelopers.eventqr.core.session.SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = com.thedavelopers.eventqr.core.session.SessionManager(this)

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation) ?: findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view_container)
        setupBottomNavigation(bottomNav)
        updateBottomNavSelection(bottomNav, R.id.nav_profile)

        findViewById<android.widget.Button>(R.id.btnEditProfile)?.setOnClickListener {
            startActivity(android.content.Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeEditProfileActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.btnProfileLogout)?.setOnClickListener {
            sessionManager.clearSession()
            startActivity(android.content.Intent(this, com.thedavelopers.eventqr.SignIn::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProfile()
    }

    private fun refreshProfile() {
        findViewById<TextView>(R.id.txtProfileName).text = sessionManager.getFullName() ?: "User"
        findViewById<TextView>(R.id.txtProfileRole).text = sessionManager.getUserRole() ?: "Member"
        findViewById<TextView>(R.id.txtProfileEmail).text = sessionManager.getEmail() ?: ""
        findViewById<TextView>(R.id.txtPhone).text = sessionManager.getPhone() ?: "N/A"
    }
}
