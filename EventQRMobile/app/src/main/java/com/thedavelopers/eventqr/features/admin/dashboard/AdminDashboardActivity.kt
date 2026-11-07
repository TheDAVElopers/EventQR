package com.thedavelopers.eventqr.features.admin.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.admin.AdminBottomNavItem
import com.thedavelopers.eventqr.features.admin.AdminEventApprovalBackendActivity
import com.thedavelopers.eventqr.features.admin.AdminEventRequestDetailActivity
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.features.admin.configureAdminBottomNav
import com.thedavelopers.eventqr.features.admin.logs.AuditLogRowBinder
import com.thedavelopers.eventqr.features.admin.model.dto.AdminPendingPreview
import com.thedavelopers.eventqr.features.admin.model.dto.AdminStatsResponse
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
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
 * Admin dashboard (§6.5). A pass head with the three server counts, then the two blocks the bottom
 * nav cannot show: the waiting queue (requester, event, submitted) and read-only recent activity.
 *
 * It is not a menu. Requests, Accounts and Logs live in the bottom nav; the only link here is
 * `View all requests`, attached to a truncated preview.
 */
class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var repository: AdminRepository
    private lateinit var sessionManager: SessionManager

    private lateinit var passHead: View
    private lateinit var passHeadSkeleton: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var skeletonDashboard: View
    private lateinit var layoutDashboardError: View
    private lateinit var pendingRowsContainer: android.widget.LinearLayout
    private lateinit var textNoPending: TextView
    private lateinit var btnViewAllRequests: TextView
    private lateinit var recentActivityBlock: View
    private lateinit var auditRowsContainer: android.widget.LinearLayout

    private var isSwipeRefreshing = false

    /** Last successful payload; refresh failures keep it on screen. */
    private var stats: AdminStatsResponse? = null
    private var retrySnackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        findViewById<View>(R.id.passHeadSkeletonIdentity).applyEventQrTopInsetPadding()

        repository = AdminRepository(this)
        sessionManager = SessionManager(this)
        bindViews()
        bindPassHead()
        bindActions()
        setupSwipeRefresh()
        configureAdminBottomNav(AdminBottomNavItem.DASHBOARD)
        textNoPending.isClickable = false
        textNoPending.isFocusable = false
        showLoadingSkeleton()
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    override fun onDestroy() {
        retrySnackbar?.dismiss()
        super.onDestroy()
    }

    private fun bindViews() {
        passHead = findViewById(R.id.passHead)
        passHeadSkeleton = findViewById(R.id.passHeadSkeleton)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
        skeletonDashboard = findViewById(R.id.skeletonDashboard)
        layoutDashboardError = findViewById(R.id.layoutDashboardError)
        pendingRowsContainer = findViewById(R.id.pendingRowsContainer)
        textNoPending = findViewById(R.id.textNoPending)
        btnViewAllRequests = findViewById(R.id.btnViewAllRequests)
        recentActivityBlock = findViewById(R.id.recentActivityBlock)
        auditRowsContainer = findViewById(R.id.auditRowsContainer)
    }

    /**
     * Role tabs replace the old portal chip + bottom sheet: the current role is the selected tab,
     * and every other portal the account may open is one tap away.
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
        val title = if (normalizedRole == AccountRole.SUPER_ADMIN.name) {
            getString(R.string.ui_admin_role_super_admin)
        } else {
            getString(R.string.ui_admin_title)
        }

        PassHeadBinder.bind(
            passHead,
            PassHeadModel(
                title = title,
                tabs = labels,
                selectedTabIndex = selected,
                stats = listOf(
                    PassStat(getString(R.string.common_value_unavailable), getString(R.string.ui_admin_stat_pending)),
                    PassStat(getString(R.string.common_value_unavailable), getString(R.string.ui_admin_stat_active)),
                    PassStat(getString(R.string.common_value_unavailable), getString(R.string.ui_admin_stat_accounts)),
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

    private fun bindActions() {
        btnViewAllRequests.setOnClickListener { openRequests() }
        findViewById<View>(R.id.btnDashboardRetry).setOnClickListener { loadDashboard(showSkeleton = true) }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            loadDashboard()
        }
    }

    private fun loadDashboard(showSkeleton: Boolean = false) {
        if (showSkeleton && stats == null) showLoadingSkeleton()

        lifecycleScope.launch {
            try {
                val statsDeferred = async { repository.loadAdminStats() }
                val auditDeferred = async {
                    repository.loadAuditLogsPage(page = 0, size = RECENT_ACTIVITY_LIMIT)
                }
                applyStatsResult(statsDeferred.await())
                applyAuditResult(auditDeferred.await())
            } finally {
                stopSwipeRefresh()
            }
        }
    }

    /** Refresh vs first load is decided by [stats]; `internal` so the branch is testable. */
    internal fun applyStatsResult(result: NetworkResult<AdminStatsResponse>) {
        when (result) {
            is NetworkResult.Success -> {
                stats = result.data
                retrySnackbar?.dismiss()
                renderStats(result.data)
            }
            is NetworkResult.Error -> {
                // A refresh keeps the rows the user is reading and offers Retry; a first load
                // replaces the queue with the in-flow error block.
                if (stats != null) showRetrySnackbar() else renderStats(null)
            }
            NetworkResult.Loading -> Unit
        }
    }

    private fun applyAuditResult(result: NetworkResult<PageResponse<AuditLogResponse>>) {
        when (result) {
            is NetworkResult.Success -> renderRecentActivity(result.data.content)
            is NetworkResult.Error -> renderRecentActivity(null)
            NetworkResult.Loading -> Unit
        }
    }

    /** Pass head counts and the waiting queue. `null` = the stats call failed. */
    internal fun renderStats(stats: AdminStatsResponse?) {
        skeletonDashboard.visibility = View.GONE
        passHeadSkeleton.visibility = View.GONE
        passHead.visibility = View.VISIBLE
        bindStatValue(R.id.passHeadStat1Value, stats?.pendingEventRequests)
        bindStatValue(R.id.passHeadStat2Value, stats?.activeEvents)
        bindStatValue(R.id.passHeadStat3Value, stats?.totalAccounts)

        if (stats == null) {
            pendingRowsContainer.removeAllViews()
            pendingRowsContainer.visibility = View.GONE
            textNoPending.visibility = View.GONE
            btnViewAllRequests.visibility = View.GONE
            layoutDashboardError.visibility = View.VISIBLE
            return
        }

        layoutDashboardError.visibility = View.GONE
        renderQueue(stats)
    }

    private fun bindStatValue(viewId: Int, value: Long?) {
        passHead.findViewById<TextView>(viewId)?.text =
            value?.toString() ?: getString(R.string.common_value_unavailable)
    }

    private fun renderQueue(stats: AdminStatsResponse) {
        val preview = stats.pendingPreview.orEmpty().take(PREVIEW_LIMIT)
        pendingRowsContainer.removeAllViews()
        preview.forEach { pendingRowsContainer.addView(buildPendingRow(it)) }
        pendingRowsContainer.visibility = if (preview.isEmpty()) View.GONE else View.VISIBLE

        val pendingTotal = stats.pendingEventRequests
        // An empty preview is either a genuine zero or a payload that carried no rows. The copy
        // only claims zero when the server actually counted zero.
        textNoPending.setText(
            if (pendingTotal == 0L) {
                R.string.ui_admin_pending_empty
            } else {
                R.string.ui_admin_pending_not_listed
            },
        )
        textNoPending.visibility = if (preview.isEmpty()) View.VISIBLE else View.GONE
        btnViewAllRequests.visibility =
            if ((pendingTotal ?: 0L) > PREVIEW_LIMIT) View.VISIBLE else View.GONE
    }

    private fun buildPendingRow(preview: AdminPendingPreview): View {
        val row = layoutInflater.inflate(R.layout.list_row_icon, pendingRowsContainer, false)
        val eventName = preview.eventName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.common_untitled_event)
        val requestId = preview.id?.takeIf { it.isNotBlank() }

        ListRowBinder.bind(
            row = row,
            title = preview.requesterName?.takeIf { it.isNotBlank() } ?: eventName,
            meta = getString(
                R.string.ui_admin_pending_request_meta,
                eventName,
                DateFormatters.formatEventDate(preview.createdAt),
            ),
            trailing = ListRowBinder.Trailing.BADGE,
            trailingText = getString(R.string.ui_attendee_pending_review),
            badgeStyle = R.style.Widget_EventQR_Ui_StatusBadge_Warning,
            showDivider = true,
            onClick = requestId?.let { id -> { openRequestDetail(id) } },
        )
        row.findViewById<ImageView>(R.id.listRowIcon)?.setImageResource(AppIcons.Request)
        return row
    }

    /**
     * Read-only activity preview. `null` = the call failed. It is supplementary, so a failure has
     * no error state and no Retry: rows already on screen stay, otherwise the block is hidden.
     */
    internal fun renderRecentActivity(items: List<AuditLogResponse>?) {
        if (items == null) {
            // Supplementary block: a failure hides it. No error state, no Retry.
            recentActivityBlock.visibility = View.GONE
            return
        }

        auditRowsContainer.removeAllViews()
        items.take(RECENT_ACTIVITY_LIMIT).forEach { item ->
            val row = layoutInflater.inflate(R.layout.item_admin_audit_log, auditRowsContainer, false)
            AuditLogRowBinder.bind(row, item)
            auditRowsContainer.addView(row)
        }
        recentActivityBlock.visibility =
            if (auditRowsContainer.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun showLoadingSkeleton() {
        passHeadSkeleton.visibility = View.VISIBLE
        passHead.visibility = View.GONE
        skeletonDashboard.visibility = View.VISIBLE
        layoutDashboardError.visibility = View.GONE
        pendingRowsContainer.visibility = View.GONE
        textNoPending.visibility = View.GONE
        btnViewAllRequests.visibility = View.GONE
        recentActivityBlock.visibility = View.GONE
    }

    private fun showRetrySnackbar() {
        retrySnackbar?.dismiss()
        retrySnackbar = Snackbar.make(
            swipeRefreshLayout,
            getString(R.string.ui_admin_dashboard_error),
            Snackbar.LENGTH_INDEFINITE,
        ).setAction(R.string.common_retry) { loadDashboard() }
        retrySnackbar?.show()
    }

    private fun openRequestDetail(requestId: String) {
        startActivity(
            Intent(this, AdminEventRequestDetailActivity::class.java)
                .putExtra(AdminEventRequestDetailActivity.EXTRA_REQUEST_ID, requestId),
        )
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

    private companion object {
        const val PREVIEW_LIMIT = 3
        const val RECENT_ACTIVITY_LIMIT = 3
    }
}
