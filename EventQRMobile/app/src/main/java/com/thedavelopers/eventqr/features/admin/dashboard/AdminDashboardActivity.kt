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
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.admin.AdminEventApprovalBackendActivity
import com.thedavelopers.eventqr.features.admin.AdminRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        repository = AdminRepository(this)
        sessionManager = SessionManager(this)
        bindViews()
        setupSwipeRefresh()
        bindActions()
        setupPortalSwitcher()
        textAdminName.text = sessionManager.getFullName().orEmpty().ifBlank { "Admin User" }
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
        findViewById<View>(R.id.cardAdminEventRequests).setOnClickListener {
            openRequests()
        }
        findViewById<View>(R.id.cardAdminAccounts).setOnClickListener {
            startActivity(Intent(this, AdminAccountManagementActivity::class.java))
        }
        findViewById<View>(R.id.cardAdminAuditLogs).setOnClickListener {
            startActivity(Intent(this, AdminAuditLogsActivity::class.java))
        }
        findViewById<View>(R.id.cardAdminNotifications).setOnClickListener {
            startActivity(Intent(this, AdminNotificationManagementActivity::class.java))
        }
        cardPendingAlert.setOnClickListener { openRequests() }

        findViewById<View>(R.id.navDashboard).setOnClickListener {
            // current tab
        }
        findViewById<View>(R.id.navRequests).setOnClickListener {
            openRequests()
        }
        findViewById<View>(R.id.navAccounts).setOnClickListener {
            startActivity(Intent(this, AdminAccountManagementActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.navLogs).setOnClickListener {
            startActivity(Intent(this, AdminAuditLogsActivity::class.java))
            finish()
        }
    }

    private fun setupPortalSwitcher() {
        val role = RoleMapper.normalizeRole(sessionManager.getUserRole())
        if (role != AccountRole.ADMIN.name && role != AccountRole.SUPER_ADMIN.name) {
            findViewById<View>(R.id.portalSwitcherChip).visibility = View.GONE
            findViewById<View>(R.id.textAdminPortalDot).visibility = View.GONE
            return
        }

        findViewById<View>(R.id.portalSwitcherChip).setOnClickListener {
            val portals = listOf("Admin Portal", "Attendee Portal")
            showPortalSwitcher(portals)
        }
    }

    private fun showPortalSwitcher(portals: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_portal_switcher, null)
        val container = view.findViewById<LinearLayout>(R.id.portalOptionsContainer)

        portals.forEach { portal ->
            val portalView = layoutInflater.inflate(R.layout.item_portal_option, container, false)
            portalView.findViewById<TextView>(R.id.txtPortalName).text = portal

            val icon = portalView.findViewById<ImageView>(R.id.imgPortalIcon)
            val subtitle = portalView.findViewById<TextView>(R.id.txtPortalSubtitle)

            when (portal) {
                "Admin Portal" -> {
                    icon.setImageResource(R.drawable.ic_group)
                    subtitle.text = "Platform administration and oversight"
                    portalView.findViewById<View>(R.id.currentPortalBadge).visibility = View.VISIBLE
                }
                else -> {
                    icon.setImageResource(R.drawable.ic_nav_profile)
                    subtitle.text = "Events, rewards, and your profile"
                }
            }

            portalView.setOnClickListener {
                dialog.dismiss()
                if (portal == "Attendee Portal") {
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
                    // TODO: keep 0 fallback for MVP if accounts endpoint is unavailable.
                    else -> 0
                }
                val activeEvents = when (eventsResult) {
                    is NetworkResult.Success -> eventsResult.data.count {
                        it.status == EventStatus.ACTIVE || it.status == EventStatus.APPROVED
                    }
                    // TODO: keep 0 fallback for MVP if events endpoint is unavailable.
                    else -> 0
                }
                val auditLogs = when (auditLogsResult) {
                    is NetworkResult.Success -> auditLogsResult.data.size
                    // TODO: keep 0 fallback for MVP if audit endpoint is unavailable.
                    else -> 0
                }

                textPendingRequests.text = pendingRequests.toString()
                textTotalAccounts.text = totalAccounts.toString()
                textActiveEvents.text = activeEvents.toString()
                textAuditLogs.text = formatCount(auditLogs)

                if (pendingRequests > 0) {
                    cardPendingAlert.visibility = View.VISIBLE
                    textPendingAlert.text = if (pendingRequests == 1) {
                        "1 event request pending review"
                    } else {
                        "$pendingRequests event requests pending review"
                    }
                } else {
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
