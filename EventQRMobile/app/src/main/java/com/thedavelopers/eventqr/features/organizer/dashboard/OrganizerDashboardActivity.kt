package com.thedavelopers.eventqr.features.organizer.dashboard

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.firstNameOnly
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
import com.thedavelopers.eventqr.features.organizer.events.ManageEventsActivity
import com.thedavelopers.eventqr.features.organizer.NAV_DASHBOARD
import com.thedavelopers.eventqr.features.organizer.bottomNav
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDashboardDto
import com.thedavelopers.eventqr.features.organizer.notifications.NotificationManagementActivity
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.ui.components.EventCardHolder
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

open class OrganizerDashboardActivity : AppCompatActivity() {
    private val TAG = "OrganizerDashboardActivity"
    private lateinit var repository: OrganizerRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private var isSwipeRefreshing = false
    private var loadedOnce = false
    private val organizerZone: ZoneId = ZoneId.of("Asia/Manila")
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
    private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_dashboard)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.headerDashboard).applyEventQrTopInsetPadding()
        repository = OrganizerRepository(this)
        sessionManager = SessionManager(this)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            loadDashboard()
        }
        setupNavigation()
        findViewById<LinearLayout>(R.id.layoutBottomNavHost).addView(bottomNav(NAV_DASHBOARD))
        // Re-issue the access token so the CURRENT database role is used (an attendee
        // upgraded to organizer after approval should be able to open this dashboard
        // immediately, without a logout/login).
        MainScope().launch {
            AuthRepository(this@OrganizerDashboardActivity).refreshSessionToken()
            loadDashboard()
        }
    }

    override fun onResume() {
        super.onResume()
        if (loadedOnce) {
            lifecycleScope.launch {
                updateNotificationBadge(repository.getMyNotifications())
            }
        }
        loadedOnce = true
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.btnOrganizerNotifications)?.setOnClickListener {
            startActivity(Intent(this@OrganizerDashboardActivity, NotificationManagementActivity::class.java))
        }

        findViewById<View>(R.id.btnSeeAllEvents).setOnClickListener {
            openOrganizerPage(ManageEventsActivity::class.java, selectedEventId().takeIf { it.isNotBlank() })
        }
        findViewById<View>(R.id.btnDashboardRetry).setOnClickListener {
            loadDashboard()
        }

        setupPortalSwitcher()
    }

    private fun updateNotificationBadge(notifResult: NetworkResult<List<NotificationResponse>>) {
        val badge = findViewById<TextView>(R.id.txtOrganizerNotificationBadge) ?: return
        val unreadCount = when (notifResult) {
            is NetworkResult.Success -> notifResult.data.count { it.status != NotificationStatus.READ && it.readAt == null }
            else -> 0
        }
        if (unreadCount > 0) {
            badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun setupPortalSwitcher() {
        val role = sessionManager.getUserRole() ?: return
        val normalizedRole = RoleMapper.normalizeRole(role)
        val allowedPortals = PortalSwitcher.portalsForRole(normalizedRole)

        val chip = findViewById<View>(R.id.portalSwitcherChip)
        val dot = findViewById<View>(R.id.txtHeaderSubtitleDot)
        chip.visibility = if (allowedPortals.size > 1) View.VISIBLE else View.GONE
        dot.visibility = if (allowedPortals.size > 1) View.VISIBLE else View.GONE
        chip.setOnClickListener(null)

        if (allowedPortals.size > 1) {
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
            portalView.findViewById<TextView>(R.id.txtPortalName).text = PortalSwitcher.title(this, portal)
            
            val icon = portalView.findViewById<android.widget.ImageView>(R.id.imgPortalIcon)
            val subtitle = portalView.findViewById<TextView>(R.id.txtPortalSubtitle)
            icon.setImageResource(PortalSwitcher.iconRes(portal))
            subtitle.text = PortalSwitcher.subtitle(this, portal)

            if (portal == PortalSwitcher.PORTAL_ORGANIZER) {
                portalView.findViewById<View>(R.id.currentPortalBadge).visibility = View.VISIBLE
            }

            portalView.setOnClickListener {
                dialog.dismiss()
                switchToPortal(portal)
            }
            container.addView(portalView)
        }
        
        view.findViewById<View>(R.id.btnPortalSignOut).setOnClickListener {
            dialog.dismiss()
            com.thedavelopers.eventqr.core.session.SignOutFlow.confirmAndSignOut(this)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun switchToPortal(portal: String) {
        when(portal) {
            PortalSwitcher.PORTAL_ATTENDEE -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_STAFF -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_ORGANIZER -> {
                // Already here
            }
            PortalSwitcher.PORTAL_ADMIN -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_SUPER_ADMIN -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java))
                finish()
            }
        }
    }

    private fun loadDashboard() {
        if (!isSwipeRefreshing) {
            skeletonLoading.visibility = View.VISIBLE
            findViewById<View>(R.id.statsGrid).visibility = View.GONE
        } else {
            skeletonLoading.visibility = View.GONE
        }
        findViewById<View>(R.id.layoutDashboardError).visibility = View.GONE
        MainScope().launch {
            try {
                val dashboard = repository.loadDashboardForMvp()
                val load = repository.loadEventsForMvp()
                renderDashboard(load, dashboard)
                updateNotificationBadge(repository.getMyNotifications())
            } catch (error: Exception) {
                skeletonLoading.visibility = View.GONE
                if (!isSwipeRefreshing) findViewById<View>(R.id.statsGrid).visibility = View.GONE
                findViewById<View>(R.id.layoutDashboardError).visibility = View.VISIBLE
                Log.w(TAG, "Dashboard load failed: ${error.message ?: "unknown"}", error)
                findViewById<TextView>(R.id.txtDashboardError).text = getString(R.string.organizer_dashboard_couldn_t_load_your_dashboard_check_y)
            } finally {
                stopSwipeRefresh()
            }
        }
    }

    private fun renderDashboard(
        load: OrganizerMvpLoad<List<OrganizerMvpEvent>>,
        dashboard: OrganizerMvpLoad<OrganizerDashboardDto?>? = null,
    ) {
        skeletonLoading.visibility = View.GONE
        findViewById<View>(R.id.statsGrid).visibility = View.VISIBLE
        val dashboardData = dashboard?.data
        val name = dashboardData?.organizerName.orEmpty().ifBlank { sessionManager.getFullName().orEmpty().ifBlank { "Organizer" } }

        findViewById<TextView>(R.id.txtHeaderTitle).text = PortalSwitcher.PORTAL_ORGANIZER
        findViewById<TextView>(R.id.txtHeaderSubtitle).text = name.firstNameOnly()

        val events = load.data.approvedOnly()
        val activeEvents = events.filter { it.lifecycleStatus() == "Active" }
        val selected = repository.resolveSelectedEvent(events, selectedEventId())
        // Counted-as-registered across approved/active/ended events, from the backend.
        val totalAttendees = dashboardData?.totalRegistrations ?: dashboardData?.totalAttendees ?: events.sumOf { it.registeredCount }
        val totalTransactions = dashboardData?.totalTransactions ?: events.sumOf { it.totalTransactions }
        // Reward redemptions (a count of redeemed rewards, not points).
        val totalPoints = dashboardData?.rewardRedemptions ?: if (events.isNotEmpty()) {
            events.sumOf { it.rewardRedemptions }
        } else {
            dashboardData?.recentEvents?.sumOf { it.rewardRedemptions } ?: 0L
        }
        val totalEvents = dashboardData?.totalEvents ?: events.size.toLong()

        findViewById<TextView>(R.id.txtStatTotalEvents).text = formatCount(totalEvents)
        findViewById<TextView>(R.id.txtStatTotalAttendees).text = formatCount(totalAttendees)
        findViewById<TextView>(R.id.txtStatTransactions).text = formatCount(totalTransactions)
         findViewById<TextView>(R.id.txtStatRewardsGiven).text = formatCount(totalPoints)

        val activeEventsContainer = findViewById<LinearLayout>(R.id.activeEventsContainer)
        val emptyEvents = findViewById<View>(R.id.layoutEventsEmpty)
        activeEventsContainer.removeAllViews()

        val hasError = load.source == OrganizerMvpDataSource.ERROR
        findViewById<View>(R.id.layoutDashboardError).visibility = if (hasError && events.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txtDashboardError).text = load.message ?: "Organizer events could not be loaded."

        if (activeEvents.isEmpty()) {
            emptyEvents.visibility = View.VISIBLE
        } else {
            emptyEvents.visibility = View.GONE
            activeEvents.take(3).forEach { event ->
                activeEventsContainer.addView(dashboardEventCard(event) {
                    val target = selected?.takeIf { it.id == event.id } ?: event
                    openOrganizerPage(EventManagementHubActivity::class.java, target.id, target.title)
                })
            }
        }
    }

    private fun dashboardEventCard(
        event: OrganizerMvpEvent,
        onClick: () -> Unit,
    ): View {
        val parsedStart = parseEventStartDateTime(event)
        val parsedDate = parsedStart?.toLocalDate() ?: parseEventDateOnly(event)

        val holder = EventCardHolder(this)
        holder.update(
            title = event.title,
            status = event.lifecycleStatus(),
            day = parsedDate?.format(dayFormatter) ?: EventCardPresenter.UNKNOWN_DAY,
            month = parsedDate?.format(monthFormatter)?.uppercase(Locale.ENGLISH) ?: EventCardPresenter.UNKNOWN_MONTH,
            time = parsedStart?.format(timeFormatter) ?: EventCardPresenter.UNKNOWN_TIME,
            location = event.venue.takeIf { it.isNotBlank() && it != "Venue not set" } ?: EventCardPresenter.UNKNOWN_LOCATION,
            count = event.currentAttendeeCount.coerceAtLeast(0),
            capacity = EventCardPresenter.capacity(event.capacity),
            onClick = onClick,
        )
        return holder.view
    }

    private fun parseEventStartDateTime(event: OrganizerMvpEvent): LocalDateTime? {
        val candidates = listOfNotNull(event.dateTime, event.shortDate)
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "-" }

        candidates.forEach { raw ->
            val firstPart = raw.substringBefore(" - ").trim()
            parseDateTimeValue(firstPart)?.let { return it }
            parseDateTimeValue(raw)?.let { return it }
        }
        return null
    }

    private fun parseEventDateOnly(event: OrganizerMvpEvent): LocalDate? {
        val candidates = listOfNotNull(event.shortDate, event.dateTime)
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "-" }

        candidates.forEach { raw ->
            val firstPart = raw.substringBefore(" - ").trim()
            parseDateValue(firstPart)?.let { return it }
            parseDateValue(raw)?.let { return it }
        }
        return null
    }

    private fun parseDateTimeValue(value: String): LocalDateTime? {
        val normalized = value.replace("•", "").replace("  ", " ").trim()
        return runCatching { Instant.parse(normalized).atZone(organizerZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(normalized).atZoneSameInstant(organizerZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(normalized).withZoneSameInstant(organizerZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) }.getOrNull()
    }

    private fun parseDateValue(value: String): LocalDate? {
        val normalized = value.replace("•", "").replace("  ", " ").trim()
        return runCatching { LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            ?: runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)) }.getOrNull()
            ?: parseDateTimeValue(normalized)?.toLocalDate()
    }

    private fun stopSwipeRefresh() {
        if (isSwipeRefreshing) {
            swipeRefreshLayout.isRefreshing = false
            isSwipeRefreshing = false
        }
    }
}
