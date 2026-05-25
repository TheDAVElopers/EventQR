package com.thedavelopers.eventqr.core.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity
import com.thedavelopers.eventqr.features.events.EventsActivity
import com.thedavelopers.eventqr.features.profile.ProfileActivity
import com.thedavelopers.eventqr.features.rewards.RewardsActivity

abstract class BaseNavActivity : AppCompatActivity() {

    protected fun setupBottomNavigation(navView: BottomNavigationView) {
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    if (this !is DashboardActivity) {
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finish()
                    }
                    true
                }
                R.id.nav_events -> {
                    if (this !is EventsActivity) {
                        startActivity(Intent(this, EventsActivity::class.java))
                        finish()
                    }
                    true
                }
                R.id.nav_rewards -> {
                    if (this !is RewardsActivity) {
                        startActivity(Intent(this, RewardsActivity::class.java))
                        finish()
                    }
                    true
                }
                R.id.nav_profile -> {
                    if (this !is ProfileActivity) {
                        startActivity(Intent(this, ProfileActivity::class.java))
                        finish()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    protected fun updateBottomNavSelection(navView: BottomNavigationView, selectedId: Int) {
        navView.selectedItemId = selectedId
    }
}
