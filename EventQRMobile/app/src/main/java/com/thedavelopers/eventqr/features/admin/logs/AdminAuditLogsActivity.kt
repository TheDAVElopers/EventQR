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

    private var allLogs: List<AuditLogResponse> = emptyList()
    private val selectedFilter = MutableStateFlow(AuditFilter.ALL)

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
    }

    private fun bindFilterChips() {
        val composeView = findViewById<ComposeView>(R.id.composeAuditFilters)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            EventQrTheme {
                val filter = selectedFilter.collectAsStateWithLifecycle().value
                FilterChipRow(
                    items = AuditFilter.entries,
                    selectedItem = filter,
                    onItemSelected = { setFilter(it) },
                    labelProvider = { it.label },
                )
            }
        }
    }

    private fun bindNav() {
        configureAdminBottomNav(AdminBottomNavItem.LOGS)
    }

    private fun loadLogs() {
        if (!swipeRefresh.isRefreshing) {
            progressLoading.visibility = View.VISIBLE
        }
        recyclerLogs.visibility = View.GONE
        textPlaceholder.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = repository.loadAuditLogs()) {
                is NetworkResult.Success -> {
                    swipeRefresh.isRefreshing = false
                    allLogs = result.data.sortedByDescending { it.timestamp }
                    progressLoading.visibility = View.GONE
                    applyFilter()
                }
                is NetworkResult.Error -> {
                    swipeRefresh.isRefreshing = false
                    allLogs = emptyList()
                    progressLoading.visibility = View.GONE
                    recyclerLogs.visibility = View.GONE
                    textPlaceholder.visibility = View.VISIBLE
                    textPlaceholder.text = getString(R.string.admin_audit_logs_unable_to_load_audit_logs_pull_down)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun setFilter(filter: AuditFilter) {
        selectedFilter.value = filter
        applyFilter()
    }

    private fun applyFilter() {
        val activeFilter = selectedFilter.value

        if (allLogs.isEmpty()) {
            recyclerLogs.visibility = View.GONE
            textPlaceholder.visibility = View.VISIBLE
            textPlaceholder.text = getString(R.string.admin_audit_logs_no_audit_logs_yet)
            adapter.submitItems(emptyList())
            return
        }

        val filtered = allLogs.filter { log ->
            val actionText = log.action.lowercase()
            val detailsText = log.details.orEmpty().lowercase()
            when (activeFilter) {
                AuditFilter.ALL -> true
                AuditFilter.APPROVAL -> actionText.contains("approve") || actionText.contains("reject") || actionText.contains("request") || detailsText.contains("approve") || detailsText.contains("reject")
                AuditFilter.ACCOUNT -> actionText.contains("account") || actionText.contains("user") || actionText.contains("role") || actionText.contains("suspend") || detailsText.contains("account") || detailsText.contains("role")
                AuditFilter.SECURITY -> actionText.contains("security") || actionText.contains("suspend") || actionText.contains("permission") || detailsText.contains("security") || detailsText.contains("suspend")
                AuditFilter.NOTIFICATION -> actionText.contains("notification") || detailsText.contains("notification")
            }
        }

        adapter.submitItems(filtered)
        recyclerLogs.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        textPlaceholder.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        textPlaceholder.text = if (filtered.isEmpty()) "No audit logs for this filter." else ""
    }

    private enum class AuditFilter(val label: String) {
        ALL("All"),
        APPROVAL("Approval"),
        ACCOUNT("Account"),
        SECURITY("Security"),
        NOTIFICATION("Notification"),
    }
}
