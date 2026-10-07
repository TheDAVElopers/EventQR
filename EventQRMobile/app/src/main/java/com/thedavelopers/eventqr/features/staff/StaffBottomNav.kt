package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.common.bindComposeBottomNav
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.ui.components.StaffNavItems

enum class StaffBottomNavItem {
    DASHBOARD,
    SCANNER,
    EVENTS,
    LOGS,
}

private val StaffBottomNavItem.navId: String
    get() = when (this) {
        StaffBottomNavItem.DASHBOARD -> "dashboard"
        StaffBottomNavItem.SCANNER -> "scanner"
        StaffBottomNavItem.EVENTS -> "events"
        StaffBottomNavItem.LOGS -> "logs"
    }

/** Pure id-to-destination mapping for the staff nav; unmapped ids resolve to null (no-op). */
internal fun staffNavDestination(id: String): Class<out AppCompatActivity>? = when (id) {
    "dashboard" -> StaffDashboardActivity::class.java
    "scanner" -> ScannerActivity::class.java
    "events" -> StaffAssignedEventsActivity::class.java
    "logs" -> StaffTransactionsActivity::class.java
    else -> null
}

/** Only the Scanner and Logs tabs carry the current event id into the launched screen. */
internal fun staffNavEventIdExtra(id: String, currentEventId: String?): String? =
    if ((id == "scanner" || id == "logs") && !currentEventId.isNullOrBlank()) currentEventId else null

/**
 * Binds the shared Compose bottom navbar into the screen's [R.id.composeBottomNav] host.
 * All staff screens share one constant item set (Dashboard, Scan, Events, Logs).
 */
fun AppCompatActivity.configureStaffBottomNav(selectedItem: StaffBottomNavItem, currentEventId: String? = null) {
    val view = findViewById<ComposeView>(R.id.composeBottomNav) ?: return
    val selectedId = selectedItem.navId
    bindComposeBottomNav(view, StaffNavItems, selectedId) { id ->
        if (id == selectedId) return@bindComposeBottomNav
        val destination = staffNavDestination(id) ?: return@bindComposeBottomNav
        val intent = Intent(this, destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        staffNavEventIdExtra(id, currentEventId)?.let {
            intent.putExtra(StaffScreenExtras.EXTRA_EVENT_ID, it)
        }
        startActivity(intent)
    }
}