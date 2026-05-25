package com.thedavelopers.eventqr.features.profile

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.thedavelopers.eventqr.R

class ProfileActivity : com.thedavelopers.eventqr.core.ui.BaseNavActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)



        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: findViewById<BottomNavigationView>(R.id.nav_view_container)
        setupBottomNavigation(bottomNav)
        updateBottomNavSelection(bottomNav, R.id.nav_profile)
    }
}