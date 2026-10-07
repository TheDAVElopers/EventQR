package com.thedavelopers.eventqr.features.admin

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity
import com.thedavelopers.eventqr.features.admin.logs.AdminAuditLogsActivity
import com.thedavelopers.eventqr.features.admin.users.AdminAccountManagementActivity
import com.thedavelopers.eventqr.features.common.bindComposeBottomNav
import com.thedavelopers.eventqr.ui.components.AdminNavItems

enum class AdminBottomNavItem {
    DASHBOARD,
    REQUESTS,
    ACCOUNTS,
    LOGS,
}

private val AdminBottomNavItem?.navId: String
    get() = when (this) {
        AdminBottomNavItem.DASHBOARD -> "dashboard"
        AdminBottomNavItem.REQUESTS -> "requests"
        AdminBottomNavItem.ACCOUNTS -> "accounts"
        AdminBottomNavItem.LOGS -> "logs"
        null -> ""
    }

/**
 * Pure id-to-destination mapping for the admin (and super-admin) nav. Logs has no own
 * destination in the legacy layout — it resolves to null so the tab is a no-op here.
 */
internal fun adminNavDestination(id: String): Class<out AppCompatActivity>? = when (id) {
    "dashboard" -> AdminDashboardActivity::class.java
    "requests" -> AdminEventApprovalBackendActivity::class.java
    "accounts" -> AdminAccountManagementActivity::class.java
    "logs" -> AdminAuditLogsActivity::class.java
    else -> null
}

/**
 * Binds the shared Compose bottom navbar into the screen's [R.id.composeBottomNav] host.
 * The super-admin portal uses the same screens, so it binds [AdminNavItems] identically.
 */
fun AppCompatActivity.configureAdminBottomNav(selectedItem: AdminBottomNavItem?) {
    val view = findViewById<ComposeView>(R.id.composeBottomNav) ?: return
    val selectedId = selectedItem.navId
    bindComposeBottomNav(view, AdminNavItems, selectedId) { id ->
        if (id == selectedId) return@bindComposeBottomNav
        val destination = adminNavDestination(id) ?: return@bindComposeBottomNav
        startActivity(
            Intent(this, destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }
}