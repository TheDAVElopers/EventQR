package com.thedavelopers.eventqr.features.organizer.attendees

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.AttendeeManagementAdapter
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.organizer.NAV_ATTENDEES
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpAttendee
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.bottomNav
import com.thedavelopers.eventqr.features.organizer.formatOrganizerEventDate
import com.thedavelopers.eventqr.features.organizer.checkedInTotal
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.registeredTotal
import com.thedavelopers.eventqr.features.organizer.resolveSelectedEvent
import com.thedavelopers.eventqr.features.organizer.saveSelectedEventId
import com.thedavelopers.eventqr.features.organizer.selectedEventId
import com.thedavelopers.eventqr.features.organizer.statusBucket
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.PassHeadModel
import com.thedavelopers.eventqr.ui.components.PassStat
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import com.thedavelopers.eventqr.ui.theme.dimenPx
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Organizer attendees list (§6.4).
 *
 * The pass head carries the three counts (REGISTERED / CHECKED IN / EXITED) - the stat-card strip is
 * gone. The event selector is a 56dp field row. Rows are grouped list rows with one status badge.
 */
open class AttendeeManagementActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private var selectedEvent: OrganizerMvpEvent? = null
    private var eventsLoadFailed = false
    private var eventsLoadJob: kotlinx.coroutines.Job? = null
    private lateinit var adapter: AttendeeManagementAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var emptyState: EventQrEmptyState
    private lateinit var eventSelectorHost: LinearLayout
    private lateinit var currentEventLabel: TextView
    private lateinit var txtEventTitle: TextView
    private lateinit var txtEventSelectorDate: TextView
    private lateinit var passHead: View

    private var attendees: List<OrganizerMvpAttendee> = emptyList()
    private var statRegistered: String? = null
    private var statCheckedIn: String? = null
    private var statExited: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendee_management)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()

        repository = OrganizerRepository(this)

        passHead = findViewById(R.id.passHead)
        txtEventTitle = findViewById(R.id.txtEventTitle)
        txtEventSelectorDate = findViewById(R.id.txtEventSelectorDate)
        swipeRefresh = findViewById(R.id.swipeRefreshAttendeeManagement)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        emptyState = findViewById(R.id.layoutAttendeesEmpty)
        eventSelectorHost = findViewById(R.id.layoutEventSelectorHost)
        currentEventLabel = findViewById(R.id.txtCurrentEventLabel)

        bindPassHead()
        findViewById<LinearLayout>(R.id.layoutBottomNavHost).addView(bottomNav(NAV_ATTENDEES))

        swipeRefresh.setOnRefreshListener {
            // No event yet means the event list failed to load: a pull retries it.
            if (selectedEvent == null) loadEvents() else refreshSelectedEventAttendees()
        }

        findViewById<View>(R.id.btnFilter).setOnClickListener {
            val current = selectedEvent ?: return@setOnClickListener
            startActivity(
                Intent(this@AttendeeManagementActivity, SearchAttendeesActivity::class.java)
                    .putExtra(EXTRA_EVENT_ID, current.id)
                    .putExtra(EXTRA_EVENT_TITLE, current.title),
            )
        }

        emptyState.setAction(getString(R.string.common_retry)) { loadEvents() }

        loadEvents()
    }

    private fun bindPassHead() {
        PassHeadBinder.bind(
            passHead,
            PassHeadModel(
                title = getString(R.string.ui_organizer_attendees),
                eyebrow = getString(R.string.ui_organizer_role),
                stats = listOf(
                    PassStat(
                        statRegistered ?: getString(R.string.ui_organizer_value_unavailable),
                        getString(R.string.ui_organizer_report_registered),
                    ),
                    PassStat(
                        statCheckedIn ?: getString(R.string.ui_organizer_value_unavailable),
                        getString(R.string.ui_organizer_report_checked_in),
                    ),
                    PassStat(
                        statExited ?: getString(R.string.ui_organizer_value_unavailable),
                        getString(R.string.ui_organizer_report_exited),
                    ),
                ),
            ),
        )
    }

    private fun loadEvents() {
        // A pull during the first load (or a second pull) must not start a parallel events load.
        if (eventsLoadJob?.isActive == true) return
        eventsLoadJob = lifecycleScope.launch {
            val eventsLoad = repository.loadApprovedOrganizerEvents()
            if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
                showEventsLoadFailedState(eventsLoad.message)
                return@launch
            }
            val events = eventsLoad.data
            restoreAfterEventsLoadFailure()

            if (events.isEmpty()) {
                showNoEventsAvailableState()
                return@launch
            }

            adapter = AttendeeManagementAdapter { attendee -> openDetails(attendee) }
            findViewById<RecyclerView>(R.id.recyclerAttendees).apply {
                layoutManager = LinearLayoutManager(this@AttendeeManagementActivity)
                adapter = this@AttendeeManagementActivity.adapter
            }

            setupEventSelector(events)

            val eventId = intentEventId() ?: selectedEventId().takeIf { it.isNotBlank() }
            val resolvedEvent = resolveSelectedEvent(events, eventId)

            if (resolvedEvent != null) {
                selectedEvent = resolvedEvent
                bindEventHeader()
                loadAttendees()
            } else {
                showNoEventEmptyState()
            }
        }
    }

    private fun formatDateOnly(rawDate: String): String =
        formatOrganizerEventDate(rawDate).ifBlank { "-" }

    private fun setupEventSelector(events: List<OrganizerMvpEvent>) {
        val approvedEvents = events.filter { it.status.equals("APPROVED", ignoreCase = true) || it.status.isBlank() }
        val selectableEvents = approvedEvents.ifEmpty { events }

        eventSelectorHost.isClickable = selectableEvents.isNotEmpty()
        eventSelectorHost.isFocusable = selectableEvents.isNotEmpty()

        eventSelectorHost.setOnClickListener { anchor ->
            if (selectableEvents.isEmpty()) return@setOnClickListener
            showEventDropdownPopup(anchor, selectableEvents)
        }
    }

    /**
     * Dropdown list on the shared card surface: `bg_ui_card`, radius 10, 48dp rows. It is a popup,
     * not a dialog, so the selection stays anchored to the field it edits.
     */
    private fun showEventDropdownPopup(anchor: View, events: List<OrganizerMvpEvent>) {
        var popup: PopupWindow? = null
        val dropdownView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@AttendeeManagementActivity, R.drawable.bg_ui_card)
            setPadding(0, dimenPx(R.dimen.space_8), 0, dimenPx(R.dimen.space_8))

            events.forEach { event ->
                val isSelected = event.id == selectedEvent?.id
                val itemLayout = LinearLayout(this@AttendeeManagementActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dimenPx(R.dimen.ui_touch_min)
                    setPadding(
                        dimenPx(R.dimen.space_16),
                        dimenPx(R.dimen.space_12),
                        dimenPx(R.dimen.space_16),
                        dimenPx(R.dimen.space_12),
                    )
                    setBackgroundColor(
                        ContextCompat.getColor(
                            this@AttendeeManagementActivity,
                            if (isSelected) R.color.ui_surface_pressed else R.color.ui_surface,
                        ),
                    )
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        popup?.dismiss()
                        selectedEvent = event
                        repository.saveSelectedEventId(event.id)
                        saveSelectedEventId(event.id)
                        restoreNormalUi()
                        bindEventHeader()
                        loadAttendees()
                    }
                }

                val textLayout = LinearLayout(this@AttendeeManagementActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                textLayout.addView(
                    TextView(this@AttendeeManagementActivity).apply {
                        text = event.title.ifBlank { getString(R.string.organizer_untitled_event) }
                        setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
                        setTextColor(
                            ContextCompat.getColor(
                                this@AttendeeManagementActivity,
                                if (isSelected) R.color.ui_primary else R.color.ui_text_primary,
                            ),
                        )
                        maxLines = 1
                    },
                )

                val dateFormatted = formatDateOnly(event.shortDate)
                if (dateFormatted.isNotBlank()) {
                    textLayout.addView(
                        TextView(this@AttendeeManagementActivity).apply {
                            text = dateFormatted
                            setTextAppearance(R.style.TextAppearance_EventQR_Type_Micro)
                            setTextColor(ContextCompat.getColor(this@AttendeeManagementActivity, R.color.ui_text_secondary))
                        },
                    )
                }

                itemLayout.addView(textLayout)
                addView(itemLayout)
            }
        }

        popup = PopupWindow(
            dropdownView,
            anchor.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = 0f
            setBackgroundDrawable(null)
        }

        popup.showAsDropDown(anchor, 0, dimenPx(R.dimen.space_4))
    }

    private fun refreshSelectedEventAttendees() {
        val event = selectedEvent ?: return
        lifecycleScope.launch {
            val currentEventId = event.id
            val eventsLoad = repository.loadApprovedOrganizerEvents()
            // A failed fetch must not run resolveSelectedEvent on an empty list: that would save "no selection"
            // and wipe the persisted event. Keep the current event and just reload its attendees.
            val latestSelectedEvent = if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
                null
            } else {
                resolveSelectedEvent(eventsLoad.data, currentEventId)
            }
            if (latestSelectedEvent != null) {
                selectedEvent = latestSelectedEvent
                repository.saveSelectedEventId(latestSelectedEvent.id)
                saveSelectedEventId(latestSelectedEvent.id)
                bindEventHeader()
            }
            loadAttendees()
        }
    }

    private fun loadAttendees() {
        val event = selectedEvent ?: return
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = View.VISIBLE
        }
        lifecycleScope.launch {
            val eventIdAtRequestTime = event.id
            val load = repository.loadAttendeesForMvp(eventIdAtRequestTime)
            if (eventIdAtRequestTime != selectedEvent?.id) {
                swipeRefresh.isRefreshing = false
                skeletonLoading.visibility = View.GONE
                return@launch
            }
            attendees = load.data
            swipeRefresh.isRefreshing = false
            render(load)
        }
    }

    private fun bindEventHeader() {
        val event = selectedEvent ?: return
        txtEventTitle.text = event.title.ifBlank { getString(R.string.organizer_choose_an_event) }
        txtEventSelectorDate.text = formatDateOnly(event.shortDate)
    }

    private fun showNoEventEmptyState() {
        skeletonLoading.visibility = View.GONE
        swipeRefresh.isEnabled = false
        currentEventLabel.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        emptyState.show(
            getString(R.string.attendee_management_no_event_selected),
            getString(R.string.attendee_management_select_an_event_from_the_dropdown_ab),
        )
        txtEventTitle.text = getString(R.string.attendee_management_choose_an_event)
        txtEventSelectorDate.text = "-"
    }

    /** The event list failed to load (not "no events"): say so and let a pull-to-refresh retry. */
    @VisibleForTesting
    internal fun showEventsLoadFailedState(message: String?) {
        eventsLoadFailed = true
        skeletonLoading.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        swipeRefresh.isEnabled = true
        currentEventLabel.visibility = View.GONE
        eventSelectorHost.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        emptyState.show(
            getString(R.string.organizer_dashboard_events_load_failed),
            getString(
                R.string.organizer_events_load_failed_pull_to_retry,
                message?.takeIf { it.isNotBlank() } ?: getString(R.string.organizer_try_again_later),
            ),
        )
    }

    private fun restoreAfterEventsLoadFailure() {
        if (!eventsLoadFailed) return
        eventsLoadFailed = false
        swipeRefresh.isRefreshing = false
        currentEventLabel.visibility = View.VISIBLE
        eventSelectorHost.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun showNoEventsAvailableState() {
        skeletonLoading.visibility = View.GONE
        swipeRefresh.isEnabled = false
        currentEventLabel.visibility = View.GONE
        txtEventSelectorDate.visibility = View.GONE
        eventSelectorHost.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        emptyState.show(
            getString(R.string.attendee_management_no_events_available),
            getString(R.string.attendee_management_create_an_event_in_the_events_tab_to),
        )
    }

    private fun restoreNormalUi() {
        currentEventLabel.visibility = View.VISIBLE
        swipeRefresh.isEnabled = true
        emptyState.visibility = View.GONE
        txtEventSelectorDate.visibility = View.VISIBLE
    }

    private fun render(load: OrganizerMvpLoad<List<OrganizerMvpAttendee>>) {
        skeletonLoading.visibility = View.GONE
        val checkedIn = attendees.checkedInTotal()
        val exited = attendees.count { it.statusBucket() == "Exited" }

        // Total counts only attendees the backend counts as registered (Cancelled / No Show are excluded).
        statRegistered = formatCount(attendees.registeredTotal())
        statCheckedIn = formatCount(checkedIn)
        statExited = formatCount(exited)
        bindPassHead()

        adapter.submitItems(attendees)
        emptyState.visibility = if (attendees.isEmpty()) View.VISIBLE else View.GONE
        when {
            load.source == OrganizerMvpDataSource.ERROR -> {
                emptyState.show(
                    getString(R.string.attendee_management_unable_to_load_attendees),
                    load.message ?: getString(R.string.organizer_try_again_later),
                )
            }
            attendees.isEmpty() -> {
                emptyState.show(
                    getString(R.string.attendee_management_no_attendees_registered_yet),
                    getString(R.string.attendee_management_attendees_will_appear_here_once_they),
                )
                // Nothing failed: the Retry action belongs to the error state only.
                emptyState.setAction(null, primary = false, onClick = null)
            }
        }
    }

    private fun formatCount(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    private fun openDetails(attendee: OrganizerMvpAttendee) {
        val event = selectedEvent ?: return
        startActivity(
            Intent(this, AttendeeDetailsActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, event.id)
                .putExtra(EXTRA_EVENT_TITLE, event.title)
                .putExtra(SearchAttendeesActivity.EXTRA_ATTENDEE_ID, attendee.id),
        )
    }
}
