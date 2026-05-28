package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity

enum class AttendeeBottomNavItem {
    DASHBOARD,
    EVENTS,
    REWARDS,
    PROFILE,
}

fun AppCompatActivity.configureAttendeeBottomNav(selectedItem: AttendeeBottomNavItem) {
    bindBottomNavItem(R.id.navDashboard, selectedItem == AttendeeBottomNavItem.DASHBOARD, DashboardActivity::class.java)
    bindBottomNavItem(R.id.navEvents, selectedItem == AttendeeBottomNavItem.EVENTS, AttendeeEventsActivity::class.java)
    bindBottomNavItem(R.id.navRewards, selectedItem == AttendeeBottomNavItem.REWARDS, AttendeeRewardsActivity::class.java)
    bindBottomNavItem(R.id.navProfile, selectedItem == AttendeeBottomNavItem.PROFILE, AttendeeProfileActivity::class.java)
}

private fun AppCompatActivity.bindBottomNavItem(
    navId: Int,
    isCurrent: Boolean,
    destination: Class<out AppCompatActivity>,
) {
    findViewById<View>(navId)?.apply {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            if (isCurrent) return@setOnClickListener

            startActivity(
                Intent(this@bindBottomNavItem, destination)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}
