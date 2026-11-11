package com.thedavelopers.eventqr.features.dashboard

import com.thedavelopers.eventqr.core.util.UiStrings
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.firstNameOnly
import com.thedavelopers.eventqr.features.attendee.AttendeeBadges
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
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardUpcomingEvent
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.ListRowBinder
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.PassHeadModel
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.icons.FieldIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Attendee dashboard (§6.2). The pass head is the header: role tabs, first name, and a three-cell
 * stub (REGISTERED / CHECK-INS / EVENTS) replacing the 135-degree gradient block and its translucent
 * stat tiles. Below it the screen answers one question - "what is next" - with a 96dp date row,
 * then three quieter rows, then two chips. No stat grid, no quick-action card.
 */
open class DashboardActivity : AppCompatActivity(), DashboardContract.View {
    private lateinit var presenter: DashboardPresenter
    private lateinit var sessionManager: SessionManager
    private lateinit var passHead: View
    private lateinit var skeletonLoading: View
    private lateinit var organizerCard: View
    private lateinit var requestEventCard: View
    private lateinit var upcomingEventsLayout: LinearLayout
    private lateinit var discoverEventsLayout: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var isSwipeRefreshing = false
    private var isFirstResume = true
    private var hasRenderedContent = false

    /** Every load goes through [reload], so a resume can prove it issues exactly one request. */
    @VisibleForTesting
    internal var loadRequests = 0
        private set

    // The pass head is re-bound on every summary / role change, so keep the last values it must
    // show rather than reading them back out of the view tree.
    private var headName: String? = null
    private var headBell: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_dashboard)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
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

        passHead = findViewById(R.id.passHead)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        organizerCard = findViewById(R.id.btnTransactionHistory)
        requestEventCard = findViewById(R.id.btnRequestEventCard)
        // The quick-action chips carry a 20dp glyph; a compound drawable would otherwise lay out at
        // the vector's 24dp intrinsic size (§5.6).
        listOf(R.id.btnTransactionHistory, R.id.btnRequestEventCard).forEach {
            FieldIcons.apply(findViewById(it))
        }
        upcomingEventsLayout = findViewById(R.id.layoutUpcomingEvents)
        discoverEventsLayout = findViewById(R.id.layoutDiscoverEvents)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)

        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            reload()
        }

        bindPassHead()
        configureActions()
        reload()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // The nav reorders this instance to the front, so returning from a detail screen must
        // reload the rows it may have changed (registration, cancellation). The first resume is
        // the onCreate load: one request per resume, never two racing summaries.
        if (!isFirstResume) reload()
        isFirstResume = false
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

        // Once rows are on screen a reload refreshes in place: no skeleton over readable content,
        // no layout jump on return from a detail screen.
        if (hasRenderedContent) {
            skeletonLoading.visibility = View.GONE
            return
        }
        skeletonLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        upcomingEventsLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
        discoverEventsLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    override fun showSummary(summary: DashboardSummary) {
        stopSwipeRefresh()
        hasRenderedContent = true
        skeletonLoading.visibility = View.GONE
        upcomingEventsLayout.visibility = View.VISIBLE
        discoverEventsLayout.visibility = View.VISIBLE

        headName = (summary.fullName?.takeIf { it.isNotBlank() }
            ?: sessionManager.getFullName()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.ui_attendee_role)).firstNameOnly()
        headBell = summary.totalNotifications.toInt()

        bindPassHead(summary)
        renderUpcomingEvents(summary.upcomingEvents.orEmpty())
        renderDiscoverEvents(summary.discoverEvents.orEmpty())
    }

    override fun showError(message: String) {
        stopSwipeRefresh()
        skeletonLoading.visibility = View.GONE
        // A failed refresh over readable rows keeps them; a failed first load owns the body. The
        // empty-list copy is never rendered here - it would claim there is nothing to show when the
        // request simply did not answer.
        if (!hasRenderedContent) {
            upcomingEventsLayout.visibility = View.VISIBLE
            // One request feeds both blocks, so one error block carries the failure and its Retry;
            // an identical block under DISCOVER would be a second control for the same request.
            discoverEventsLayout.visibility = View.GONE
            bindPassHead()
            renderUpcomingError()
        } else {
            // The rows stay readable; the failure is a background notice.
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun openSection(title: String, message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun updateHeader(role: String?, name: String?) {
        headName = (name?.takeIf { it.isNotBlank() } ?: headName ?: getString(R.string.ui_attendee_role))
            .firstNameOnly()
        bindPassHead()
    }

    // ── Pass head ────────────────────────────────────────────────────────────

    /**
     * @param summary null = not loaded yet (or the load failed); the stub renders "--" rather than
     *   a fake zero for every cell.
     */
    private fun bindPassHead(summary: DashboardSummary? = null) {
        val portals = PortalSwitcher.portalsForRole(RoleMapper.normalizeRole(sessionManager.getUserRole()))
        val roleNames = portals.map { PortalSwitcher.shortRoleName(this, it) }
        val stats = DashboardStats.passHeadStats(
            summary = summary,
            registeredLabel = getString(R.string.ui_attendee_registered),
            checkInsLabel = getString(R.string.ui_attendee_check_ins),
            eventsLabel = getString(R.string.ui_attendee_events),
            format = { formatCount(it) },
        )

        PassHeadBinder.bind(
            passHead,
            PassHeadModel(
                title = headName ?: sessionManager.getFullName()?.takeIf { it.isNotBlank() }
                    ?.firstNameOnly()
                    ?: getString(R.string.ui_attendee_role),
                eyebrow = if (roleNames.size <= 1) roleNames.firstOrNull() else null,
                tabs = if (roleNames.size > 1) roleNames else emptyList(),
                selectedTabIndex = portals.indexOf(PortalSwitcher.PORTAL_ATTENDEE).coerceAtLeast(0),
                bellCount = headBell,
                stats = stats,
                onTabSelected = { index -> portals.getOrNull(index)?.let(::switchToPortal) },
                onBellClick = {
                    startActivity(
                        Intent(
                            this,
                            com.thedavelopers.eventqr.features.attendee.AttendeeNotificationsActivity::class.java,
                        ),
                    )
                },
            ),
        )
    }

    private fun formatCount(value: Long?): String =
        value?.let { NumberFormat.getIntegerInstance(Locale.getDefault()).format(it) }
            ?: getString(R.string.dashboard_value_unavailable)

    // ── Rows ─────────────────────────────────────────────────────────────────

    private fun renderUpcomingEvents(events: List<DashboardUpcomingEvent>) {
        val row = events.distinctBy { it.eventId }.firstOrNull()
        replaceRows(upcomingEventsLayout) {
            if (row == null) {
                addView(emptyRow(R.string.ui_attendee_dashboard_empty_next, R.string.ui_attendee_dashboard_empty_next_body))
            } else {
                addView(dateRow(row))
            }
        }
    }

    private fun renderDiscoverEvents(events: List<DashboardUpcomingEvent>) {
        val rows = events.distinctBy { it.eventId }.take(3)
        replaceRows(discoverEventsLayout) {
            if (rows.isEmpty()) {
                addView(emptyRow(R.string.ui_attendee_dashboard_empty_activity, R.string.ui_attendee_dashboard_empty_activity_body))
            } else {
                rows.forEach { addView(iconRow(it)) }
            }
        }
    }

    /** Keeps the eyebrow at index 0 (the layout contract) and swaps everything below it. */
    private inline fun replaceRows(container: LinearLayout, build: LinearLayout.() -> Unit) {
        while (container.childCount > 1) {
            container.removeViewAt(1)
        }
        container.build()
    }

    private fun dateRow(event: DashboardUpcomingEvent): View {
        val row = layoutInflater.inflate(R.layout.list_row_date, upcomingEventsLayout, false)
        val parts = EventCardPresenter.dateParts(event.eventStartAt)
        row.findViewById<TextView>(R.id.listRowDay).text = parts.day
        row.findViewById<TextView>(R.id.listRowMonth).text = parts.month
        ListRowBinder.bind(
            row,
            title = event.title.ifBlank { getString(R.string.common_untitled_event) },
            meta = metaFor(event, parts.time),
            trailing = ListRowBinder.Trailing.BADGE,
            showDivider = false,
            onClick = { openEventDetail(event) },
        )
        AttendeeBadges.bind(row.findViewById(R.id.listRowBadge), statusOf(event))
        return row
    }

    private fun iconRow(event: DashboardUpcomingEvent): View {
        val row = layoutInflater.inflate(R.layout.list_row_icon, discoverEventsLayout, false)
        row.findViewById<android.widget.ImageView>(R.id.listRowIcon)
            .setImageResource(AppIcons.Event)
        val parts = EventCardPresenter.dateParts(event.eventStartAt)
        ListRowBinder.bind(
            row,
            title = event.title.ifBlank { getString(R.string.common_untitled_event) },
            meta = metaFor(event, parts.time),
            trailing = ListRowBinder.Trailing.CHEVRON,
            showDivider = true,
            onClick = { openEventDetail(event) },
        )
        return row
    }

    private fun emptyRow(titleRes: Int, bodyRes: Int): View {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setPadding(
                resources.getDimensionPixelSize(R.dimen.ui_gutter),
                resources.getDimensionPixelSize(R.dimen.space_lg),
                resources.getDimensionPixelSize(R.dimen.ui_gutter),
                resources.getDimensionPixelSize(R.dimen.space_lg),
            )
        }
        block.addView(TextView(this).apply {
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
            setTextColor(getColor(R.color.ui_text_primary))
            text = getString(titleRes)
        })
        block.addView(TextView(this).apply {
            setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
            setTextColor(getColor(R.color.ui_text_secondary))
            text = getString(bodyRes)
        })
        return block
    }

    /**
     * The first-load failure owns the body: one honest error with the Retry for the one request
     * that feeds both blocks. It replaces the "no events" copy, which would claim the list is
     * empty when the request simply did not answer.
     */
    private fun renderUpcomingError() {
        replaceRows(upcomingEventsLayout) {
            addView(
                EventQrEmptyState(this@DashboardActivity).apply {
                    setIcon(AppIcons.Warning)
                    show(
                        getString(R.string.ui_attendee_dashboard_error_title),
                        getString(R.string.ui_attendee_load_error_body),
                    )
                    setAction(getString(R.string.common_retry)) { reload() }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    /** Every load path counts here, so a resume cannot quietly issue a second request. */
    private fun reload() {
        loadRequests++
        presenter.loadDashboard()
    }

    private fun metaFor(event: DashboardUpcomingEvent, time: String): String =
        listOf(time, EventCardPresenter.location(event.location))
            .filter { it.isNotBlank() }
            .joinToString(" - ")

    private fun statusOf(event: DashboardUpcomingEvent) = EventStatusBadgeStyler.resolve(
        event.status?.let { EventStatusBadgeStyler.fromLabel(it) },
        event.eventStartAt,
        event.eventEndAt,
    )

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

    // ── Navigation ───────────────────────────────────────────────────────────

    private fun switchToPortal(portal: String) {
        when (portal) {
            PortalSwitcher.PORTAL_ATTENDEE -> Unit
            PortalSwitcher.PORTAL_STAFF -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java))
            }
            PortalSwitcher.PORTAL_ORGANIZER -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java))
            }
            PortalSwitcher.PORTAL_ADMIN, PortalSwitcher.PORTAL_SUPER_ADMIN -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java))
                finish()
            }
        }
    }

    private fun configureActions() {
        organizerCard.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeTransactionsActivity::class.java))
        }
        requestEventCard.setOnClickListener {
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
