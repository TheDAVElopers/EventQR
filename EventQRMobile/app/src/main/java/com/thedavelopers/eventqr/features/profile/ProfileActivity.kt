package com.thedavelopers.eventqr.features.profile

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import kotlinx.coroutines.launch

class ProfileActivity : com.thedavelopers.eventqr.core.ui.BaseNavActivity() {
    private lateinit var sessionManager: com.thedavelopers.eventqr.core.session.SessionManager
    private lateinit var repository: com.thedavelopers.eventqr.features.attendee.AttendeeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = com.thedavelopers.eventqr.core.session.SessionManager(this)
        repository = com.thedavelopers.eventqr.features.attendee.AttendeeRepository(this)

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
        loadProfile()
    }

    private fun loadProfile() {
        renderProfile()
        
        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is com.thedavelopers.eventqr.core.api.NetworkResult.Success -> {
                    val user = result.data
                    sessionManager.updateProfile(user.fullName, user.phoneNumber)
                    renderProfile()
                }
                else -> Unit
            }
        }
    }

    private fun renderProfile() {
        findViewById<TextView>(R.id.txtProfileName).text = sessionManager.getFullName() ?: "User"
        findViewById<TextView>(R.id.txtProfileRole).text = com.thedavelopers.eventqr.core.util.RoleMapper.getDisplayName(sessionManager.getUserRole())
        findViewById<TextView>(R.id.txtProfileEmail).text = sessionManager.getEmail() ?: ""
        findViewById<TextView>(R.id.txtPhone).text = sessionManager.getPhone() ?: "N/A"
    }
}
