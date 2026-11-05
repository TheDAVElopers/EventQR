package com.thedavelopers.eventqr.features.admin.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.admin.AdminBottomNavItem
import com.thedavelopers.eventqr.features.admin.AdminEventApprovalBackendActivity
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.features.admin.configureAdminBottomNav
import com.thedavelopers.eventqr.features.admin.logs.AdminAuditLogsActivity
import com.thedavelopers.eventqr.features.admin.model.dto.AdminStatsResponse
import com.thedavelopers.eventqr.features.admin.users.AdminAccountManagementActivity
import com.thedavelopers.eventqr.ui.components.ListRowBinder
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.PassHeadModel
import com.thedavelopers.eventqr.ui.components.PassStat
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Admin dashboard (§6.5). Pass head with the role tabs, the three primary numbers in the stub,
 * one actionable pending row in the content flow, metric rows for the secondary numbers, and the
 * management rows. The old gradient header, the 2x2 card grid and the in-header alert banner are
 * gone; the portal switcher moved into the pass-head tabs (§5.12).
 */
class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var repository: AdminRepository
    private lateinit var sessionManager: SessionManager

    private lateinit var passHead: View
    private lateinit var rowPendingAlert: View
    private lateinit var textPendingAlert: TextView
    private lateinit var textActiveEvents: TextView
    private lateinit var textTotalAccounts: TextView
    private lateinit var progressLoading: ProgressBar
    private lateinit var textLoadHint: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var isSwipeRefreshing = false
    private var hasLoadedSummary = false
    private var pendingRequests = 0
    private var totalAccounts = "--"
    private var auditCount = "--"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()

        repository = AdminRepository(this)
        sessionManager = SessionManager(this)
        bindViews()
        bindPassHead()
        bindRows()
        setupSwipeRefresh()
        configureAdminBottomNav(AdminBottomNavItem.DASHBOARD)
        rowPendingAlert.visibility = View.GONE
        renderStats(null)
    }

    override fun onResume() {
        super.onResume()
        loadSummary()
    }

    private fun bindViews() {
        passHead = findViewById(R.id.passHead)
        rowPendingAlert = findViewById(R.id.rowPendingAlert)
        textPendingAlert = findViewById(R.id.textPendingAlert)
        textActiveEvents = findViewById<View>(R.id.metricActiveEvents).findViewById(R.id.metricValue)
        textTotalAccounts = findViewById<View>(R.id.metricTotalAccounts).findViewById(R.id.metricValue)
        progressLoading = findViewById(R.id.progressDashboardLoading)
        textLoadHint = findViewById(R.id.textDashboardLoadHint)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)

        findViewById<View>(R.id.metricActiveEvents).findViewById<TextView>(R.id.metricLabel)
            .setText(R.string.ui_admin_active_events)
        findViewById<View>(R.id.metricTotalAccounts).findViewById<TextView>(R.id.metricLabel)
            .setText(R.string.ui_admin_total_accounts)
    }

    /**
     * Role tabs replace the old portal chip + bottom sheet: the current portal is the selected
     * tab, and every other portal the account may open is one tap away.
     */
    private fun bindPassHead() {
        val normalizedRole = RoleMapper.normalizeRole(sessionManager.getUserRole())
        val portals = PortalSwitcher.portalsForRole(normalizedRole)
        val currentPortal = if (normalizedRole == AccountRole.SUPER_ADMIN.name) {
            PortalSwitcher.PORTAL_SUPER_ADMIN
        } else {
            PortalSwitcher.PORTAL_ADMIN
        }
        val labels = portals.map { portal ->
            when (portal) {
                PortalSwitcher.PORTAL_ATTENDEE -> getString(R.string.ui_admin_role_attendee)
                PortalSwitcher.PORTAL_STAFF -> getString(R.string.ui_admin_role_staff)
                PortalSwitcher.PORTAL_ORGANIZER -> getString(R.string.ui_admin_role_organizer)
                PortalSwitcher.PORTAL_SUPER_ADMIN -> getString(R.string.ui_admin_role_super_admin)
                else -> getString(R.string.ui_admin_tab)
            }
        }
        val selected = portals.indexOf(currentPortal).coerceAtLeast(0)

        PassHeadBinder.bind(
            passHead,
            PassHeadModel(
                title = getString(R.string.ui_admin_title),
                tabs = labels,
                selectedTabIndex = selected,
                stats = listOf(
                    PassStat(getString(R.string.common_value_unavailable), getString(R.string.ui_admin_stat_pending)),
                    PassStat(getString(R.string.common_value_unavailable), getString(R.string.ui_admin_stat_accounts)),
                    PassStat(getString(R.string.common_value_unavailable), getString(R.string.ui_admin_stat_audit)),
                ),
                onTabSelected = { index ->
                    val target = portals.getOrNull(index)
                    if (target != null && target != currentPortal) {
                        openPortal(target)
                    }
                },
            ),
        )
    }

    private fun openPortal(portal: String) {
        when (portal) {
            PortalSwitcher.PORTAL_ATTENDEE ->
                startActivity(Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java))
            PortalSwitcher.PORTAL_STAFF ->
                startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java))
            PortalSwitcher.PORTAL_ORGANIZER ->
                startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java))
            else -> return
        }
        finish()
    }

    private fun bindRows() {
        rowPendingAlert.setOnClickListener { openRequests() }
        bindRow(R.id.rowEventRequests, AppIcons.Request, R.string.ui_admin_row_event_requests, R.string.ui_admin_row_event_requests_meta) { openRequests() }
        bindRow(R.id.rowAccountManagement, AppIcons.People, R.string.ui_admin_row_accounts, R.string.ui_admin_row_accounts_meta) {
            startActivity(Intent(this, AdminAccountManagementActivity::class.java))
        }
        bindRow(R.id.rowAuditLogs, AppIcons.Logs, R.string.ui_admin_row_audit_logs, R.string.ui_admin_row_audit_logs_meta) {
            startActivity(Intent(this, AdminAuditLogsActivity::class.java))
        }
    }

    private fun bindRow(
        rowId: Int,
        iconRes: Int,
        titleRes: Int,
        metaRes: Int,
        onClick: () -> Unit,
    ) {
        val row = findViewById<View>(rowId)
        ListRowBinder.bind(
            row = row,
            title = getString(titleRes),
            meta = getString(metaRes),
            trailing = ListRowBinder.Trailing.CHEVRON,
            showDivider = true,
            onClick = onClick,
        )
        row.findViewById<android.widget.ImageView>(R.id.listRowIcon)?.setImageResource(iconRes)
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            loadSummary()
        }
    }

    private fun loadSummary() {
        rowPendingAlert.visibility = if (hasLoadedSummary && rowPendingAlert.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        if (!isSwipeRefreshing) {
            progressLoading.visibility = View.VISIBLE
        } else {
            progressLoading.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                val requestsDeferred = async { repository.loadAllEventRequests() }
                val statsDeferred = async { repository.loadAdminStats() }

                val requestsResult = requestsDeferred.await()
                val statsResult = statsDeferred.await()

                pendingRequests = when (requestsResult) {
                    is NetworkResult.Success -> requestsResult.data.count { it.status == EventRequestStatus.PENDING }
                    else -> 0
                }
                val stats = (statsResult as? NetworkResult.Success)?.data

                renderStats(stats)
                renderPendingAlert(requestsResult is NetworkResult.Success)

                hasLoadedSummary = true
                textLoadHint.text = when {
                    requestsResult is NetworkResult.Error -> getString(R.string.admin_dashboard_pending_unavailable)
                    stats == null -> getString(R.string.admin_dashboard_stats_unavailable)
                    else -> ""
                }
                textLoadHint.visibility = if (textLoadHint.text.isNullOrBlank()) View.GONE else View.VISIBLE
                progressLoading.visibility = View.GONE
            } finally {
                stopSwipeRefresh()
            }
        }
    }

    private fun renderPendingAlert(loaded: Boolean) {
        passHead.findViewById<TextView>(R.id.passHeadStat1Value)?.text =
            if (loaded) pendingRequests.toString() else getString(R.string.common_value_unavailable)
        if (loaded && pendingRequests > 0) {
            textPendingAlert.text = resources.getQuantityString(
                R.plurals.ui_admin_pending_alert,
                pendingRequests,
                pendingRequests,
            )
            rowPendingAlert.visibility = View.VISIBLE
        } else {
            textPendingAlert.text = ""
            rowPendingAlert.visibility = View.GONE
        }
    }

    internal fun renderStats(stats: AdminStatsResponse?) {
        totalAccounts = stats?.let { formatCount(it.totalAccounts) } ?: getString(R.string.common_value_unavailable)
        auditCount = stats?.let { formatCount(it.auditLogCount) } ?: getString(R.string.common_value_unavailable)
        textTotalAccounts.text = totalAccounts
        textActiveEvents.text = stats?.let { formatCount(it.activeEvents) } ?: getString(R.string.common_value_unavailable)

        val stub = passHead.findViewById<LinearLayout>(R.id.passHeadStub)
        passHead.findViewById<TextView>(R.id.passHeadStat2Value)?.text = totalAccounts
        passHead.findViewById<TextView>(R.id.passHeadStat3Value)?.text = auditCount
        stub?.visibility = View.VISIBLE
    }

    private fun formatCount(value: Long): String {
        if (value < 1000) {
            return value.toString()
        }
        val compact = value / 1000f
        return if (compact >= 10f || compact % 1f == 0f) {
            "${compact.toInt()}k"
        } else {
            "${"%.1f".format(compact)}k"
        }
    }

    private fun openRequests() {
        startActivity(Intent(this, AdminEventApprovalBackendActivity::class.java))
    }

    private fun stopSwipeRefresh() {
        if (isSwipeRefreshing) {
            swipeRefreshLayout.isRefreshing = false
            isSwipeRefreshing = false
        }
    }
}
