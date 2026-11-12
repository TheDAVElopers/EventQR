package com.thedavelopers.eventqr.features.admin.users

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.AccountStatus
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
import com.thedavelopers.eventqr.ui.icons.FieldIcons
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
    private lateinit var skeletonAccounts: View
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
        FieldIcons.apply(searchInput)
        recyclerAccounts = findViewById(R.id.recyclerAdminAccounts)
        skeletonAccounts = findViewById(R.id.skeletonAccountsLoading)
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
        skeletonAccounts.visibility = View.VISIBLE
        recyclerAccounts.visibility = View.GONE
        textPlaceholder.hide()
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
                        skeletonAccounts.visibility = View.GONE
                        if (users.isEmpty()) {
                            recyclerAccounts.visibility = View.GONE
                            showEmptyState(query)
                        } else {
                            recyclerAccounts.visibility = View.VISIBLE
                            textPlaceholder.hide()
                        }
                        adapter.submitItems(users)
                    }
                }
                is NetworkResult.Error -> {
                    if (paging.onFailure(ticket)) {
                        skeletonAccounts.visibility = View.GONE
                        if (paging.isEmpty) {
                            showLoadFailure(result.serverMessage)
                        } else {
                            showRetrySnackbar(result.serverMessage)
                        }
                    }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    /** §5.10: one control. Only the search state has something to do. */
    private fun showEmptyState(query: String) {
        if (query.isNotEmpty()) {
            textPlaceholder.setIcon(AppIcons.Search)
            textPlaceholder.show(
                getString(R.string.ui_admin_accounts_empty_search_title),
                getString(R.string.ui_admin_accounts_empty_search_body),
            )
            textPlaceholder.setAction(getString(R.string.ui_admin_accounts_clear_search), primary = false) {
                searchJob?.cancel()
                searchQuery = ""
                searchInput.setText("")
                loadAccounts()
            }
            return
        }
        textPlaceholder.setIcon(AppIcons.People)
        textPlaceholder.show(
            getString(R.string.ui_admin_accounts_empty_title),
            getString(R.string.ui_admin_accounts_empty_body),
        )
        textPlaceholder.setAction(null)
    }

    private fun showLoadFailure(serverMessage: String?) {
        recyclerAccounts.visibility = View.GONE
        textPlaceholder.setIcon(AppIcons.Warning)
        textPlaceholder.show(
            getString(R.string.ui_admin_accounts_error_title),
            serverMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.ui_admin_error_body),
        )
        textPlaceholder.setAction(getString(R.string.common_retry), primary = false) { loadAccounts() }
    }

    private fun showRetrySnackbar(serverMessage: String?) {
        retrySnackbar?.dismiss()
        val message = serverMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.ui_admin_accounts_error_title)
        retrySnackbar = Snackbar.make(recyclerAccounts, message, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.common_retry) {
                paging.retry()
                loadPage()
            }
        retrySnackbar?.show()
    }

    private fun onAccountActionClick(user: UserResponse) {
        // Mirrors the adapter: an active account and a super admin are not deletable.
        if (user.status == AccountStatus.ACTIVE || user.role == AccountRole.SUPER_ADMIN) return
        if (user.userId.toString() == currentUserId) {
            Snackbar.make(recyclerAccounts, R.string.ui_admin_accounts_self_delete, Snackbar.LENGTH_SHORT).show()
            return
        }
        showDeleteConfirmation(user)
    }

    /** §5.12: `dialog_base` with the verb as the destructive commit. */
    private fun showDeleteConfirmation(user: UserResponse) {
        val view = layoutInflater.inflate(R.layout.dialog_base, null, false)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        view.findViewById<TextView>(R.id.dialogTitle).setText(R.string.ui_admin_accounts_delete_title)
        view.findViewById<TextView>(R.id.dialogBody).text =
            getString(R.string.ui_admin_accounts_delete_body, user.fullName)

        view.findViewById<Button>(R.id.dialogNegative).apply {
            setText(R.string.ui_admin_accounts_keep)
            setOnClickListener { dialog.dismiss() }
        }
        view.findViewById<Button>(R.id.dialogPositive).apply {
            setText(R.string.ui_admin_accounts_delete)
            setBackgroundResource(R.drawable.bg_ui_button_destructive_commit)
            setTextColor(ContextCompat.getColor(context, R.color.ui_on_primary))
            setOnClickListener {
                dialog.dismiss()
                performDelete(user)
            }
        }
        dialog.show()
    }

    private fun performDelete(user: UserResponse) {
        lifecycleScope.launch {
            when (val result = repository.deleteUser(user.userId.toString())) {
                is NetworkResult.Success -> {
                    Snackbar.make(recyclerAccounts, R.string.ui_admin_accounts_deleted, Snackbar.LENGTH_SHORT).show()
                    loadAccounts()
                }
                is NetworkResult.Error -> {
                    val message = result.serverMessage?.takeIf { it.isNotBlank() } ?: result.message
                    Snackbar.make(recyclerAccounts, message, Snackbar.LENGTH_LONG).show()
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
