package com.thedavelopers.eventqr.features.dashboard

import android.app.Activity

/**
 * Decides whether a role change found by the background session refresh should move the user to another dashboard.
 * Kept free of Android state so the rules are unit-testable.
 */
object RoleReroutePolicy {
    /**
     * True only when [foreground] is a dashboard that is not the one [newRole] maps to. Login, registration and
     * every other screen are left alone (the persisted role applies on the next launch, and sub-screens guard
     * themselves against the live role). A dashboard that already matches [newRole] means the cached role was
     * only stale bookkeeping, so there is nothing to re-route.
     */
    fun shouldReroute(foreground: Class<out Activity>?, newRole: String?): Boolean {
        if (foreground == null || foreground !in DashboardRouter.dashboardClasses) return false
        return foreground != DashboardRouter.destinationFor(newRole)
    }
}
