package com.thedavelopers.eventqr.features.admin.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.firstNameOnly
import com.thedavelopers.eventqr.features.admin.AdminBottomNavItem
import com.thedavelopers.eventqr.features.admin.AdminEventApprovalBackendActivity
import com.thedavelopers.eventqr.features.admin.AdminRepository
import com.thedavelopers.eventqr.features.admin.configureAdminBottomNav
import com.thedavelopers.eventqr.features.admin.logs.AdminAuditLogsActivity
import com.thedavelopers.eventqr.features.admin.notifications.AdminNotificationManagementActivity
import com.thedavelopers.eventqr.features.admin.users.AdminAccountManagementActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var repository: AdminRepository
    private lateinit var sessionManager: SessionManager

    private lateinit var textAdminName: TextView
    private lateinit var textPendingAlert: TextView
    private lateinit var cardPendingAlert: View
    private lateinit var textPendingRequests: TextView
    private lateinit var textTotalAccounts: TextView
    private lateinit var textActiveEvents: TextView
    private lateinit var textAuditLogs: TextView
    private lateinit var progressLoading: ProgressBar
    private lateinit var textLoadHint: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var isSwipeRefreshing = false
    private var hasLoadedSummary = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        repository = AdminRepository(this)
        sessionManager = SessionManager(this)
        bindViews()
        setupSwipeRefresh()
        bindActions()
        setupPortalSwitcher()
        textAdminName.text = sessionManager.getFullName().orEmpty().ifBlank { "Admin User" }.firstNameOnly()
        cardPendingAlert.visibility = View.GONE
        textPendingAlert.text = ""
        textPendingRequests.text = "0"
    }

    override fun onResume() {
        super.onResume()
        loadSummary()
    }

    private fun bindViews() {
        textAdminName = findViewById(R.id.textAdminName)
        textPendingAlert = findViewById(R.id.textPendingAlert)
        cardPendingAlert = findViewById(R.id.cardPendingAlert)
        textPendingRequests = findViewById(R.id.textPendingRequestsValue)
        textTotalAccounts = findViewById(R.id.textTotalAccountsValue)
        textActiveEvents = findViewById(R.id.textActiveEventsValue)
        textAuditLogs = findViewById(R.id.textAuditLogsValue)
        progressLoading = findViewById(R.id.progressDashboardLoading)
        textLoadHint = findViewById(R.id.textDashboardLoadHint)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            loadSummary()
        }
    }

    private fun bindActions() {
        findViewById<View>(R.id.cardAdminNotifications).setOnClickListener {
            startActivity(Intent(this, AdminNotificationManagementActivity::class.java))
        }
        cardPendingAlert.setOnClickListener { openRequests() }

        configureAdminBottomNav(AdminBottomNavItem.DASHBOARD)
    }

    private fun setupPortalSwitcher() {
        val normalizedRole = RoleMapper.normalizeRole(sessionManager.getUserRole())
        val allowedPortals = PortalSwitcher.portalsForRole(normalizedRole)
        val currentPortal =
            if (normalizedRole == AccountRole.SUPER_ADMIN.name) PortalSwitcher.PORTAL_SUPER_ADMIN
            else PortalSwitcher.PORTAL_ADMIN

        findViewById<TextView>(R.id.textAdminPortalTitle).text = currentPortal
        findViewById<TextView>(R.id.portalSwitcherChip).text =
            if (normalizedRole == AccountRole.SUPER_ADMIN.name) "Super Admin ▾" else "Admin ▾"

        val chip = findViewById<View>(R.id.portalSwitcherChip)
        val dot = findViewById<View>(R.id.textAdminPortalDot)
        chip.visibility = if (allowedPortals.size > 1) View.VISIBLE else View.GONE
        dot.visibility = if (allowedPortals.size > 1) View.VISIBLE else View.GONE
        chip.setOnClickListener(null)

        if (allowedPortals.size > 1) {
            chip.setOnClickListener {
                showPortalSwitcher(allowedPortals, currentPortal)
            }
        }
    }

    private fun showPortalSwitcher(portals: List<String>, currentPortal: String) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_portal_switcher, null)
        val container = view.findViewById<LinearLayout>(R.id.portalOptionsContainer)

        portals.forEach { portal ->
            val portalView = layoutInflater.inflate(R.layout.item_portal_option, container, false)
            portalView.findViewById<TextView>(R.id.txtPortalName).text = portal

            val icon = portalView.findViewById<ImageView>(R.id.imgPortalIcon)
            val subtitle = portalView.findViewById<TextView>(R.id.txtPortalSubtitle)
            icon.setImageResource(PortalSwitcher.iconRes(portal))
            subtitle.text = PortalSwitcher.subtitle(portal)

            if (portal == currentPortal) {
                portalView.findViewById<View>(R.id.currentPortalBadge).visibility = View.VISIBLE
            }

            portalView.setOnClickListener {
                dialog.dismiss()
                if (portal == PortalSwitcher.PORTAL_ATTENDEE) {
                    startActivity(Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java))
                    finish()
                }
            }
            container.addView(portalView)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun loadSummary() {
        cardPendingAlert.visibility = if (hasLoadedSummary && cardPendingAlert.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        if (!isSwipeRefreshing) {
            progressLoading.visibility = View.VISIBLE
            textLoadHint.visibility = View.VISIBLE
        } else {
            progressLoading.visibility = View.GONE
            textLoadHint.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                val requestsDeferred = async { repository.loadAllEventRequests() }
                val usersDeferred = async { repository.loadUsers() }
                val eventsDeferred = async { repository.loadEvents() }
                val auditLogsDeferred = async { repository.loadAuditLogs() }

                val requestsResult = requestsDeferred.await()
                val usersResult = usersDeferred.await()
                val eventsResult = eventsDeferred.await()
                val auditLogsResult = auditLogsDeferred.await()

                val pendingRequests = when (requestsResult) {
                    is NetworkResult.Success -> requestsResult.data.count { it.status == EventRequestStatus.PENDING }
                    else -> 0
                }
                val totalAccounts = when (usersResult) {
                    is NetworkResult.Success -> usersResult.data.size
                    else -> 0
                }
                val activeEvents = when (eventsResult) {
                    is NetworkResult.Success -> eventsResult.data.count {
                        it.status == EventStatus.ACTIVE || it.status == EventStatus.APPROVED
                    }
                    else -> 0
                }
                val auditLogs = when (auditLogsResult) {
                    is NetworkResult.Success -> auditLogsResult.data.size
                    else -> 0
                }

                textPendingRequests.text = pendingRequests.toString()
                textTotalAccounts.text = totalAccounts.toString()
                textActiveEvents.text = activeEvents.toString()
                textAuditLogs.text = formatCount(auditLogs)

                hasLoadedSummary = true
                if (pendingRequests > 0) {
                    cardPendingAlert.visibility = View.VISIBLE
                    textPendingAlert.text = if (pendingRequests == 1) {
                        "1 event request pending review"
                    } else {
                        "$pendingRequests event requests pending review"
                    }
                } else {
                    textPendingAlert.text = ""
                    cardPendingAlert.visibility = View.GONE
                }

                textLoadHint.text = when {
                    requestsResult is NetworkResult.Error -> "Unable to refresh pending requests right now."
                    usersResult is NetworkResult.Error ||
                        eventsResult is NetworkResult.Error ||
                        auditLogsResult is NetworkResult.Error ->
                        "Some dashboard stats are currently unavailable."
                    else -> ""
                }
                textLoadHint.visibility = if (textLoadHint.text.isNullOrBlank()) View.GONE else View.VISIBLE
                progressLoading.visibility = View.GONE
            } finally {
                stopSwipeRefresh()
            }
        }
    }

    private fun formatCount(value: Int): String {
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
        finish()
    }

    private fun stopSwipeRefresh() {
        if (isSwipeRefreshing) {
            swipeRefreshLayout.isRefreshing = false
            isSwipeRefreshing = false
        }
    }
}
