package com.thedavelopers.eventqr.features.admin.users

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.PagedAccumulator
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.addNearEndListener
import com.thedavelopers.eventqr.features.admin.AdminBottomNavItem
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.features.admin.AdminRoleLabels
import com.thedavelopers.eventqr.features.admin.configureAdminBottomNav
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import com.thedavelopers.eventqr.ui.components.CountLabel
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.FilterChipRow
import com.thedavelopers.eventqr.ui.components.ListRowBinder
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Account management (§6.5). Compact pass head, search field, the shared FilterChipRow for the
 * role filter, and hairline account rows. The Material ChipGroup, the per-status badge drawables
 * and the raised "create account" card are gone.
 */
class AdminAccountManagementActivity : AppCompatActivity() {
    /** Tests set this before the first load to replace the network repository. */
    @VisibleForTesting
    internal var repositoryOverride: AdminRepository? = null

    private val repository: AdminRepository by lazy {
        repositoryOverride ?: AdminRepository(this)
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: AdminAccountAdapter
    private lateinit var searchInput: EditText
    private lateinit var recyclerAccounts: RecyclerView
    private lateinit var progressLoading: ProgressBar
    private lateinit var textPlaceholder: EventQrEmptyState
    private lateinit var countView: TextView
    private lateinit var rowCreateAccount: View

    private val paging = PagedAccumulator<UserResponse, java.util.UUID> { it.userId }
    private var searchQuery: String = ""
    private var searchJob: Job? = null
    private var retrySnackbar: Snackbar? = null

    private val selectedRoleFilter = MutableStateFlow<AccountRole?>(null)
    private val currentUserId: String? by lazy { sessionManager.getUserId() }

    private val roleFilters = mutableListOf<AccountRole?>(null)

    override fun onDestroy() {
        retrySnackbar?.dismiss()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_account_management)
        applyEventQrSystemBarAppearance()

        sessionManager = SessionManager(this)
        adapter = AdminAccountAdapter(onActionClick = ::onAccountActionClick)
        bindViews()
        bindHeader()
        bindCreateRow()
        bindFilterChips()
        bindNav()
        bindSearch()
    }

    override fun onResume() {
        super.onResume()
        // The token refresher persists a role change before it announces it, so re-reading the cached
        // role keeps a screen left open current without another network call.
        bindRoleEyebrow(sessionManager.getUserRole())
        loadAccounts()
    }

    private fun bindViews() {
        searchInput = findViewById(R.id.inputAccountSearch)
        recyclerAccounts = findViewById(R.id.recyclerAdminAccounts)
        progressLoading = findViewById(R.id.progressAccountsLoading)
        textPlaceholder = findViewById(R.id.textAccountsPlaceholder)
        rowCreateAccount = findViewById(R.id.rowCreateAccount)
        recyclerAccounts.layoutManager = LinearLayoutManager(this)
        recyclerAccounts.adapter = adapter
    }

    private fun bindHeader() {
        val head = findViewById<View>(R.id.passHead)
        head.findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        PassHeadBinder.bindCompact(
            root = head,
            title = getString(R.string.ui_admin_accounts_title),
            eyebrow = getString(AdminRoleLabels.eyebrowRes(sessionManager.getUserRole())),
        )
        countView = head.findViewById<TextView>(R.id.passHeadTitleCount)
    }

    /** Paints the eyebrow from a role; the header's title and insets are bound once in [bindHeader]. */
    private fun bindRoleEyebrow(role: String?) {
        findViewById<TextView>(R.id.passHeadEyebrow)?.text =
            getString(AdminRoleLabels.eyebrowRes(role))
    }

    private fun bindCreateRow() {
        rowCreateAccount.visibility = if (isSuperAdmin()) View.VISIBLE else View.GONE
        ListRowBinder.bind(
            row = rowCreateAccount,
            title = getString(R.string.ui_admin_accounts_create),
            meta = getString(R.string.ui_admin_accounts_create_meta),
            trailing = ListRowBinder.Trailing.CHEVRON,
            showDivider = true,
            onClick = { startActivity(Intent(this, CreateAdminAccountActivity::class.java)) },
        )
        rowCreateAccount.findViewById<android.widget.ImageView>(R.id.listRowIcon)
            ?.setImageResource(AppIcons.Add)
    }

    private fun bindFilterChips() {
        roleFilters.clear()
        roleFilters.add(null)
        if (isSuperAdmin()) roleFilters.add(AccountRole.ADMIN)
        roleFilters.add(AccountRole.ORGANIZER)
        roleFilters.add(AccountRole.STAFF)
        roleFilters.add(AccountRole.ATTENDEE)

        val composeView = findViewById<ComposeView>(R.id.composeAccountFilters)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            EventQrTheme {
                val selected = selectedRoleFilter.collectAsStateWithLifecycle().value
                FilterChipRow(
                    items = roleFilters.toList(),
                    selectedItem = selected,
                    onItemSelected = { role ->
                        selectedRoleFilter.value = role
                        loadAccounts()
                    },
                    labelProvider = { role -> roleLabel(role) },
                )
            }
        }
    }

    private fun roleLabel(role: AccountRole?): String = when (role) {
        null -> getString(R.string.ui_admin_filter_all)
        AccountRole.ADMIN -> getString(R.string.ui_admin_role_admin)
        AccountRole.ORGANIZER -> getString(R.string.ui_admin_role_organizer)
        AccountRole.STAFF -> getString(R.string.ui_admin_role_staff)
        AccountRole.SUPER_ADMIN -> getString(R.string.ui_admin_role_super_admin)
        else -> getString(R.string.ui_admin_role_attendee)
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
        countView.visibility = View.GONE
        adapter.submitItems(emptyList())
        progressLoading.visibility = View.VISIBLE
        recyclerAccounts.visibility = View.GONE
        textPlaceholder.visibility = View.GONE
        loadPage()
    }

    private fun loadPage() {
        val ticket = paging.begin() ?: return
        val role = selectedRoleFilter.value
        val query = searchQuery
        lifecycleScope.launch {
            when (val result = repository.loadUsersPage(role, query, ticket.page)) {
                is NetworkResult.Success -> {
                    if (paging.onSuccess(ticket, result.data)) {
                        val users = paging.items
                        countView.text = CountLabel.format(paging.totalElements, getString(R.string.ui_admin_accounts_count))
                        countView.visibility = View.VISIBLE
                        progressLoading.visibility = View.GONE
                        recyclerAccounts.visibility = if (users.isEmpty()) View.GONE else View.VISIBLE
                        textPlaceholder.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
                        textPlaceholder.subtext = getString(
                            if (query.isNotEmpty()) R.string.ui_admin_accounts_empty_search_body
                            else R.string.ui_admin_accounts_empty_body
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
                            // A load failure replaces the copy; the empty-state body would contradict it.
                            textPlaceholder.text = getString(R.string.admin_account_management_account_management_is_currently_unav)
                            textPlaceholder.subtext = null
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
