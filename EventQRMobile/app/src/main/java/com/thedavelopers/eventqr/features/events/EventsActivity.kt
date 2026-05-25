package com.thedavelopers.eventqr.features.events

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.thedavelopers.eventqr.R

class EventsActivity : com.thedavelopers.eventqr.core.ui.BaseNavActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_events)


        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: findViewById<BottomNavigationView>(R.id.nav_view_container)
        setupBottomNavigation(bottomNav)
        updateBottomNavSelection(bottomNav, R.id.nav_events)
    }
}