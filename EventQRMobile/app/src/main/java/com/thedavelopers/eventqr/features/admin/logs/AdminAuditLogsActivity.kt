package com.thedavelopers.eventqr.features.admin.logs

import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.admin.AdminBottomNavItem
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.features.admin.AuditCategory
import com.thedavelopers.eventqr.features.admin.actionPrefix
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.core.util.PagedAccumulator
import com.thedavelopers.eventqr.core.util.addNearEndListener
import com.thedavelopers.eventqr.features.admin.configureAdminBottomNav
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import com.thedavelopers.eventqr.ui.components.FilterChipRow
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AdminAuditLogsActivity : AppCompatActivity() {
    private lateinit var repository: AdminRepository
    private lateinit var adapter: AdminAuditLogAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressLoading: ProgressBar
    private lateinit var textPlaceholder: EventQrEmptyState
    private lateinit var recyclerLogs: RecyclerView

    private val paging = PagedAccumulator<AuditLogResponse, java.util.UUID> { it.auditLogId }
    private val selectedFilter = MutableStateFlow(AuditCategory.ALL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_audit_logs)

        val normalizedRole = RoleMapper.normalizeRole(SessionManager(this).getUserRole())
        if (normalizedRole != AccountRole.ADMIN.name && normalizedRole != AccountRole.SUPER_ADMIN.name) {
            Toast.makeText(this, this.getString(R.string.admin_audit_logs_access_denied_admin_only), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        repository = AdminRepository(this)
        adapter = AdminAuditLogAdapter()
        bindViews()
        bindFilterChips()
        bindNav()
        loadLogs()
    }

    private fun bindViews() {
        swipeRefresh = findViewById(R.id.swipeRefreshAuditLogs)
        progressLoading = findViewById(R.id.progressAuditLoading)
        textPlaceholder = findViewById(R.id.textAuditPlaceholder)
        recyclerLogs = findViewById(R.id.recyclerAuditLogs)
        recyclerLogs.layoutManager = LinearLayoutManager(this)
        recyclerLogs.adapter = adapter
        swipeRefresh.setOnRefreshListener { loadLogs() }
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

    private fun loadLogs() {
        retrySnackbar?.dismiss()
        paging.reset()
        if (!swipeRefresh.isRefreshing) {
            progressLoading.visibility = View.VISIBLE
        }
        recyclerLogs.visibility = View.GONE
        textPlaceholder.visibility = View.GONE
        loadPage()
    }

    private fun loadPage() {
        val ticket = paging.begin() ?: return
        lifecycleScope.launch {
            when (val result = repository.loadAuditLogsPage(ticket.page, actionPrefix = selectedFilter.value.actionPrefix())) {
                is NetworkResult.Success -> {
                    if (paging.onSuccess(ticket, result.data)) {
                        swipeRefresh.isRefreshing = false
                        progressLoading.visibility = View.GONE
                        applyRows()
                    }
                }
                is NetworkResult.Error -> {
                    if (paging.onFailure(ticket)) {
                        swipeRefresh.isRefreshing = false
                        progressLoading.visibility = View.GONE
                        if (paging.isEmpty) {
                            recyclerLogs.visibility = View.GONE
                            textPlaceholder.visibility = View.VISIBLE
                            textPlaceholder.text = getString(R.string.admin_audit_logs_unable_to_load_audit_logs_pull_down)
                        }
                        retrySnackbar = Snackbar.make(recyclerLogs, result.message, Snackbar.LENGTH_INDEFINITE)
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

    /** A category change restarts paging at page 0; the server filters by action prefix. */
    private fun setFilter(filter: AuditCategory) {
        if (selectedFilter.value == filter) return
        selectedFilter.value = filter
        adapter.submitItems(emptyList())
        loadLogs()
    }

    private fun applyRows() {
        val rows = paging.items
        adapter.submitItems(rows)
        recyclerLogs.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        textPlaceholder.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        textPlaceholder.text = when {
            rows.isNotEmpty() -> ""
            selectedFilter.value == AuditCategory.ALL -> getString(R.string.admin_audit_logs_no_audit_logs_yet)
            else -> getString(R.string.admin_audit_logs_no_logs_for_filter)
        }
    }
}
