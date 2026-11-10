package com.thedavelopers.eventqr.features.admin.logs

import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.admin.AdminBottomNavItem
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.features.admin.AdminRoleLabels
import com.thedavelopers.eventqr.features.admin.AuditCategory
import com.thedavelopers.eventqr.features.admin.actionPrefix
import com.thedavelopers.eventqr.features.admin.auditDayGroups
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.core.util.PagedAccumulator
import com.thedavelopers.eventqr.core.util.addNearEndListener
import com.thedavelopers.eventqr.features.admin.configureAdminBottomNav
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import com.thedavelopers.eventqr.ui.components.FilterChipRow
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class AdminAuditLogsActivity : AppCompatActivity() {
    /** Tests set this before the first load to replace the network repository. */
    @VisibleForTesting
    internal var repositoryOverride: AdminRepository? = null

    private val repository: AdminRepository by lazy {
        repositoryOverride ?: AdminRepository(this)
    }

    private lateinit var adapter: AdminAuditLogAdapter
    private lateinit var dayHeaders: AuditDayHeaderDecoration
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var textPlaceholder: EventQrEmptyState
    private lateinit var recyclerLogs: RecyclerView

    private val paging = PagedAccumulator<AuditLogResponse, java.util.UUID> { it.auditLogId }
    private val selectedFilter = MutableStateFlow(AuditCategory.ALL)

    /** Identifies the current load: a new id tells the adapter this is a replacement, not an append. */
    private var loadId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_audit_logs)
        applyEventQrSystemBarAppearance(lightStatusBars = false)

        val normalizedRole = RoleMapper.normalizeRole(SessionManager(this).getUserRole())
        if (normalizedRole != AccountRole.ADMIN.name && normalizedRole != AccountRole.SUPER_ADMIN.name) {
            Toast.makeText(this, this.getString(R.string.admin_audit_logs_access_denied_admin_only), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        adapter = AdminAuditLogAdapter()
        bindHeader(normalizedRole)
        bindViews()
        bindFilterChips()
        bindNav()
        loadLogs()
    }

    override fun onResume() {
        super.onResume()
        // The token refresher persists a role change before it announces it, so re-reading the cached
        // role keeps a screen left open current without another network call.
        bindRoleEyebrow(SessionManager(this).getUserRole())
    }

    /** Paints the eyebrow from a role; the header's title and insets are bound once in [bindHeader]. */
    private fun bindRoleEyebrow(role: String?) {
        findViewById<TextView>(R.id.passHeadEyebrow)?.text =
            getString(AdminRoleLabels.eyebrowRes(role))
    }

    private fun bindHeader(role: String?) {
        val head = findViewById<View>(R.id.passHead)
        head.findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        PassHeadBinder.bindCompact(
            root = head,
            title = getString(R.string.ui_admin_logs_title),
            eyebrow = getString(AdminRoleLabels.eyebrowRes(role)),
        )
    }

    private fun bindViews() {
        swipeRefresh = findViewById(R.id.swipeRefreshAuditLogs)
        skeletonLoading = findViewById(R.id.skeletonAuditLoading)
        textPlaceholder = findViewById(R.id.textAuditPlaceholder)
        recyclerLogs = findViewById(R.id.recyclerAuditLogs)
        // Added once and owned by the decoration itself: a config change rebuilds this view tree,
        // while a refresh or filter change only replaces the header set, so headers are never
        // stacked twice and no caller has to remember to invalidate.
        dayHeaders = AuditDayHeaderDecoration(this)
        dayHeaders.attachTo(recyclerLogs)
        recyclerLogs.layoutManager = LinearLayoutManager(this)
        recyclerLogs.adapter = adapter
        swipeRefresh.setOnRefreshListener { loadLogs(firstLoad = false) }
        recyclerLogs.addNearEndListener { loadPage() }
    }

    private fun bindFilterChips() {
        val composeView = findViewById<ComposeView>(R.id.composeAuditFilters)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            EventQrTheme {
                val filter = selectedFilter.collectAsStateWithLifecycle().value
                FilterChipRow(
                    items = AuditCategory.entries,
                    selectedItem = filter,
                    onItemSelected = { setFilter(it) },
                    labelProvider = { getString(it.labelRes) },
                )
            }
        }
    }

    private fun bindNav() {
        configureAdminBottomNav(AdminBottomNavItem.LOGS)
    }

    private var retrySnackbar: Snackbar? = null

    override fun onDestroy() {
        retrySnackbar?.dismiss()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        // A screen left open past midnight must not keep yesterday's rows under a "TODAY" header.
        // Idempotent submit: nothing is redrawn unless a header actually moved.
        if (this::dayHeaders.isInitialized) dayHeaders.submit(auditDayGroups(paging.items), auditToday())
    }

    /**
     * Restarts paging at page 0. A first load (screen open, filter change, retry) renders skeleton
     * rows; a pull to refresh keeps the rows on screen and uses the swipe indicator instead.
     */
    private fun loadLogs(firstLoad: Boolean = true) {
        retrySnackbar?.dismiss()
        loadId++
        paging.reset()
        if (firstLoad) {
            adapter.submitItems(emptyList(), loadId)
            dayHeaders.submit(emptyList(), auditToday())
            recyclerLogs.visibility = View.GONE
            textPlaceholder.hide()
            skeletonLoading.visibility = View.VISIBLE
        }
        loadPage()
    }

    private fun loadPage() {
        val ticket = paging.begin() ?: return
        lifecycleScope.launch {
            when (val result = repository.loadAuditLogsPage(ticket.page, actionPrefix = selectedFilter.value.actionPrefix())) {
                is NetworkResult.Success -> {
                    if (paging.onSuccess(ticket, result.data)) {
                        swipeRefresh.isRefreshing = false
                        skeletonLoading.visibility = View.GONE
                        applyRows()
                    }
                }
                is NetworkResult.Error -> {
                    if (paging.onFailure(ticket)) {
                        swipeRefresh.isRefreshing = false
                        skeletonLoading.visibility = View.GONE
                        // The two failure paths are exclusive and decided by what is on screen, not
                        // by the accumulator: a pull to refresh resets paging before the request, so
                        // `paging.isEmpty` would hide rows that are still visible.
                        if (adapter.itemCount == 0) showLoadFailure(result.serverMessage) else showRetrySnackbar(result.serverMessage)
                    }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    /** A category change restarts paging at page 0; the server filters by action prefix. */
    private fun setFilter(filter: AuditCategory) {
        if (selectedFilter.value == filter) return
        selectedFilter.value = filter
        loadLogs()
    }

    private fun applyRows() {
        val rows = paging.items
        dayHeaders.submit(auditDayGroups(rows), auditToday())
        adapter.submitItems(rows, loadId)
        if (rows.isEmpty()) {
            recyclerLogs.visibility = View.GONE
            showEmpty()
        } else {
            recyclerLogs.visibility = View.VISIBLE
            textPlaceholder.hide()
        }
    }

    /** §5.10: left aligned, one control. Only the filter state has something to do. */
    private fun showEmpty() {
        val filter = selectedFilter.value
        if (filter == AuditCategory.ALL) {
            textPlaceholder.show(
                getString(R.string.ui_admin_logs_empty_title),
                getString(R.string.ui_admin_logs_empty_body),
            )
            textPlaceholder.setAction(null)
            return
        }
        textPlaceholder.show(
            getString(R.string.ui_audit_empty_filter_title, getString(filter.labelRes)),
            getString(R.string.ui_audit_empty_filter_body),
        )
        textPlaceholder.setAction(getString(R.string.ui_audit_empty_filter_action), primary = false) {
            setFilter(AuditCategory.ALL)
        }
    }

    private fun showLoadFailure(serverMessage: String?) {
        recyclerLogs.visibility = View.GONE
        textPlaceholder.show(
            getString(R.string.ui_audit_error_title),
            serverMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.ui_audit_error_body),
        )
        textPlaceholder.setAction(getString(R.string.common_retry), primary = false) { loadLogs() }
    }

    private fun showRetrySnackbar(serverMessage: String?) {
        retrySnackbar?.dismiss()
        // Same source as the failure state: the server message, else fixed copy - never the
        // transport's internal `message`.
        val message = serverMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.ui_audit_error_title)
        retrySnackbar = Snackbar.make(recyclerLogs, message, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.common_retry) {
                paging.retry()
                loadPage()
            }
        retrySnackbar?.show()
    }

    /** Today in the audit zone, so the header set and the rows agree near midnight. */
    private fun auditToday() = DateFormatters.auditDay(Instant.now())
}
