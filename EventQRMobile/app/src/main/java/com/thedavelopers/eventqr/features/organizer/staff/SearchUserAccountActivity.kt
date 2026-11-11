package com.thedavelopers.eventqr.features.organizer.staff

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.ScreenInsets
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

/**
 * Add staff (6.4): search an account, pick one, then set permissions in the shared sheet and assign.
 *
 * Outcomes stay in the flow: the assigned row leaves the results and a confirmation strip names the
 * person, so an organizer can assign the next one without leaving the screen. The screen has no
 * dialog of its own - the promotion note lives in the sheet.
 */
class SearchUserAccountActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ACTIVE_STAFF_EMAILS = "extra_active_staff_emails"
    }

    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var searchField: EditText
    private lateinit var recyclerUsers: RecyclerView
    private lateinit var emptyStateText: EventQrEmptyState
    private lateinit var assignButton: Button
    private lateinit var userAdapter: SearchUserAccountAdapter
    private lateinit var assignedStrip: View
    private lateinit var assignedStripText: TextView

    private var resultUsers: List<OrganizerMvpStaff> = emptyList()
    private var selectedUser: OrganizerMvpStaff? = null
    private var assigning = false
    private val activeStaffEmails = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)

        lifecycleScope.launch {
            val eventId = intentEventId() ?: return@launch showMissingEventScreen(getString(R.string.search_user_account_title))
            showEventsLoadingScreen(getString(R.string.search_user_account_title))
            val eventsLoad = repository.loadApprovedOrganizerEvents()
            if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
                showEventsLoadErrorScreen(getString(R.string.search_user_account_title), eventsLoad.message) { recreate() }
                return@launch
            }
            selectedEvent = resolveSelectedEvent(eventsLoad.data, eventId)
                ?: run {
                    showMissingEventScreen(getString(R.string.search_user_account_title))
                    return@launch
                }

            setContentView(R.layout.activity_search_user_account)
            ScreenInsets.padContentForNavigationBar(this@SearchUserAccountActivity)
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
        FieldIcons.apply(searchField)
        recyclerUsers = findViewById(R.id.recyclerSearchUsers)
        emptyStateText = findViewById(R.id.txtSearchEmpty)
        assignButton = findViewById(R.id.btnAssignStaff)
        assignedStrip = findViewById(R.id.staffAssignedStrip)
        assignedStripText = findViewById(R.id.txtStaffAssignedStrip)
        findViewById<TextView>(R.id.eventStripTitle).text = selectedEvent.title
        findViewById<View>(R.id.eventStripAction).visibility = View.GONE
    }

    private fun setupList() {
        userAdapter = SearchUserAccountAdapter { user ->
            selectedUser = user
            assignButtonState()
        }
        recyclerUsers.layoutManager = LinearLayoutManager(this)
        recyclerUsers.adapter = userAdapter
    }

    private fun bindActions() {
        findViewById<View>(R.id.topBarBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.topBarTitle).text = getString(R.string.ui_staff_search_title)
        searchField.doAfterTextChanged {
            hideAssignedStrip()
            loadUsers()
        }
        assignButton.setOnClickListener { openPermissionsSheet() }
        assignButtonState()
    }

    private fun loadUsers() {
        val query = searchField.text?.toString().orEmpty()
        lifecycleScope.launch {
            val source = repository.searchStaffUsersForMvp(query)
            resultUsers = source.data.map {
                it.copy(assignedEventId = selectedEvent.id, assignedEvent = selectedEvent.title)
            }
            userAdapter.submitItems(resultUsers)
            selectedUser = userAdapter.selectedUser()
            assignButtonState()

            val isEmpty = resultUsers.isEmpty()
            emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
            if (isEmpty) {
                // A blank field is "nothing searched yet", not "account not found".
                if (query.isBlank()) {
                    showEmptyState(
                        R.string.ui_staff_search_prompt,
                        R.string.ui_staff_search_prompt_body,
                    )
                } else {
                    showEmptyState(
                        R.string.ui_staff_search_none,
                        R.string.ui_staff_search_none_body,
                    )
                }
            }
        }
    }

    private fun showEmptyState(titleRes: Int, bodyRes: Int) {
        emptyStateText.setIcon(AppIcons.Search)
        emptyStateText.setContent(getString(titleRes), getString(bodyRes))
        emptyStateText.setAction(null, false, null)
    }

    private fun assignButtonState() {
        assignButton.isEnabled = selectedUser != null && !assigning
    }

    private fun openPermissionsSheet() {
        val user = selectedUser ?: return
        StaffPermissionsForm(this, user).show(
            staff = user,
            title = getString(R.string.ui_staff_permissions_title),
            identity = getString(R.string.ui_staff_permissions_identity, user.name, user.email),
            primaryLabel = getString(R.string.staff_assign_button),
            promoteNote = if (user.willBePromotedToStaff) {
                getString(R.string.ui_staff_promote_note)
            } else {
                null
            },
            onSubmit = { updated, form -> performAssign(updated, form) },
        )
    }

    private fun performAssign(user: OrganizerMvpStaff, form: StaffPermissionsForm) {
        val normalizedEmail = user.email.trim().lowercase()
        if (activeStaffEmails.contains(normalizedEmail)) {
            form.showError(getString(R.string.search_user_account_duplicate_staff_assignment))
            return
        }

        assigning = true
        assignButtonState()
        form.setSubmitting(true)
        lifecycleScope.launch {
            val source = repository.addStaffForMvp(selectedEvent, user)
            assigning = false
            assignButtonState()

            if (source.source == OrganizerMvpDataSource.BACKEND) {
                form.dismiss()
                activeStaffEmails.add(normalizedEmail)
                showAssigned(user)
                return@launch
            }

            val alreadyAssigned = source.message?.contains("already assigned", ignoreCase = true) == true
            if (alreadyAssigned) {
                form.dismiss()
                setResult(RESULT_OK)
                finish()
                return@launch
            }
            form.showError(source.message ?: getString(R.string.staff_permissions_update_failed))
        }
    }

    /** The row leaves the results; the strip says why and clears on the next keystroke. */
    private fun showAssigned(user: OrganizerMvpStaff) {
        val email = user.email.trim().lowercase()
        resultUsers = resultUsers.filterNot { it.email.trim().lowercase() == email }
        userAdapter.submitItems(resultUsers)
        selectedUser = null
        assignButtonState()
        emptyStateText.visibility = if (resultUsers.isEmpty()) View.VISIBLE else View.GONE
        if (resultUsers.isEmpty()) {
            if (searchField.text.isNullOrBlank()) {
                showEmptyState(R.string.ui_staff_search_prompt, R.string.ui_staff_search_prompt_body)
            } else {
                showEmptyState(R.string.ui_staff_search_none, R.string.ui_staff_search_none_body)
            }
        }
        assignedStripText.text = getString(R.string.ui_staff_assigned, user.name)
        assignedStrip.visibility = View.VISIBLE
    }

    private fun hideAssignedStrip() {
        assignedStrip.visibility = View.GONE
    }
}
