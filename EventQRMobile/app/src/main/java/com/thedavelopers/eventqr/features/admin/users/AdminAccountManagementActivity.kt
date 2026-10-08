package com.thedavelopers.eventqr.features.admin.users

import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.AccountStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.admin.AdminBottomNavItem
import com.thedavelopers.eventqr.features.admin.AdminEventApprovalBackendActivity
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.features.admin.configureAdminBottomNav
import com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity
import com.thedavelopers.eventqr.features.admin.logs.AdminAuditLogsActivity
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.core.util.PagedAccumulator
import com.thedavelopers.eventqr.core.util.addNearEndListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdminAccountManagementActivity : AppCompatActivity() {
    private lateinit var repository: AdminRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: AdminAccountAdapter
    private lateinit var searchInput: EditText
    private lateinit var recyclerAccounts: RecyclerView
    private lateinit var progressLoading: ProgressBar
    private lateinit var textPlaceholder: EventQrEmptyState
    private lateinit var filterChipsLayout: ChipGroup

    private val paging = PagedAccumulator<UserResponse, java.util.UUID> { it.userId }
    private var searchQuery: String = ""
    private var searchJob: Job? = null
    private var retrySnackbar: Snackbar? = null

    override fun onDestroy() {
        retrySnackbar?.dismiss()
        super.onDestroy()
    }
    private var selectedRoleFilter: AccountRole? = null
    private val currentUserId: String? by lazy { sessionManager.getUserId() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_account_management)

        repository = AdminRepository(this)
        sessionManager = SessionManager(this)
        adapter = AdminAccountAdapter(onActionClick = ::onAccountActionClick)
        bindViews()
        bindNav()
        bindSearch()
        setupRoleFilterChips()
    }

    override fun onResume() {
        super.onResume()
        loadAccounts()
    }

    private fun bindViews() {
        searchInput = findViewById(R.id.inputAccountSearch)
        recyclerAccounts = findViewById(R.id.recyclerAdminAccounts)
        progressLoading = findViewById(R.id.progressAccountsLoading)
        textPlaceholder = findViewById(R.id.textAccountsPlaceholder)
        filterChipsLayout = findViewById(R.id.filterChipsLayout)
        recyclerAccounts.layoutManager = LinearLayoutManager(this)
        recyclerAccounts.adapter = adapter

        val isSuperAdmin = isSuperAdmin()
        findViewById<View>(R.id.buttonCreateAdminAccount).visibility = if (isSuperAdmin) View.VISIBLE else View.GONE
        findViewById<View>(R.id.buttonCreateAdminAccount).setOnClickListener {
            startActivity(Intent(this, CreateAdminAccountActivity::class.java))
        }
    }

    private fun setupRoleFilterChips() {
        val roles = mutableListOf(
            null to "All",
            AccountRole.ORGANIZER to "Organizer",
            AccountRole.STAFF to "Staff",
            AccountRole.ATTENDEE to "Attendee"
        )
        if (isSuperAdmin()) {
            roles.add(1, AccountRole.ADMIN to "Admin")
        }

        val chipBg = ContextCompat.getColorStateList(this, R.color.chip_background_selector)
        val chipText = ContextCompat.getColorStateList(this, R.color.chip_text_selector)

        roles.forEach { (role, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = role == null
                setChipIconVisible(false)
                setCheckedIconVisible(false)
                setChipBackgroundColor(chipBg)
                setTextColor(chipText)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedRoleFilter = role
                        filterChipsLayout.check(id)
                        loadAccounts()
                    }
                }
                layoutParams = ChipGroup.LayoutParams(
                    ChipGroup.LayoutParams.WRAP_CONTENT,
                    ChipGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, (8 * resources.displayMetrics.density).toInt(), 0) }
            }
            filterChipsLayout.addView(chip)
        }
    }

    private fun bindNav() {
        configureAdminBottomNav(AdminBottomNavItem.ACCOUNTS)
    }

    private fun bindSearch() {
        searchInput.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty().trim()
            if (query == searchQuery) return@addTextChangedListener
            // Server-side search: debounce keystrokes, then restart from page 0.
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                searchQuery = query
                loadAccounts()
            }
        }
        recyclerAccounts.addNearEndListener { loadPage() }
    }

    /** Restarts paging from page 0 (role chip, search, refresh, or after a row action). */
    private fun loadAccounts() {
        retrySnackbar?.dismiss()
        paging.reset()
        adapter.submitItems(emptyList())
        progressLoading.visibility = View.VISIBLE
        recyclerAccounts.visibility = View.GONE
        textPlaceholder.visibility = View.GONE
        loadPage()
    }

    private fun loadPage() {
        val ticket = paging.begin() ?: return
        val role = selectedRoleFilter
        val query = searchQuery
        lifecycleScope.launch {
            when (val result = repository.loadUsersPage(role, query, ticket.page)) {
                is NetworkResult.Success -> {
                    if (paging.onSuccess(ticket, result.data)) {
                        val users = paging.items
                        progressLoading.visibility = View.GONE
                        recyclerAccounts.visibility = if (users.isEmpty()) View.GONE else View.VISIBLE
                        textPlaceholder.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
                        textPlaceholder.text = getString(
                            if (query.isNotEmpty()) R.string.admin_account_management_no_accounts_match_your_search
                            else R.string.admin_account_management_no_accounts_found_yet
                        )
                        adapter.submitItems(users)
                    }
                }
                is NetworkResult.Error -> {
                    if (paging.onFailure(ticket)) {
                        progressLoading.visibility = View.GONE
                        if (paging.isEmpty) {
                            recyclerAccounts.visibility = View.GONE
                            textPlaceholder.visibility = View.VISIBLE
                            textPlaceholder.text = getString(R.string.admin_account_management_account_management_is_currently_unav)
                        }
                        retrySnackbar?.dismiss()
                        retrySnackbar = Snackbar.make(recyclerAccounts, result.message, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.common_retry) {
                                paging.retry()
                                loadPage()
                            }
                        retrySnackbar?.show()
                    }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun onAccountActionClick(user: UserResponse, action: AccountAction) {
        when (action) {
            AccountAction.ENABLE -> {
                if (user.userId.toString() == currentUserId) {
                    Toast.makeText(this, this.getString(R.string.admin_account_management_cannot_enable_your_own_account), Toast.LENGTH_SHORT).show()
                    return
                }
                performEnable(user)
            }
            AccountAction.DISABLE -> {
                if (user.userId.toString() == currentUserId) {
                    Toast.makeText(this, this.getString(R.string.admin_account_management_cannot_disable_your_own_account), Toast.LENGTH_SHORT).show()
                    return
                }
                performDisable(user)
            }
            AccountAction.DELETE -> {
                if (user.userId.toString() == currentUserId) {
                    Toast.makeText(this, this.getString(R.string.admin_account_management_cannot_delete_your_own_account), Toast.LENGTH_SHORT).show()
                    return
                }
                showDeleteConfirmation(user)
            }
        }
    }

    private fun performEnable(user: UserResponse) {
        lifecycleScope.launch {
            when (val result = repository.enableUser(user.userId.toString())) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@AdminAccountManagementActivity, this@AdminAccountManagementActivity.getString(R.string.admin_account_management_account_enabled), Toast.LENGTH_SHORT).show()
                    loadAccounts()
                }
                is NetworkResult.Error -> {
                    Toast.makeText(this@AdminAccountManagementActivity, getString(R.string.common_failed_with_reason, result.message), Toast.LENGTH_LONG).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun performDisable(user: UserResponse) {
        lifecycleScope.launch {
            when (val result = repository.disableUser(user.userId.toString())) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@AdminAccountManagementActivity, this@AdminAccountManagementActivity.getString(R.string.admin_account_management_account_disabled), Toast.LENGTH_SHORT).show()
                    loadAccounts()
                }
                is NetworkResult.Error -> {
                    Toast.makeText(this@AdminAccountManagementActivity, getString(R.string.common_failed_with_reason, result.message), Toast.LENGTH_LONG).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun showDeleteConfirmation(user: UserResponse) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.admin_account_management_delete_account))
            .setMessage(getString(R.string.admin_account_confirm_delete, user.fullName))
            .setPositiveButton(getString(R.string.admin_account_management_delete)) { _, _ -> performDelete(user) }
            .setNegativeButton(getString(R.string.request_event_cancel), null)
            .show()
    }

    private fun performDelete(user: UserResponse) {
        lifecycleScope.launch {
            when (val result = repository.deleteUser(user.userId.toString())) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@AdminAccountManagementActivity, this@AdminAccountManagementActivity.getString(R.string.admin_account_management_account_deleted), Toast.LENGTH_SHORT).show()
                    loadAccounts()
                }
                is NetworkResult.Error -> {
                    Toast.makeText(this@AdminAccountManagementActivity, getString(R.string.common_failed_with_reason, result.message), Toast.LENGTH_LONG).show()
                    loadAccounts()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun isSuperAdmin(): Boolean {
        return RoleMapper.normalizeRole(sessionManager.getUserRole()) == AccountRole.SUPER_ADMIN.name
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
