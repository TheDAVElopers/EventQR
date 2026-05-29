package com.thedavelopers.eventqr.features.organizer.staff

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.resolveSelectedEvent
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import kotlinx.coroutines.launch

open class ManageUsersActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var recyclerStaff: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateText: TextView
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

        val eventId = intentEventId() ?: return showMissingEventScreen("Staff Assignment")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId)
            ?: return showMissingEventScreen("Staff Assignment")

        setContentView(R.layout.activity_staff_assignment)
        bindViews()
        setupList()
        bindActions()
        loadAssigned()
    }

    private fun bindViews() {
        recyclerStaff = findViewById(R.id.recyclerStaffAssignment)
        progressBar = findViewById(R.id.progressStaffAssignment)
        emptyStateText = findViewById(R.id.txtEmptyStaff)
    }

    private fun setupList() {
        staffAdapter = StaffAssignmentAdapter { staff -> confirmRemove(staff) }
        recyclerStaff.layoutManager = LinearLayoutManager(this)
        recyclerStaff.adapter = staffAdapter
    }

    private fun bindActions() {
        findViewById<ImageButton>(R.id.btnStaffAssignmentBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAddStaff).setOnClickListener { openSearchUserPage() }
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

    private fun loadAssigned(showAlreadyAssignedRefreshFailureIfEmpty: Boolean = false) {
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE
        recyclerStaff.visibility = View.VISIBLE

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

            source.message?.let {
                Toast.makeText(this@ManageUsersActivity, it, Toast.LENGTH_SHORT).show()
            }

            val renderedCount = renderAssigned()
            if (showAlreadyAssignedRefreshFailureIfEmpty && renderedCount == 0) {
                Toast.makeText(
                    this@ManageUsersActivity,
                    "Staff is already assigned, but assigned staff list could not be refreshed.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            if (source.source == OrganizerMvpDataSource.ERROR && renderedCount == 0) {
                emptyStateText.text = source.message ?: getString(R.string.staff_assignment_empty)
            }
            progressBar.visibility = View.GONE
        }
    }

    private fun renderAssigned(): Int {
        val staff = assignedStaff.filter {
            it.assignedEventId.isBlank() || it.assignedEventId == selectedEvent.id
        }
        staffAdapter.submitItems(staff)
        val hasStaff = staff.isNotEmpty()
        emptyStateText.visibility = if (hasStaff) View.GONE else View.VISIBLE
        recyclerStaff.visibility = if (hasStaff) View.VISIBLE else View.GONE
        emptyStateText.text = getString(R.string.staff_assignment_empty)
        return staff.size
    }

    private fun confirmRemove(staff: OrganizerMvpStaff) {
        AlertDialog.Builder(this)
            .setTitle("Remove staff?")
            .setMessage("Remove ${staff.name} from ${staff.assignedEvent}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                removeStaff(staff)
            }
            .show()
    }

    private fun removeStaff(staff: OrganizerMvpStaff) {
        lifecycleScope.launch {
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
}
