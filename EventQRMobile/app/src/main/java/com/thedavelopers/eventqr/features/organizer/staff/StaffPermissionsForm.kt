package com.thedavelopers.eventqr.features.organizer.staff

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff
import com.thedavelopers.eventqr.ui.theme.dimenOffset

/**
 * The one permission surface, used for assigning and for editing a staff member (6.4). A bottom
 * sheet with the 5.12 anatomy: title, identity line, a locked "Scan a pass" value row, three
 * toggle rows, an optional promotion note, an inline error and one primary action.
 *
 * Scan stays on by contract (the assignment payload always sends `canScan = true`), so it is shown
 * as a fact, not a disabled control. The sheet carries no destructive action; removal lives in the
 * roster row's overflow menu.
 */
class StaffPermissionsForm(context: Context, initial: OrganizerMvpStaff? = null) {
    private val context: Context = context
    private val sheet: View = LayoutInflater.from(context).inflate(R.layout.sheet_bottom_base, null, false)

    val printIdSwitch: SwitchCompat
    val viewLogsSwitch: SwitchCompat
    val manageRewardsSwitch: SwitchCompat

    private val identityText = TextView(context)
    private val promoteText = TextView(context)
    private val errorText = TextView(context)
    private val primaryAction: Button = sheet.findViewById(R.id.sheetPrimaryAction)
    private var dialog: BottomSheetDialog? = null

    /** The sheet root, already composed. Hosts that need to inspect it (tests) use this. */
    val view: View get() = sheet

    init {
        sheet.findViewById<TextView>(R.id.sheetTitle).text = context.getString(R.string.ui_staff_permissions_title)
        sheet.findViewById<Button>(R.id.sheetSecondaryAction).visibility = View.GONE

        val content = sheet.findViewById<android.widget.FrameLayout>(R.id.sheetContent)
        // The rows carry their own ui_row_start inset; the base slot's gutter would double it.
        content.setPadding(0, content.paddingTop, 0, 0)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(
            column,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        identityText.apply {
            setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
            setTextColor(ContextCompat.getColor(context, R.color.ui_text_secondary))
            setPadding(dimenOffset(R.dimen.ui_gutter), 0, dimenOffset(R.dimen.ui_gutter), 0)
        }
        column.addView(identityText)

        // Scan: a value row, not a switch that can never move.
        val scanRow = LayoutInflater.from(context).inflate(R.layout.list_row_value, column, false)
        scanRow.findViewById<TextView>(R.id.listRowTitle).text = context.getString(R.string.ui_staff_scan_locked)
        scanRow.findViewById<TextView>(R.id.listRowMeta).apply {
            text = context.getString(R.string.ui_staff_scan_locked_meta)
            visibility = View.VISIBLE
        }
        scanRow.findViewById<TextView>(R.id.listRowValue).text = context.getString(R.string.ui_staff_scan_locked_value)
        scanRow.findViewById<View>(R.id.listRowContainer).apply {
            isClickable = false
            isFocusable = false
            background = null
        }
        column.addView(scanRow)

        printIdSwitch = addToggle(
            column,
            R.string.staff_permission_print_ids,
            R.string.staff_permission_print_ids_helper,
            initial?.canPrintId == true,
        )
        viewLogsSwitch = addToggle(column, R.string.staff_permission_view_logs, null, initial?.canViewLogs == true)
        manageRewardsSwitch = addToggle(column, R.string.staff_permission_manage_rewards, null, initial?.canManageRewards == true)

        promoteText.apply {
            setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
            setTextColor(ContextCompat.getColor(context, R.color.ui_text_secondary))
            setPadding(dimenOffset(R.dimen.ui_row_start), dimenOffset(R.dimen.space_12), dimenOffset(R.dimen.ui_row_end), 0)
            visibility = View.GONE
        }
        column.addView(promoteText)

        errorText.apply {
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Micro)
            setTextColor(ContextCompat.getColor(context, R.color.ui_error))
            setPadding(dimenOffset(R.dimen.ui_row_start), dimenOffset(R.dimen.space_8), dimenOffset(R.dimen.ui_row_end), 0)
            visibility = View.GONE
        }
        column.addView(errorText)
    }

    /** Opens the sheet for [staff]. The caller owns the outcome (assign or save). */
    fun show(
        staff: OrganizerMvpStaff,
        title: String,
        identity: String,
        primaryLabel: String,
        promoteNote: String? = null,
        onSubmit: (OrganizerMvpStaff, StaffPermissionsForm) -> Unit,
    ): StaffPermissionsForm {
        sheet.findViewById<TextView>(R.id.sheetTitle).text = title
        identityText.text = identity
        promoteText.text = promoteNote
        promoteText.visibility = if (promoteNote.isNullOrBlank()) View.GONE else View.VISIBLE
        errorText.visibility = View.GONE
        primaryAction.text = primaryLabel
        primaryAction.isEnabled = true
        primaryAction.setOnClickListener { onSubmit(applyTo(staff), this) }

        dialog = BottomSheetDialog(sheet.context).apply {
            setContentView(sheet)
            setOnDismissListener { dialog = null }
        }.also { it.show() }
        return this
    }

    /** Applies the current switch positions to [staff] (Scan QR is always granted). */
    fun applyTo(staff: OrganizerMvpStaff): OrganizerMvpStaff = staff.withFlags(
        printId = printIdSwitch.isChecked,
        viewLogs = viewLogsSwitch.isChecked,
        manageRewards = manageRewardsSwitch.isChecked,
    )

    /** Keeps the sheet open and states why the action did not go through. */
    fun showError(message: String?) {
        errorText.text = message
        errorText.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        primaryAction.isEnabled = true
    }

    fun setSubmitting(submitting: Boolean) {
        primaryAction.isEnabled = !submitting
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    private fun addToggle(
        container: LinearLayout,
        titleRes: Int,
        metaRes: Int?,
        checked: Boolean,
    ): SwitchCompat {
        val row = LayoutInflater.from(context).inflate(R.layout.item_organizer_toggle_row, container, false)
        val title = context.getString(titleRes)
        row.findViewById<TextView>(R.id.txtToggleTitle).text = title
        row.findViewById<TextView>(R.id.txtToggleMeta).apply {
            val meta = metaRes?.let(context::getString)
            text = meta
            visibility = if (meta.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val switch = row.findViewById<SwitchCompat>(R.id.switchToggle).apply {
            isChecked = checked
            contentDescription = title
        }
        container.addView(row)
        return switch
    }
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
