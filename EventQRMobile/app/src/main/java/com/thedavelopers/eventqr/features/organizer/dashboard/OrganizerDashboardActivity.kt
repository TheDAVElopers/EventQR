package com.thedavelopers.eventqr.features.organizer.dashboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.attendee.RequestEventActivity
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.organizer.NAV_DASHBOARD
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.approvedOnly
import com.thedavelopers.eventqr.features.organizer.bottomNav
import com.thedavelopers.eventqr.features.organizer.lifecycleStatus
import com.thedavelopers.eventqr.features.organizer.openOrganizerPage
import com.thedavelopers.eventqr.features.organizer.selectedEventId
import com.thedavelopers.eventqr.features.organizer.summaryCard
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
import com.thedavelopers.eventqr.features.organizer.events.ManageEventsActivity
import com.thedavelopers.eventqr.features.organizer.notifications.NotificationManagementActivity
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.ListRowBinder
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.PassHeadModel
import com.thedavelopers.eventqr.ui.components.PassStat
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import com.thedavelopers.eventqr.ui.theme.color
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Organizer dashboard (§6.4).
 *
 * The pass head is the header - ORGANIZER tab (plus whatever portals the account may enter), the
 * title, and a three-cell stub (REGISTERED / ATTENDED / LIVE NOW). Below it: three metric rows,
 * the next event as one date row and the active events as hairline rows. There is no stat-card
 * grid, no chip row and no gradient.
 */
open class OrganizerDashboardActivity : AppCompatActivity() {
    private val TAG = "OrganizerDashboardActivity"
    private lateinit var repository: OrganizerRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var activeEventsContainer: LinearLayout
    private lateinit var metricRowsContainer: LinearLayout
    private lateinit var nextEventHost: ViewGroup
    private lateinit var nextEventEyebrow: View
    private lateinit var emptyEvents: EventQrEmptyState
    private lateinit var errorState: EventQrEmptyState
    private lateinit var passHead: View
    private var isSwipeRefreshing = false
    private var isFirstResume = true
    private var hasRenderedContent = false

    private val organizerZone: ZoneId = ZoneId.of("Asia/Manila")
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
    private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    private var headBell: Int = 0
    // Counts resume-triggered reload decisions so the screen can prove one request per resume.
    @VisibleForTesting
    internal var resumeReloads = 0
        private set
    // All null until the load answers: unknown prints `--`, never a fake 0.
    private var numbers = OrganizerDashboardNumbers()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_dashboard)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()

        repository = OrganizerRepository(this)
        sessionManager = SessionManager(this)
        passHead = findViewById(R.id.passHead)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        activeEventsContainer = findViewById(R.id.activeEventsContainer)
        metricRowsContainer = findViewById(R.id.metricRowsContainer)
        nextEventHost = findViewById(R.id.nextEventHost)
        nextEventEyebrow = findViewById(R.id.txtNextEventEyebrow)
        emptyEvents = findViewById(R.id.layoutEventsEmpty)
        errorState = findViewById(R.id.layoutDashboardError)

        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            loadDashboard()
        }

        setupNavigation()
        findViewById<LinearLayout>(R.id.layoutBottomNavHost).addView(bottomNav(NAV_DASHBOARD))
        bindPassHead()

        // Re-issue the access token so the CURRENT database role is used (an attendee
        // upgraded to organizer after approval should be able to open this dashboard
        // immediately, without a logout/login).
        lifecycleScope.launch {
            AuthRepository(this@OrganizerDashboardActivity).refreshSessionToken()
            loadDashboard()
        }
    }

    override fun onResume() {
        super.onResume()
        // A create/edit page returns through CLEAR_TOP|SINGLE_TOP, so this instance is reused and
        // its numbers would stay stale. Reload the dashboard data in place; the first resume is
        // the onCreate load.
        if (!isFirstResume) {
            resumeReloads++
            loadDashboard()
        }
        isFirstResume = false
    }

    // ── Pass head ────────────────────────────────────────────────────────────

    private fun bindPassHead() {
        val portals = PortalSwitcher.portalsForRole(
            RoleMapper.normalizeRole(sessionManager.getUserRole()),
        )
        val roleNames = portals.map { PortalSwitcher.shortRoleName(this, it) }
        val hasTabs = roleNames.size > 1

        PassHeadBinder.bind(
            passHead,
            PassHeadModel(
                title = getString(R.string.ui_organizer_events),
                eyebrow = if (hasTabs) null else getString(R.string.ui_organizer_role),
                tabs = if (hasTabs) roleNames else emptyList(),
                selectedTabIndex = portals.indexOf(PortalSwitcher.PORTAL_ORGANIZER).coerceAtLeast(0),
                bellCount = headBell,
                stats = listOf(
                    PassStat(numberText(numbers.registered), getString(R.string.ui_organizer_registrations)),
                    PassStat(numberText(numbers.attended), getString(R.string.ui_organizer_attended)),
                    PassStat(numberText(numbers.liveNow), getString(R.string.ui_organizer_live_now)),
                ),
                onTabSelected = { index -> portals.getOrNull(index)?.let(::switchToPortal) },
                onBellClick = {
                    startActivity(Intent(this, NotificationManagementActivity::class.java))
                },
            ),
        )
    }

    private fun switchToPortal(portal: String) {
        when (portal) {
            PortalSwitcher.PORTAL_ATTENDEE -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_STAFF -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.StaffDashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_ORGANIZER -> Unit
            PortalSwitcher.PORTAL_ADMIN, PortalSwitcher.PORTAL_SUPER_ADMIN -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java))
                finish()
            }
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private fun setupNavigation() {
        findViewById<View>(R.id.btnSeeAllEvents).setOnClickListener {
            openOrganizerPage(ManageEventsActivity::class.java, selectedEventId().takeIf { it.isNotBlank() })
        }
        // Creation only exists as the event request form; the dashboard action opens it directly
        // instead of dead-ending on the managed-events list.
        findViewById<View>(R.id.btnRequestEvent).setOnClickListener {
            startActivity(Intent(this, RequestEventActivity::class.java))
        }
        errorState.setAction(getString(R.string.common_retry)) { loadDashboard() }
    }

    private fun updateNotificationBadge(notifResult: NetworkResult<List<NotificationResponse>>) {
        val unreadCount = when (notifResult) {
            is NetworkResult.Success -> notifResult.data.count { it.status != NotificationStatus.READ && it.readAt == null }
            else -> 0
        }
        if (unreadCount != headBell) {
            headBell = unreadCount
            bindPassHead()
        }
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    internal open fun loadDashboard() {
        if (!isSwipeRefreshing && !hasRenderedContent) {
            skeletonLoading.visibility = View.VISIBLE
            // The skeleton and the empty state describe the same slot: a reload over a zero-event
            // screen must not claim both that it is loading and that there are no events.
            emptyEvents.visibility = View.GONE
        } else {
            // A reload over readable content refreshes in place: no skeleton over the rows.
            skeletonLoading.visibility = View.GONE
        }

        errorState.hide()
        lifecycleScope.launch {
            try {
                val load = repository.loadEventsForMvp()
                if (load.source != OrganizerMvpDataSource.ERROR) {
                    hasRenderedContent = true
                }
                renderDashboard(load)
                updateNotificationBadge(repository.getMyNotifications())
            } catch (error: Exception) {
                skeletonLoading.visibility = View.GONE
                // The error card is the whole story here too; "no events" under it would
                // contradict it (`renderDashboard` does the same for a failed load).
                emptyEvents.visibility = View.GONE
                errorState.show(
                    getString(R.string.ui_organizer_dashboard_error_title),
                    getString(R.string.ui_attendee_load_error_body),
                )
                Log.w(TAG, "Dashboard load failed: ${error.message ?: "unknown"}", error)
            } finally {
                stopSwipeRefresh()
            }
        }
    }

    internal fun renderDashboard(
        load: OrganizerMvpLoad<List<OrganizerMvpEvent>>,
    ) {
        skeletonLoading.visibility = View.GONE

        val hasError = load.source == OrganizerMvpDataSource.ERROR
        if (!hasError) hasRenderedContent = true
        val events = load.data.approvedOnly()
        val activeEvents = events.filter { it.lifecycleStatus() == "Active" }
        // NEXT EVENT must never be a completed event, and the API returns events unsorted, so the
        // row is derived here instead of from the saved selection that other tabs share.
        val next = if (hasError) null else nextEventOf(events)

        // One source for all six cells: a failed events load leaves every number unknown (`--`).
        numbers = OrganizerDashboardNumbers.of(
            events = events.takeIf { !hasError },
        )
        bindPassHead()

        // Secondary numbers: one metric row each, no cards and no icon tiles (§5.9).
        metricRowsContainer.removeAllViews()
        metricRowsContainer.addView(summaryCard(getString(R.string.ui_organizer_metrics_upcoming), numberText(numbers.upcoming)))
        metricRowsContainer.addView(summaryCard(getString(R.string.ui_organizer_metrics_ended), numberText(numbers.ended)))
        metricRowsContainer.addView(summaryCard(getString(R.string.ui_organizer_redeemed), numberText(numbers.redeemed)))

        // Next event: one 96dp date row.
        nextEventHost.removeAllViews()
        if (next == null) {
            nextEventHost.visibility = View.GONE
        } else {
            nextEventHost.visibility = View.VISIBLE
            nextEventHost.addView(eventRow(next, showDivider = false) {
                openOrganizerPage(EventManagementHubActivity::class.java, next.id, next.title)
            })
        }
        // The eyebrow only exists when the row does.
        nextEventEyebrow.visibility = nextEventHost.visibility

        // Active events: hairline rows, no per-row card.
        activeEventsContainer.removeAllViews()
        // A failed first load owns the list area with the error and its Retry; a failed refresh
        // over content keeps the rows and does not blank the section.
        if (hasError && events.isEmpty()) {
            errorState.show(
                getString(R.string.ui_organizer_dashboard_error_title),
                getString(R.string.ui_attendee_load_error_body),
            )
        } else {
            errorState.hide()
        }

        if (hasError) {
            // The error card is the whole story; an empty state here would contradict it.
            emptyEvents.visibility = View.GONE
        } else if (activeEvents.isEmpty()) {
            emptyEvents.visibility = View.VISIBLE
            if (events.isEmpty()) {
                emptyEvents.show(
                    getString(R.string.ui_organizer_no_events),
                    getString(R.string.ui_organizer_no_events_body),
                )
            } else {
                emptyEvents.show(
                    getString(R.string.ui_organizer_no_active_events),
                    getString(R.string.ui_organizer_no_active_events_body),
                )
            }
            // The Request event chip on the ALL EVENTS row is the one control (§5.10 item 4).
            emptyEvents.setAction(null, primary = false, onClick = null)
        } else {
            emptyEvents.visibility = View.GONE
            activeEvents.take(4).forEachIndexed { index, event ->
                activeEventsContainer.addView(
                    eventRow(event, showDivider = index != activeEvents.take(4).lastIndex) {
                        openOrganizerPage(EventManagementHubActivity::class.java, event.id, event.title)
                    },
                )
            }
        }
    }

    private fun formatCount(value: Long): String =
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    /** Unknown prints the pass head's `--`; a known zero prints `0` (§6.4). */
    private fun numberText(value: Long?): String =
        value?.let(::formatCount) ?: getString(R.string.ui_organizer_value_unavailable)

    /**
     * NEXT EVENT (§6.4): the earliest event that has not ended. The backend returns events
     * unsorted and the saved selection may name a finished one, so the row is picked here by
     * start time, with an undated event sorting last.
     */
    private fun nextEventOf(events: List<OrganizerMvpEvent>): OrganizerMvpEvent? =
        events
            .filter {
                EventStatusBadgeStyler.resolve(
                    EventStatusBadgeStyler.fromLabel(it.status),
                    it.eventStartAt,
                    it.eventEndAt,
                ) != EventStatus.ENDED
            }
            .minWithOrNull(compareBy(nullsLast()) { event: OrganizerMvpEvent -> event.eventStartAt })

    /**
     * One event as a `list_row_date` (§5.5): neutral date block, title, time + venue, one status
     * badge. The row is the target; there is no card and no progress bar.
     */
    private fun eventRow(
        event: OrganizerMvpEvent,
        showDivider: Boolean,
        onClick: () -> Unit,
    ): View {
        val row = layoutInflater.inflate(R.layout.list_row_date, activeEventsContainer, false)
        val parsedStart = parseEventStartDateTime(event)
        val parsedDate = parsedStart?.toLocalDate() ?: parseEventDateOnly(event)
        row.findViewById<TextView>(R.id.listRowDay).text =
            parsedDate?.format(dayFormatter) ?: EventCardPresenter.UNKNOWN_DAY
        row.findViewById<TextView>(R.id.listRowMonth).text =
            parsedDate?.format(monthFormatter)?.uppercase(Locale.ENGLISH) ?: EventCardPresenter.UNKNOWN_MONTH
        val time = parsedStart?.format(timeFormatter) ?: EventCardPresenter.UNKNOWN_TIME
        val location = event.venue.takeIf { it.isNotBlank() && it != "Venue not set" }
            ?: EventCardPresenter.UNKNOWN_LOCATION
        val status = event.lifecycleStatus()
        ListRowBinder.bind(
            row,
            title = event.title.ifBlank { getString(R.string.common_untitled_event) },
            meta = listOf(time, location)
                .filter { it != EventCardPresenter.UNKNOWN_TIME && it != EventCardPresenter.UNKNOWN_LOCATION }
                .joinToString(" - ")
                .ifBlank { getString(R.string.ui_organizer_none_scheduled) },
            trailing = ListRowBinder.Trailing.BADGE,
            trailingText = status,
            showDivider = showDivider,
            onClick = onClick,
        )

        val badge = row.findViewById<TextView>(R.id.listRowBadge)
        val (bg, fg) = when (status) {
            "Active" -> R.color.ui_accent to R.color.ui_on_accent
            "Upcoming" -> R.color.ui_info_container to R.color.ui_on_info_container
            "Draft", "Pending Review" -> R.color.ui_warning_container to R.color.ui_on_warning_container
            "Cancelled", "Rejected" -> R.color.ui_error_container to R.color.ui_on_error_container
            else -> R.color.ui_surface_sunken to R.color.ui_text_secondary
        }
        badge.backgroundTintList = android.content.res.ColorStateList.valueOf(color(bg))
        badge.setTextColor(ContextCompat.getColor(this, fg))
        return row
    }

    private fun stopSwipeRefresh() {
        if (isSwipeRefreshing) {
            swipeRefreshLayout.isRefreshing = false
            isSwipeRefreshing = false
        }
    }

    // ── Date parsing (the MVP DTO carries formatted strings) ─────────────────

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
        return runCatching { java.time.Instant.parse(normalized).atZone(organizerZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(normalized).atZoneSameInstant(organizerZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(normalized).withZoneSameInstant(organizerZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("d MMM yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) }.getOrNull()
    }

    private fun parseDateValue(value: String): LocalDate? {
        val normalized = value.replace("•", "").replace("  ", " ").trim()
        return runCatching { LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            ?: runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) }.getOrNull()
            ?: parseDateTimeValue(normalized)?.toLocalDate()
    }
}
