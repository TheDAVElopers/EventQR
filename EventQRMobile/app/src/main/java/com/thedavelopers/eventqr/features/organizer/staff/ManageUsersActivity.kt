package com.thedavelopers.eventqr.features.organizer.staff

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.resolveSelectedEvent
import com.thedavelopers.eventqr.features.organizer.showEventsLoadErrorScreen
import com.thedavelopers.eventqr.features.organizer.showEventsLoadingScreen
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.icons.FieldIcons
import kotlinx.coroutines.launch

open class ManageUsersActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var recyclerStaff: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyStateText: EventQrEmptyState
    private lateinit var staffAdapter: StaffAssignmentAdapter
    private val assignedStaff = mutableListOf<OrganizerMvpStaff>()

    private val searchUserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                loadAssigned()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)

        lifecycleScope.launch {
            val eventId = intentEventId() ?: return@launch showMissingEventScreen(getString(R.string.manage_users_title))
            showEventsLoadingScreen(getString(R.string.manage_users_title))
            val eventsLoad = repository.loadApprovedOrganizerEvents()
            if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
                showEventsLoadErrorScreen(getString(R.string.manage_users_title), eventsLoad.message) { recreate() }
                return@launch
            }
            selectedEvent = resolveSelectedEvent(eventsLoad.data, eventId)
                ?: run {
                    showMissingEventScreen(getString(R.string.manage_users_title))
                    return@launch
                }

            setContentView(R.layout.activity_staff_assignment)
            ScreenInsets.padContentForNavigationBar(this@ManageUsersActivity)
            bindViews()
            setupList()
            bindActions()
            loadAssigned()
        }
    }

    private fun bindViews() {
        swipeRefresh = findViewById(R.id.swipeRefreshStaffAssignment)
        recyclerStaff = findViewById(R.id.recyclerStaffAssignment)
        emptyStateText = findViewById(R.id.txtEmptyStaff)
        findViewById<TextView>(R.id.eventStripTitle).text = selectedEvent.title
        FieldIcons.apply(findViewById(R.id.eventStripAction))
        swipeRefresh.setOnRefreshListener { loadAssigned() }
    }

    private fun setupList() {
        staffAdapter = StaffAssignmentAdapter(
            onRemoveClicked = { staff -> confirmRemove(staff) },
            onEditPermissionsClicked = { staff -> showEditPermissions(staff) },
        )
        recyclerStaff.layoutManager = LinearLayoutManager(this)
        recyclerStaff.adapter = staffAdapter
    }

    private fun bindActions() {
        findViewById<View>(R.id.topBarBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.topBarTitle).text = getString(R.string.staff_assignment_title)
        findViewById<View>(R.id.eventStripAction).setOnClickListener { openSearchUserPage() }
    }

    private fun openSearchUserPage() {
        val activeStaffEmails = assignedStaff
            .filter {
                it.assignedEventId == selectedEvent.id &&
                    it.accessStatus.equals("Active", ignoreCase = true)
            }
            .map { it.email.lowercase() }
            .distinct()
            .let { ArrayList(it) }

        val intent = Intent(this, SearchUserAccountActivity::class.java).apply {
            putExtra(EXTRA_EVENT_ID, selectedEvent.id)
            putExtra(EXTRA_EVENT_TITLE, selectedEvent.title)
            putStringArrayListExtra(SearchUserAccountActivity.EXTRA_ACTIVE_STAFF_EMAILS, activeStaffEmails)
        }
        searchUserLauncher.launch(intent)
    }

    private fun loadAssigned() {
        lifecycleScope.launch {
            val source = repository.loadStaffForMvp(selectedEvent)
            assignedStaff.clear()
            source.data.forEach { incoming ->
                val existingIndex = assignedStaff.indexOfFirst { existing ->
                    existing.id == incoming.id ||
                        (
                            existing.email.equals(incoming.email, ignoreCase = true) &&
                                existing.assignedEventId == incoming.assignedEventId
                            )
                }
                if (existingIndex >= 0) {
                    assignedStaff[existingIndex] = incoming
                } else {
                    assignedStaff.add(incoming)
                }
            }

            swipeRefresh.isRefreshing = false
            val renderedCount = renderAssigned()
            if (renderedCount == 0 && source.source == OrganizerMvpDataSource.ERROR) {
                showLoadError(source.message)
            }
        }
    }

    private fun renderAssigned(): Int {
        val staff = assignedStaff.filter {
            it.assignedEventId.isBlank() || it.assignedEventId == selectedEvent.id
        }
        staffAdapter.submitItems(staff)
        val hasStaff = staff.isNotEmpty()
        recyclerStaff.visibility = if (hasStaff) View.VISIBLE else View.GONE
        emptyStateText.visibility = if (hasStaff) View.GONE else View.VISIBLE
        if (!hasStaff) showEmptyState()
        return staff.size
    }

    private fun showEmptyState() {
        emptyStateText.setIcon(AppIcons.People)
        emptyStateText.setContent(
            getString(R.string.ui_staff_no_staff),
            getString(R.string.ui_staff_no_staff_body),
        )
        emptyStateText.setAction(getString(R.string.ui_organizer_add_staff), primary = true) {
            openSearchUserPage()
        }
    }

    private fun showLoadError(message: String?) {
        emptyStateText.setIcon(AppIcons.Warning)
        emptyStateText.setContent(
            getString(R.string.ui_staff_load_failed),
            message?.takeIf { it.isNotBlank() } ?: getString(R.string.ui_staff_load_failed_body),
        )
        emptyStateText.setAction(getString(R.string.common_retry)) { loadAssigned() }
    }

    private fun showEditPermissions(staff: OrganizerMvpStaff) {
        StaffPermissionsForm(this, staff).show(
            staff = staff,
            title = getString(R.string.staff_edit_permissions_title, staff.name),
            identity = getString(R.string.ui_staff_permissions_identity, staff.name, staff.email),
            primaryLabel = getString(R.string.manage_scan_purposes_save),
            onSubmit = { updated, form -> savePermissions(updated, form) },
        )
    }

    private fun savePermissions(updated: OrganizerMvpStaff, form: StaffPermissionsForm) {
        form.setSubmitting(true)
        lifecycleScope.launch {
            // updated.id is the ASSIGNMENT id, as the PATCH endpoint expects.
            val source = repository.updateStaffForMvp(selectedEvent, updated)
            if (source.source == OrganizerMvpDataSource.BACKEND) {
                form.dismiss()
                loadAssigned()
            } else {
                form.showError(source.message ?: getString(R.string.staff_permissions_update_failed))
            }
        }
    }

    private fun confirmRemove(staff: OrganizerMvpStaff) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_base, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.dialogTitle).text =
            getString(R.string.manage_users_remove_staff)
        dialogView.findViewById<TextView>(R.id.dialogBody).text =
            getString(R.string.manage_users_remove_confirm, staff.name, staff.assignedEvent)
        dialogView.findViewById<Button>(R.id.dialogNegative).apply {
            text = getString(R.string.request_event_cancel)
            setOnClickListener { dialog.dismiss() }
        }
        dialogView.findViewById<Button>(R.id.dialogPositive).apply {
            text = getString(R.string.staff_remove)
            setBackgroundResource(R.drawable.bg_ui_button_destructive_commit)
            setTextColor(ContextCompat.getColor(context, R.color.ui_on_primary))
            setOnClickListener {
                dialog.dismiss()
                removeStaff(staff)
            }
        }
        dialog.show()
    }

    private fun removeStaff(staff: OrganizerMvpStaff) {
        lifecycleScope.launch {
            val source = repository.removeStaffForMvp(selectedEvent, staff)
            if (source.source == OrganizerMvpDataSource.BACKEND) {
                assignedStaff.removeAll { it.id == staff.id && it.assignedEventId == staff.assignedEventId }
                renderAssigned()
            } else {
                Snackbar.make(
                    recyclerStaff,
                    source.message ?: getString(R.string.staff_permissions_update_failed),
                    Snackbar.LENGTH_LONG,
                ).show()
            }
        }
    }
}
