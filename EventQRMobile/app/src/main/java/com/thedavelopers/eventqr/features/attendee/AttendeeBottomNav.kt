package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.common.bindComposeBottomNav
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity
import com.thedavelopers.eventqr.ui.components.AttendeeNavItems

enum class AttendeeBottomNavItem {
    DASHBOARD,
    EVENTS,
    REGISTERED,
    REWARDS,
    PROFILE,
}

private val AttendeeBottomNavItem.navId: String
    get() = when (this) {
        AttendeeBottomNavItem.DASHBOARD -> "home"
        AttendeeBottomNavItem.EVENTS -> "events"
        AttendeeBottomNavItem.REGISTERED -> "registered"
        AttendeeBottomNavItem.REWARDS -> "rewards"
        AttendeeBottomNavItem.PROFILE -> "profile"
    }

/** Pure id-to-destination mapping for the attendee nav; unmapped ids resolve to null (no-op). */
internal fun attendeeNavDestination(id: String): Class<out AppCompatActivity>? = when (id) {
    "home" -> DashboardActivity::class.java
    "events" -> AttendeeEventsActivity::class.java
    "registered" -> RegisteredEventsActivity::class.java
    "rewards" -> AttendeeRewardsActivity::class.java
    "profile" -> AttendeeProfileActivity::class.java
    else -> null
}

/**
 * Only the Home tab is a root: returning to it clears the tab history above it. Every other
 * destination is reordered to the front when it is already on the stack - pushing it again would
 * leave duplicate instances behind, so Back would need one press per visit - and pushed when it is
 * not, so Back still walks the tabs and exits from Home.
 */
internal fun attendeeTabFlags(destination: Class<out AppCompatActivity>): Int =
    if (destination == DashboardActivity::class.java) {
        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    } else {
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    }

internal fun AppCompatActivity.openAttendeeDestination(destination: Class<out AppCompatActivity>) {
    startActivity(Intent(this, destination).addFlags(attendeeTabFlags(destination)))
}

/**
 * Binds the shared Compose bottom navbar into the screen's [R.id.composeBottomNav] host.
 * Item set and destinations mirror the legacy XML nav: Home, Events, Registered, Rewards, Profile.
 */
fun AppCompatActivity.configureAttendeeBottomNav(selectedItem: AttendeeBottomNavItem) {
    val view = findViewById<ComposeView>(R.id.composeBottomNav) ?: return
    val selectedId = selectedItem.navId
    bindComposeBottomNav(view, AttendeeNavItems, selectedId) { id ->
        if (id == selectedId) return@bindComposeBottomNav
        val destination = attendeeNavDestination(id) ?: return@bindComposeBottomNav
        openAttendeeDestination(destination)
    }
}