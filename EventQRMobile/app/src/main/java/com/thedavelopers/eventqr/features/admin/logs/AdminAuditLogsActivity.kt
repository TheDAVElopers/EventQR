package com.thedavelopers.eventqr.features.admin.logs

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.admin.AdminEventApprovalBackendActivity
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity
import com.thedavelopers.eventqr.features.admin.users.AdminAccountManagementActivity
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import kotlinx.coroutines.launch

class AdminAuditLogsActivity : AppCompatActivity() {
    private lateinit var repository: AdminRepository
    private lateinit var adapter: AdminAuditLogAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressLoading: ProgressBar
    private lateinit var textPlaceholder: TextView
    private lateinit var recyclerLogs: RecyclerView

    private lateinit var chipAll: TextView
    private lateinit var chipApproval: TextView
    private lateinit var chipAccount: TextView
    private lateinit var chipSecurity: TextView
    private lateinit var chipNotification: TextView

    private var allLogs: List<AuditLogResponse> = emptyList()
    private var selectedFilter: AuditFilter = AuditFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_audit_logs)

        repository = AdminRepository(this)
        adapter = AdminAuditLogAdapter()
        bindViews()
        bindFilterClicks()
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
        swipeRefresh.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefresh.setOnRefreshListener { loadLogs() }

        chipAll = findViewById(R.id.chipAuditAll)
        chipApproval = findViewById(R.id.chipAuditApproval)
        chipAccount = findViewById(R.id.chipAuditAccount)
        chipSecurity = findViewById(R.id.chipAuditSecurity)
        chipNotification = findViewById(R.id.chipAuditNotification)
        updateChipStyles()
    }

    private fun bindFilterClicks() {
        chipAll.setOnClickListener { setFilter(AuditFilter.ALL) }
        chipApproval.setOnClickListener { setFilter(AuditFilter.APPROVAL) }
        chipAccount.setOnClickListener { setFilter(AuditFilter.ACCOUNT) }
        chipSecurity.setOnClickListener { setFilter(AuditFilter.SECURITY) }
        chipNotification.setOnClickListener { setFilter(AuditFilter.NOTIFICATION) }
    }

    private fun bindNav() {
        findViewById<View>(R.id.navDashboard).setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navRequests).setOnClickListener {
            startActivity(Intent(this, AdminEventApprovalBackendActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navAccounts).setOnClickListener {
            startActivity(Intent(this, AdminAccountManagementActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navLogs).setOnClickListener {
            // current tab
        }
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
                    textPlaceholder.text = "Unable to load audit logs. Pull down to retry."
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun setFilter(filter: AuditFilter) {
        selectedFilter = filter
        updateChipStyles()
        applyFilter()
    }

    private fun updateChipStyles() {
        styleChip(chipAll, selectedFilter == AuditFilter.ALL)
        styleChip(chipApproval, selectedFilter == AuditFilter.APPROVAL)
        styleChip(chipAccount, selectedFilter == AuditFilter.ACCOUNT)
        styleChip(chipSecurity, selectedFilter == AuditFilter.SECURITY)
        styleChip(chipNotification, selectedFilter == AuditFilter.NOTIFICATION)
    }

    private fun styleChip(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_admin_request_filter_active
            else R.drawable.bg_admin_request_filter_inactive
        )
        chip.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        chip.elevation = if (selected) 2f else 1f
    }

    private fun applyFilter() {
        if (allLogs.isEmpty()) {
            recyclerLogs.visibility = View.GONE
            textPlaceholder.visibility = View.VISIBLE
            textPlaceholder.text = "No audit logs yet."
            adapter.submitItems(emptyList())
            return
        }

        val filtered = allLogs.filter { log ->
            val actionText = log.action.lowercase()
            val detailsText = log.details.orEmpty().lowercase()
            when (selectedFilter) {
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

    private enum class AuditFilter {
        ALL,
        APPROVAL,
        ACCOUNT,
        SECURITY,
        NOTIFICATION,
    }
}