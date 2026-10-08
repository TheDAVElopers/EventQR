package com.thedavelopers.eventqr.features.dashboard

import com.thedavelopers.eventqr.core.util.UiStrings
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.firstNameOnly
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.attendee.AttendeeBottomNavItem
import com.thedavelopers.eventqr.features.attendee.AttendeeRepository
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_CAPACITY
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_CATEGORY
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_COUNT
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_DESCRIPTION
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_END
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_LOCATION
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_START
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_STATUS
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.attendee.configureAttendeeBottomNav
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardUpcomingEvent
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.ui.components.EventCardHolder
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.launch

open class DashboardActivity : AppCompatActivity(), DashboardContract.View {
    private lateinit var presenter: DashboardPresenter
    private lateinit var sessionManager: SessionManager
    private lateinit var welcomeText: TextView
    private lateinit var nameText: TextView
    private lateinit var summaryEvents: TextView
    private lateinit var summaryRegistrations: TextView
    private lateinit var summaryCompleted: TextView
    private lateinit var skeletonLoading: View
    private lateinit var attendeeCard: View
    private lateinit var organizerCard: View
    private lateinit var notificationsCard: View
    private lateinit var notificationBell: ImageView
    private lateinit var notificationBadge: TextView
    private lateinit var upcomingEventsLayout: LinearLayout
    private lateinit var discoverEventsLayout: LinearLayout
    private lateinit var discoverEventsSeeAll: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var isSwipeRefreshing = false
    private var refreshBadgeOnResume = false
    private val upcomingEventHolders = mutableMapOf<String, EventCardHolder>()
    private val discoverEventHolders = mutableMapOf<String, EventCardHolder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_dashboard)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.headerDashboard).applyEventQrTopInsetPadding()
        configureAttendeeBottomNav(AttendeeBottomNavItem.DASHBOARD)

        sessionManager = SessionManager(this)
        presenter = DashboardPresenter(
            this,
            DashboardRepository(this),
            AttendeeRepository(this),
            sessionManager,
            UiStrings(this),
        )
        presenter.attach(this)

        welcomeText = findViewById(R.id.txtDashboardWelcome)
        nameText = findViewById(R.id.txtDashboardName)
        summaryEvents = findViewById(R.id.txtTotalEvents)
        summaryRegistrations = findViewById(R.id.txtTotalRegistrations)
        summaryCompleted = findViewById(R.id.txtTotalCompleted)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        attendeeCard = findViewById(R.id.btnAttendeeHub)
        organizerCard = findViewById(R.id.btnTransactionHistory)
        notificationsCard = findViewById(R.id.btnNotificationsHub)
        notificationBell = findViewById(R.id.btnDashboardNotifications)
        notificationBadge = findViewById(R.id.txtNotificationBadge)
        upcomingEventsLayout = findViewById(R.id.layoutUpcomingEvents)
        discoverEventsLayout = findViewById(R.id.layoutDiscoverEvents)
        discoverEventsSeeAll = findViewById(R.id.txtDiscoverEventsSeeAll)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)

        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            presenter.loadDashboard()
        }

        notificationBell.setOnClickListener {
            refreshBadgeOnResume = true
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeNotificationsActivity::class.java))
        }
        discoverEventsSeeAll.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeEventsActivity::class.java))
        }

        setupPortalSwitcher()
        configureActions()
        presenter.loadDashboard()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (refreshBadgeOnResume) {
            refreshBadgeOnResume = false
            presenter.loadDashboard()
        }
        // Re-issue the access token so a role change (e.g. upgraded to organizer after an
        // event request approval) is reflected without requiring a logout/login.
        lifecycleScope.launch {
            AuthRepository(this@DashboardActivity).refreshSessionToken()
        }
    }

    override fun showLoading(isLoading: Boolean) {
        if (isSwipeRefreshing) {
            if (!isLoading) {
                stopSwipeRefresh()
            }
            return
        }

        skeletonLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        upcomingEventsLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
        discoverEventsLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    override fun showSummary(summary: DashboardSummary) {
        stopSwipeRefresh()
        skeletonLoading.visibility = View.GONE
        upcomingEventsLayout.visibility = View.VISIBLE
        discoverEventsLayout.visibility = View.VISIBLE

        nameText.text = (summary.fullName?.takeIf { it.isNotBlank() }
            ?: sessionManager.getFullName()?.takeIf { it.isNotBlank() }
            ?: "Attendee").firstNameOnly()

        summaryEvents.text = summary.totalEvents.toString()
        summaryRegistrations.text = summary.totalRegistrations?.toString() ?: getString(R.string.dashboard_value_unavailable)
        summaryCompleted.text = summary.completedEventsCount?.toString() ?: getString(R.string.dashboard_value_unavailable)
        updateNotificationBadge(summary.totalNotifications)

        setupPortalSwitcher()
        renderUpcomingEvents(summary.upcomingEvents.orEmpty())
        renderDiscoverEvents(summary.discoverEvents.orEmpty())
    }

    private fun updateNotificationBadge(unreadCount: Long) {
        if (unreadCount > 0) {
            notificationBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            notificationBadge.visibility = View.VISIBLE
        } else {
            notificationBadge.visibility = View.GONE
        }
    }

    override fun showError(message: String) {
        stopSwipeRefresh()
        setupPortalSwitcher()
        skeletonLoading.visibility = View.GONE
        upcomingEventsLayout.visibility = View.VISIBLE
        discoverEventsLayout.visibility = View.VISIBLE
        renderUpcomingEvents(emptyList())
        renderDiscoverEvents(emptyList())
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun openSection(title: String, message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun updateHeader(role: String?, name: String?) {
        welcomeText.text = getString(R.string.common_welcome_back)
        nameText.text = (name?.takeIf { it.isNotBlank() } ?: "Attendee").firstNameOnly()
    }

    private fun renderUpcomingEvents(events: List<DashboardUpcomingEvent>) {
        val rows = events.take(1)
        if (rows.isEmpty()) {
            renderEmptyState(upcomingEventsLayout, upcomingEventHolders, getString(R.string.registered_events_none_upcoming))
            return
        }
        syncEventRows(upcomingEventsLayout, upcomingEventHolders, rows)
    }

    private fun renderDiscoverEvents(events: List<DashboardUpcomingEvent>) {
        if (events.isEmpty()) {
            renderEmptyState(discoverEventsLayout, discoverEventHolders, getString(R.string.attendee_events_no_upcoming_events))
            return
        }
        syncEventRows(discoverEventsLayout, discoverEventHolders, events)
    }

    private fun renderEmptyState(
        layout: LinearLayout,
        holders: MutableMap<String, EventCardHolder>,
        message: String,
    ) {
        holders.clear()
        while (layout.childCount > 1) {
            layout.removeViewAt(1)
        }
        layout.addView(createEmptyStateView(message))
    }

    private fun syncEventRows(
        layout: LinearLayout,
        holders: MutableMap<String, EventCardHolder>,
        events: List<DashboardUpcomingEvent>,
    ) {
        val rows = events.distinctBy { it.eventId }
        val liveKeys = rows.mapTo(mutableSetOf()) { it.eventId.toString() }

        holders.keys.filterNot { it in liveKeys }.forEach { holders.remove(it) }
        (1 until layout.childCount)
            .map { layout.getChildAt(it) }
            .filter { it.tag !in liveKeys }
            .forEach { layout.removeView(it) }

        rows.forEachIndexed { index, event ->
            val holder = holders.getOrPut(event.eventId.toString()) {
                EventCardHolder(this).also { created ->
                    created.view.tag = event.eventId.toString()
                    created.view.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
            }
            bindEventCard(holder, event, isFirst = index == 0)
            if (layout.getChildAt(index + 1) !== holder.view) {
                (holder.view.parent as? ViewGroup)?.removeView(holder.view)
                layout.addView(holder.view, minOf(index + 1, layout.childCount))
            }
        }
    }

    private fun bindEventCard(holder: EventCardHolder, event: DashboardUpcomingEvent, isFirst: Boolean) {
        val eventStatus = EventStatusBadgeStyler.resolve(
            event.status?.let { EventStatusBadgeStyler.fromLabel(it) },
            event.eventStartAt,
            event.eventEndAt,
        )
        val date = EventCardPresenter.dateParts(event.eventStartAt)
        holder.update(
            title = event.title.ifBlank { "Untitled event" },
            status = EventStatusBadgeStyler.displayLabel(eventStatus, event.status),
            day = date.day,
            month = date.month,
            time = date.time,
            location = EventCardPresenter.location(event.location),
            count = if (event.capacityUnknown) null else event.currentAttendeeCount,
            capacity = if (event.capacityUnknown) null else EventCardPresenter.capacity(event.capacity),
            onClick = { openEventDetail(event) },
        )
        val topMargin = if (isFirst) dp(12) else 0
        val params = holder.view.layoutParams as LinearLayout.LayoutParams
        if (params.topMargin != topMargin) {
            params.topMargin = topMargin
            holder.view.layoutParams = params
        }
    }

    private fun createEmptyStateView(message: String): View {
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)
        val textDisabled = ContextCompat.getColor(this, R.color.text_disabled)

        val container = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(12)
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        container.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setBackgroundResource(R.drawable.bg_dashboard_empty_icon)
            addView(ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
                setImageResource(R.drawable.ic_calendar)
                setColorFilter(textDisabled)
            })
        })

        container.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(12)
            }
            text = message
            setTextColor(textSecondary)
            textSize = 13f
            gravity = Gravity.CENTER
        })

        return container
    }

    private fun openEventDetail(event: DashboardUpcomingEvent) {
        val intent = Intent(this, com.thedavelopers.eventqr.features.attendee.EventDetailActivity::class.java).apply {
            putExtra(EXTRA_EVENT_ID, event.eventId.toString())
            putExtra(EXTRA_EVENT_TITLE, event.title)
            putExtra(EXTRA_EVENT_LOCATION, event.location ?: "")
            putExtra(EXTRA_EVENT_DESCRIPTION, event.description ?: "")
            putExtra(EXTRA_EVENT_CATEGORY, event.category ?: "")
            putExtra(EXTRA_EVENT_START, DateFormatters.formatInstant(event.eventStartAt))
            putExtra(EXTRA_EVENT_END, DateFormatters.formatInstant(event.eventEndAt))
            putExtra(EXTRA_EVENT_STATUS, event.status ?: "Upcoming")
            putExtra(EXTRA_EVENT_CAPACITY, event.capacity.toString())
            putExtra(EXTRA_EVENT_COUNT, event.currentAttendeeCount.toString())
        }
        startActivity(intent)
    }

    private fun setupPortalSwitcher() {
        val role = sessionManager.getUserRole()
        val normalizedRole = RoleMapper.normalizeRole(role)
        val allowedPortals = PortalSwitcher.portalsForRole(normalizedRole)

        val chip = findViewById<View>(R.id.portalSwitcherChip)
        val dot = findViewById<View>(R.id.txtDashboardNameDot)
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

            val icon = portalView.findViewById<ImageView>(R.id.imgPortalIcon)
            val subtitle = portalView.findViewById<TextView>(R.id.txtPortalSubtitle)
            icon.setImageResource(PortalSwitcher.iconRes(portal))
            subtitle.text = PortalSwitcher.subtitle(this, portal)

            if (portal == PortalSwitcher.PORTAL_ATTENDEE) {
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
        when (portal) {
            PortalSwitcher.PORTAL_ATTENDEE -> Unit
            PortalSwitcher.PORTAL_STAFF -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java))
            }
            PortalSwitcher.PORTAL_ORGANIZER -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java))
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun configureActions() {
        attendeeCard.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeEventsActivity::class.java))
        }
        organizerCard.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeTransactionsActivity::class.java))
        }
        notificationsCard.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.RequestEventActivity::class.java))
        }
    }

    private fun stopSwipeRefresh() {
        if (isSwipeRefreshing) {
            swipeRefreshLayout.isRefreshing = false
            isSwipeRefreshing = false
        }
    }
}