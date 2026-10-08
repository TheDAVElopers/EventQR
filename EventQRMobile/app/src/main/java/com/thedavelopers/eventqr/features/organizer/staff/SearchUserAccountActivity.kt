package com.thedavelopers.eventqr.features.organizer.staff

import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.resolveSelectedEvent
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import kotlinx.coroutines.launch

class SearchUserAccountActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ACTIVE_STAFF_EMAILS = "extra_active_staff_emails"
    }

    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var searchField: EditText
    private lateinit var recyclerUsers: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateText: EventQrEmptyState
    private lateinit var assignButton: Button
    private lateinit var userAdapter: SearchUserAccountAdapter

    private lateinit var permissionsHost: android.widget.LinearLayout
    private var permissionsForm: StaffPermissionsForm? = null
    private var selectedUser: OrganizerMvpStaff? = null
    private var assigning = false
    private val activeStaffEmails = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)

        lifecycleScope.launch {
            val eventId = intentEventId() ?: return@launch showMissingEventScreen("Search User Account")
            selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId)
                ?: run {
                    showMissingEventScreen("Search User Account")
                    return@launch
                }

            setContentView(R.layout.activity_search_user_account)
            activeStaffEmails.addAll(
                intent.getStringArrayListExtra(EXTRA_ACTIVE_STAFF_EMAILS)
                    .orEmpty()
                    .map { it.trim().lowercase() },
            )

            bindViews()
            setupList()
            bindActions()
            loadUsers()
        }
    }

    private fun bindViews() {
        searchField = findViewById(R.id.edtSearchUser)
        recyclerUsers = findViewById(R.id.recyclerSearchUsers)
        progressBar = findViewById(R.id.progressSearchUsers)
        emptyStateText = findViewById(R.id.txtSearchEmpty)
        assignButton = findViewById(R.id.btnAssignStaff)
        permissionsHost = findViewById(R.id.layoutPermissionsHost)
    }

    private fun setupList() {
        userAdapter = SearchUserAccountAdapter { user ->
            selectedUser = user
            assignButtonState()
        }
        recyclerUsers.layoutManager = LinearLayoutManager(this)
        recyclerUsers.adapter = userAdapter
        // Scan QR is always on; Print IDs, View logs and Manage rewards default to off.
        permissionsForm = StaffPermissionsForm(this).also { form -> permissionsHost.addView(form.view) }
    }

    private fun bindActions() {
        findViewById<ImageButton>(R.id.nav_header_back).setOnClickListener { finish() }
        searchField.doAfterTextChanged {
            loadUsers()
        }
        assignButton.setOnClickListener {
            assignSelectedUser()
        }
        assignButtonState()
    }

    private fun loadUsers() {
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE

        val query = searchField.text?.toString().orEmpty()
        lifecycleScope.launch {
            val source = repository.searchStaffUsersForMvp(query)
            progressBar.visibility = View.GONE

            source.message?.let {
                Toast.makeText(this@SearchUserAccountActivity, it, Toast.LENGTH_SHORT).show()
            }

            val users = source.data.map {
                it.copy(assignedEventId = selectedEvent.id, assignedEvent = selectedEvent.title)
            }
            userAdapter.submitItems(users)
            selectedUser = userAdapter.selectedUser()
            assignButtonState()

            if (users.isEmpty()) {
                emptyStateText.text = source.message ?: getString(R.string.staff_search_empty)
                emptyStateText.visibility = View.VISIBLE
            } else {
                emptyStateText.visibility = View.GONE
            }
        }
    }

    private fun assignSelectedUser() {
        val picked = selectedUser ?: return
        val user = permissionsForm?.applyTo(picked) ?: picked
        if (user.willBePromotedToStaff) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.staff_promote_title))
                .setMessage(getString(R.string.staff_promote_message, user.name))
                .setNegativeButton(getString(R.string.request_event_cancel), null)
                .setPositiveButton(getString(R.string.staff_assign_button)) { _, _ -> performAssign(user) }
                .show()
            return
        }
        performAssign(user)
    }

    private fun performAssign(user: OrganizerMvpStaff) {
        val normalizedEmail = user.email.trim().lowercase()
        if (activeStaffEmails.contains(normalizedEmail)) {
            Toast.makeText(this, this.getString(R.string.search_user_account_duplicate_staff_assignment), Toast.LENGTH_SHORT).show()
            return
        }

        assigning = true
        assignButtonState()
        lifecycleScope.launch {
            val source = repository.addStaffForMvp(selectedEvent, user)
            source.message?.let {
                Toast.makeText(this@SearchUserAccountActivity, it, Toast.LENGTH_SHORT).show()
            }
            if (source.source == OrganizerMvpDataSource.BACKEND && source.data.promotedToStaff) {
                Toast.makeText(
                    this@SearchUserAccountActivity,
                    getString(R.string.staff_promoted_toast, source.data.name),
                    Toast.LENGTH_LONG,
                ).show()
            }

            val alreadyAssigned = source.message?.contains("already assigned", ignoreCase = true) == true
            if (source.source == OrganizerMvpDataSource.BACKEND) {
                showSuccessDialog()
                return@launch
            }
            if (alreadyAssigned) {
                setResult(RESULT_OK)
                finish()
                return@launch
            }
            assigning = false
            assignButtonState()
        }
    }

    private fun assignButtonState() {
        val canAssign = selectedUser != null && !assigning
        assignButton.isEnabled = canAssign
        assignButton.text = if (assigning) getString(R.string.staff_assigning) else getString(R.string.staff_assign_button)
        permissionsHost.visibility = if (selectedUser != null) View.VISIBLE else View.GONE
    }

    private fun showSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_staff_assigned, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<Button>(R.id.btnDoneAssigned).setOnClickListener {
            dialog.dismiss()
            setResult(RESULT_OK)
            finish()
        }
        dialog.show()
    }
}
