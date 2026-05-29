package com.thedavelopers.eventqr.features.organizer.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDashboardDto
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class OrganizerDashboardActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var isSwipeRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_dashboard)
        repository = OrganizerRepository(this)
        sessionManager = SessionManager(this)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
        swipeRefreshLayout.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            loadDashboard()
        }
        setupNavigation()
        loadDashboard()
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.navDashboard).setOnClickListener {
            // Stay here
        }
        findViewById<View>(R.id.navEvents).setOnClickListener {
            openOrganizerPage(ManageEventsActivity::class.java)
        }
        findViewById<View>(R.id.navAttendees).setOnClickListener {
            openOrganizerPlaceholder(
                title = "Attendees",
                message = "Attendee management details will be available in a follow-up release.",
                selectedNav = NAV_ATTENDEES,
            )
        }
        findViewById<View>(R.id.navReports).setOnClickListener {
            openOrganizerPlaceholder(
                title = "Reports",
                message = "Reports and analytics will be available in a follow-up release.",
                selectedNav = NAV_REPORTS,
            )
        }

        findViewById<View>(R.id.btnManageMyEvents).setOnClickListener {
            openOrganizerPage(ManageEventsActivity::class.java)
        }
        findViewById<View>(R.id.btnManageAttendees).setOnClickListener {
            openOrganizerPlaceholder(
                title = "Attendees",
                message = "Attendee management is currently a placeholder from the dashboard.",
            )
        }
        findViewById<View>(R.id.btnManageReports).setOnClickListener {
            openOrganizerPlaceholder("Overall Reports", "Full dashboard analytics coming soon.")
        }
        findViewById<View>(R.id.btnManageRewards).setOnClickListener {
            openOrganizerPlaceholder(
                title = "Rewards",
                message = "Rewards management is currently a placeholder from the dashboard.",
            )
        }
        findViewById<View>(R.id.btnSeeAllEvents).setOnClickListener {
            openOrganizerPage(ManageEventsActivity::class.java)
        }
        findViewById<View>(R.id.btnDashboardRetry).setOnClickListener {
            loadDashboard()
        }

        setupPortalSwitcher()
    }

    private fun setupPortalSwitcher() {
        val role = sessionManager.getUserRole() ?: return
        val normalizedRole = RoleMapper.normalizeRole(role)
        val allowedPortals = mutableListOf<String>()
        allowedPortals.add("Attendee Portal")

        if (normalizedRole == AccountRole.STAFF.name || normalizedRole == AccountRole.ADMIN.name || normalizedRole == AccountRole.SUPER_ADMIN.name) {
            allowedPortals.add("Staff Portal")
        }
        if (normalizedRole == AccountRole.ORGANIZER.name || normalizedRole == AccountRole.ADMIN.name || normalizedRole == AccountRole.SUPER_ADMIN.name) {
            allowedPortals.add("Organizer Portal")
        }
        if (normalizedRole == AccountRole.ADMIN.name || normalizedRole == AccountRole.SUPER_ADMIN.name) {
            allowedPortals.add("Admin Portal")
        }

        if (allowedPortals.size > 1) {
            val chip = findViewById<View>(R.id.portalSwitcherChip)
            chip.visibility = View.VISIBLE
            findViewById<View>(R.id.txtHeaderSubtitleDot).visibility = View.VISIBLE
            chip.setOnClickListener {
                showPortalSwitcher(allowedPortals)
            }
        }
    }

    private fun showPortalSwitcher(portals: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_portal_switcher, null)
        
        val container = view.findViewById<LinearLayout>(R.id.portalOptionsContainer)
        portals.forEach { portal ->
            val portalView = layoutInflater.inflate(R.layout.item_portal_option, container, false)
            portalView.findViewById<TextView>(R.id.txtPortalName).text = portal
            
            val icon = portalView.findViewById<android.widget.ImageView>(R.id.imgPortalIcon)
            val subtitle = portalView.findViewById<TextView>(R.id.txtPortalSubtitle)
            
            when(portal) {
                "Attendee Portal" -> {
                    icon.setImageResource(R.drawable.ic_nav_profile)
                    subtitle.text = "Events, rewards, and your profile"
                }
                "Staff Portal" -> {
                    icon.setImageResource(R.drawable.ic_qr_scan)
                    subtitle.text = "Scan QR codes and manage entries"
                }
                "Organizer Portal" -> {
                    icon.setImageResource(R.drawable.ic_nav_calendar)
                    subtitle.text = "Manage your events and attendees"
                }
                "Admin Portal" -> {
                    icon.setImageResource(R.drawable.ic_group)
                    subtitle.text = "Platform administration and oversight"
                }
            }

            if (portal == "Organizer Portal") {
                portalView.findViewById<View>(R.id.currentPortalBadge).visibility = View.VISIBLE
            }

            portalView.setOnClickListener {
                dialog.dismiss()
                switchToPortal(portal)
            }
            container.addView(portalView)
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun switchToPortal(portal: String) {
        when(portal) {
            "Attendee Portal" -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java))
                finish()
            }
            "Staff Portal" -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java))
                finish()
            }
            "Organizer Portal" -> {
                // Already here
            }
            "Admin Portal" -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java))
                finish()
            }
        }
    }

    private fun loadDashboard() {
        if (!isSwipeRefreshing) {
            findViewById<ProgressBar>(R.id.progressDashboardLoading).visibility = View.VISIBLE
        } else {
            findViewById<ProgressBar>(R.id.progressDashboardLoading).visibility = View.GONE
        }
        findViewById<View>(R.id.layoutDashboardError).visibility = View.GONE
        MainScope().launch {
            try {
                val dashboard = repository.loadDashboardForMvp()
                val load = repository.loadEventsForMvp()
                renderDashboard(load, dashboard)
            } finally {
                stopSwipeRefresh()
            }
        }
    }

    private fun renderDashboard(
        load: OrganizerMvpLoad<List<OrganizerMvpEvent>>,
        dashboard: OrganizerMvpLoad<OrganizerDashboardDto?>? = null,
    ) {
        findViewById<ProgressBar>(R.id.progressDashboardLoading).visibility = View.GONE
        val dashboardData = dashboard?.data
        val name = dashboardData?.organizerName.orEmpty().ifBlank { sessionManager.getFullName().orEmpty().ifBlank { "Organizer" } }
        val organization = dashboardData?.organization.orEmpty().ifBlank { "Organization not set" }

        findViewById<TextView>(R.id.txtHeaderTitle).text = "Organizer Portal"
        findViewById<TextView>(R.id.txtHeaderSubtitle).text = "$name • $organization"

        val events = load.data.approvedOnly()
        val selected = repository.resolveSelectedEvent(events, selectedEventId())
        val totalAttendees = dashboardData?.totalAttendees ?: events.sumOf { it.registeredCount }
        val totalTransactions = dashboardData?.totalTransactions ?: events.sumOf { it.totalTransactions }
        val totalRewards = events.sumOf { it.rewardRedemptions }
        val totalEvents = dashboardData?.totalEvents ?: events.size

        findViewById<TextView>(R.id.txtStatTotalEvents).text = formatCount(totalEvents)
        findViewById<TextView>(R.id.txtStatTotalAttendees).text = formatCount(totalAttendees)
        findViewById<TextView>(R.id.txtStatScansToday).text = formatCount(totalTransactions)
        findViewById<TextView>(R.id.txtStatRewardsGiven).text = formatCount(totalRewards)

        val activeEventsContainer = findViewById<LinearLayout>(R.id.activeEventsContainer)
        val emptyEvents = findViewById<TextView>(R.id.txtActiveEventsEmpty)
        activeEventsContainer.removeAllViews()

        val hasError = load.source == OrganizerMvpDataSource.ERROR
        findViewById<View>(R.id.layoutDashboardError).visibility = if (hasError && events.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txtDashboardError).text = load.message ?: "Organizer events could not be loaded."

        if (events.isEmpty()) {
            emptyEvents.visibility = View.VISIBLE
        } else {
            emptyEvents.visibility = View.GONE
            events.take(3).forEach { event ->
                activeEventsContainer.addView(eventListCard(event) {
                    val target = selected?.takeIf { it.id == event.id } ?: event
                    openOrganizerPage(EventManagementHubActivity::class.java, target.id, target.title)
                })
            }
        }
    }

    private fun stopSwipeRefresh() {
        if (isSwipeRefreshing) {
            swipeRefreshLayout.isRefreshing = false
            isSwipeRefreshing = false
        }
    }
}
