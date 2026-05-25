package com.thedavelopers.eventqr.features.rewards

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.thedavelopers.eventqr.R

class RewardsActivity : com.thedavelopers.eventqr.core.ui.BaseNavActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rewards)


        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: findViewById<BottomNavigationView>(R.id.nav_view_container)
        setupBottomNavigation(bottomNav)
        updateBottomNavSelection(bottomNav, R.id.nav_rewards)
    }
}