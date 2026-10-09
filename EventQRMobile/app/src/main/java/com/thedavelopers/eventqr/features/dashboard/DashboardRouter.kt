package com.thedavelopers.eventqr.features.dashboard

import android.content.Context
import android.content.Intent
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity
import com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity

/** Picks the dashboard that matches an account role; shared by the launch route and the role-change re-route. */
object DashboardRouter {
    fun intentFor(context: Context, role: String?): Intent {
        val normalizedRole = RoleMapper.normalizeRole(role)
        val destination = when (normalizedRole) {
            AccountRole.STAFF.name -> StaffDashboardActivity::class.java
            AccountRole.ORGANIZER.name -> OrganizerDashboardActivity::class.java
            AccountRole.ADMIN.name, AccountRole.SUPER_ADMIN.name -> AdminDashboardActivity::class.java
            else -> DashboardActivity::class.java
        }
        return Intent(context, destination).putExtra("extra_role", normalizedRole)
    }
}
