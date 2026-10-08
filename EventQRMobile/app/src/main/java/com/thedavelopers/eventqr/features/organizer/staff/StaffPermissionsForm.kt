package com.thedavelopers.eventqr.features.organizer.staff

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff

/**
 * The permission switches shown when assigning or editing staff: "Scan QR" is always on (and locked),
 * the others default to off.
 */
class StaffPermissionsForm(context: Context, initial: OrganizerMvpStaff? = null) {
    val scanSwitch = SwitchCompat(context).apply {
        id = R.id.staff_perm_scan
        setText(R.string.staff_permission_scan)
        isChecked = true
        isEnabled = false
    }
    val printIdSwitch = SwitchCompat(context).apply {
        id = R.id.staff_perm_print_id
        setText(R.string.staff_permission_print_ids)
        isChecked = initial?.canPrintId == true
    }
    val viewLogsSwitch = SwitchCompat(context).apply {
        id = R.id.staff_perm_view_logs
        setText(R.string.staff_permission_view_logs)
        isChecked = initial?.canViewLogs == true
    }
    val manageRewardsSwitch = SwitchCompat(context).apply {
        id = R.id.staff_perm_manage_rewards
        setText(R.string.staff_permission_manage_rewards)
        isChecked = initial?.canManageRewards == true
    }

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        listOf(scanSwitch, printIdSwitch, viewLogsSwitch, manageRewardsSwitch).forEach { switch ->
            switch.minHeight = (48 * resources.displayMetrics.density).toInt()
            addView(switch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Applies the current switch positions to [staff] (Scan QR is always granted). */
    fun applyTo(staff: OrganizerMvpStaff): OrganizerMvpStaff = staff.withFlags(
        printId = printIdSwitch.isChecked,
        viewLogs = viewLogsSwitch.isChecked,
        manageRewards = manageRewardsSwitch.isChecked,
    )
}

/** "Scan, Print IDs" style summary of the real flags on [staff]. */
fun staffPermissionSummary(context: Context, staff: OrganizerMvpStaff): String =
    staffPermissionSummary(
        scan = staff.canScan,
        printId = staff.canPrintId,
        viewLogs = staff.canViewLogs,
        manageRewards = staff.canManageRewards,
        labels = listOf(
            context.getString(R.string.staff_permission_summary_scan),
            context.getString(R.string.staff_permission_summary_print_ids),
            context.getString(R.string.staff_permission_summary_view_logs),
            context.getString(R.string.staff_permission_summary_manage_rewards),
        ),
        none = context.getString(R.string.staff_permission_summary_none),
    )

internal fun staffPermissionSummary(
    scan: Boolean,
    printId: Boolean,
    viewLogs: Boolean,
    manageRewards: Boolean,
    labels: List<String>,
    none: String,
): String {
    val parts = listOf(scan, printId, viewLogs, manageRewards).zip(labels).filter { it.first }.map { it.second }
    return if (parts.isEmpty()) none else parts.joinToString(", ")
}
