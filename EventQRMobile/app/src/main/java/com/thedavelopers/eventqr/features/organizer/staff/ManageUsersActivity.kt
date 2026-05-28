package com.thedavelopers.eventqr.features.organizer.staff

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class ManageUsersActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var search: EditText
    private lateinit var results: LinearLayout
    private lateinit var assigned: LinearLayout
    private val assignedStaff = mutableListOf<OrganizerMvpStaff>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Staff Access")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId) ?: return showMissingEventScreen("Staff Access")
        val content = organizerShell("Staff Access", selectedEvent.title, showBack = true, topRightLabel = "+") {
            renderSearch(showOnlyWhenQuery = false)
        }
        content.addView(card().apply {
            addView(text("Total Staff", 12, false, MUTED))
            addView(text(selectedEvent.staffCount.toString(), 24, true))
        })
        content.addView(primaryButton("+ Add Staff Member") { renderSearch(showOnlyWhenQuery = false) })
        search = EditText(this).apply {
            hint = "Search user by email/name"
            background = rounded(Color.WHITE, 10, BORDER, density = resources.displayMetrics.density)
            setPadding(dp(12), 0, dp(12), 0)
        }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        assigned = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(search)
        content.addView(section("Search Results"))
        content.addView(results)
        content.addView(section("Staff Members"))
        content.addView(assigned)
        search.afterTextChanged { renderSearch() }
        renderSearch()
        loadAssigned()
    }

    private fun renderSearch(showOnlyWhenQuery: Boolean = true) {
        if (!::results.isInitialized) return
        val query = search.text.toString()
        results.removeAllViews()
        if (showOnlyWhenQuery && query.isBlank()) {
            results.addView(text("Search by name or email to add staff.", 13, false, MUTED))
            return
        }
        results.addView(loadingState("Searching users..."))
        MainScope().launch {
            val source = repository.searchStaffUsersForMvp(query)
            results.removeAllViews()
            dataSourceBanner(source)?.let { results.addView(it) }
            if (source.data.isEmpty()) {
                results.addView(text("User not found.", 13, true, ERROR))
                return@launch
            }
            source.data.forEach { user ->
                results.addView(staffCard(user.copy(assignedEventId = selectedEvent.id, assignedEvent = selectedEvent.title), true))
            }
        }
    }

    private fun renderAssigned() {
        assigned.removeAllViews()
        val staff = assignedStaff.filter { it.assignedEventId == selectedEvent.id }
        if (staff.isEmpty()) {
            assigned.addView(emptyState("Empty staff list. Add staff members before event day."))
            return
        }
        staff.forEach { assigned.addView(staffCard(it, false)) }
        assigned.addView(stateCard())
    }

    private fun loadAssigned() {
        assigned.removeAllViews()
        assigned.addView(loadingState("Loading staff..."))
        MainScope().launch {
            val source = repository.loadStaffForMvp(selectedEvent)
            assignedStaff.clear()
            assignedStaff.addAll(source.data)
            source.message?.let {
                Toast.makeText(this@ManageUsersActivity, it, Toast.LENGTH_SHORT).show()
            }
            renderAssigned()
        }
    }

    private fun staffCard(staff: OrganizerMvpStaff, canAdd: Boolean): LinearLayout =
        card().apply {
            val top = row()
            top.addView(text(staff.name, 17, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            top.addView(badge(staff.accessStatus))
            addView(top)
            addView(chip(staff.roleLabel))
            addView(text(staff.email, 13, false, MUTED))
            addView(text("Permissions: ${staff.permissions.joinToString(", ")}", 12, false, MUTED))
            addView(text("Added: ${staff.addedDate}", 12, false, MUTED))
            if (canAdd) {
                addView(primaryButton("Add staff to event") {
                    if (assignedStaff.any { it.email.equals(staff.email, ignoreCase = true) && it.accessStatus.equals("Active", ignoreCase = true) }) {
                        Toast.makeText(this@ManageUsersActivity, "Duplicate staff assignment", Toast.LENGTH_SHORT).show()
                    } else {
                        MainScope().launch {
                            val source = repository.addStaffForMvp(selectedEvent, staff)
                            if (source.source == OrganizerMvpDataSource.BACKEND) {
                                assignedStaff.add(source.data)
                            }
                            source.message?.let {
                                Toast.makeText(this@ManageUsersActivity, it, Toast.LENGTH_SHORT).show()
                            }
                            renderAssigned()
                        }
                    }
                })
            } else {
                addView(ghostButton("Enable/disable staff access") {
                    val index = assignedStaff.indexOfFirst { it.id == staff.id && it.assignedEventId == staff.assignedEventId }
                    if (index >= 0) {
                        val nextStatus = if (assignedStaff[index].accessStatus == "Active") "Disabled" else "Active"
                        val updated = assignedStaff[index].copy(accessStatus = nextStatus)
                        MainScope().launch {
                            val source = repository.updateStaffForMvp(selectedEvent, updated)
                            if (source.source == OrganizerMvpDataSource.BACKEND) {
                                assignedStaff[index] = source.data
                            }
                            source.message?.let {
                                Toast.makeText(this@ManageUsersActivity, it, Toast.LENGTH_SHORT).show()
                            }
                            renderAssigned()
                        }
                    }
                })
                addView(ghostButton("View/edit staff permissions") {
                    AlertDialog.Builder(this@ManageUsersActivity)
                        .setTitle(staff.name)
                        .setMessage("Role: ${staff.roleLabel}\nPermissions: ${staff.permissions.joinToString(", ")}")
                        .setPositiveButton("Close", null)
                        .show()
                })
                addView(ghostButton("Remove staff from event") {
                    AlertDialog.Builder(this@ManageUsersActivity)
                        .setTitle("Remove staff?")
                        .setMessage("Remove ${staff.name} from ${staff.assignedEvent}?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ ->
                            MainScope().launch {
                                val source = repository.removeStaffForMvp(selectedEvent, staff)
                                if (source.source == OrganizerMvpDataSource.BACKEND) {
                                    assignedStaff.removeAll { it.id == staff.id && it.assignedEventId == staff.assignedEventId }
                                }
                                source.message?.let {
                                    Toast.makeText(this@ManageUsersActivity, it, Toast.LENGTH_SHORT).show()
                                }
                                renderAssigned()
                            }
                        }
                        .show()
                })
            }
        }
}
