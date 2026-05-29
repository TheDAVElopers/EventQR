package com.thedavelopers.eventqr.features.organizer.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDashboardDto
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class OrganizerDashboardActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_dashboard)
        repository = OrganizerRepository(this)
        sessionManager = SessionManager(this)
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
        findViewById<View>(R.id.btnLogoutTest).setOnClickListener {
            sessionManager.clearSession()
            val intent = Intent(this, com.thedavelopers.eventqr.features.auth.login.LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadDashboard() {
        findViewById<ProgressBar>(R.id.progressDashboardLoading).visibility = View.VISIBLE
        findViewById<View>(R.id.layoutDashboardError).visibility = View.GONE
        MainScope().launch {
            val dashboard = repository.loadDashboardForMvp()
            val load = repository.loadEventsForMvp()
            renderDashboard(load, dashboard)
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
}
