package com.thedavelopers.eventqr.features.dashboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity
import com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity

/** Picks the dashboard that matches an account role; shared by the launch route and the role-change re-route. */
object DashboardRouter {
    /** Every activity that can be a role's landing dashboard. */
    val dashboardClasses: Set<Class<out Activity>> = setOf(
        DashboardActivity::class.java,
        StaffDashboardActivity::class.java,
        OrganizerDashboardActivity::class.java,
        AdminDashboardActivity::class.java,
    )

    fun destinationFor(role: String?): Class<out Activity> = when (RoleMapper.normalizeRole(role)) {
        AccountRole.STAFF.name -> StaffDashboardActivity::class.java
        AccountRole.ORGANIZER.name -> OrganizerDashboardActivity::class.java
        AccountRole.ADMIN.name, AccountRole.SUPER_ADMIN.name -> AdminDashboardActivity::class.java
        else -> DashboardActivity::class.java
    }

    fun intentFor(context: Context, role: String?): Intent =
        Intent(context, destinationFor(role)).putExtra("extra_role", RoleMapper.normalizeRole(role))
}
