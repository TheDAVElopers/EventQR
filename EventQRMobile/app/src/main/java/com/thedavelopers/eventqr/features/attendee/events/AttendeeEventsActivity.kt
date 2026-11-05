package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.ui.components.CountLabel
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import java.time.Instant

/**
 * Discover (§6.2). Compact pass head + 48dp search + a chip row; the list is EventCard rows on the
 * flat page. The old radial-glow header, TextInputLayout search box and pill chips are gone.
 */
open class AttendeeEventsActivity : AppCompatActivity(), EventsContract.View {
    @VisibleForTesting
    internal lateinit var presenter: EventsPresenter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: EventQrEmptyState
    private lateinit var skeletonLoading: View
    private lateinit var chipAll: TextView
    private lateinit var chipUpcoming: TextView
    private lateinit var chipActive: TextView
    private lateinit var chipCompleted: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var countView: TextView
    private lateinit var adapter: AttendeeEventAdapter
    private var allEvents: List<AttendeeEventResponse> = emptyList()
    private var selectedFilter: EventFilter = EventFilter.ALL
    private var isFirstResume = true
    private var searchQuery: String = ""

    enum class EventFilter { ALL, UPCOMING, ACTIVE, COMPLETED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_events)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        configureAttendeeBottomNav(AttendeeBottomNavItem.EVENTS)

        presenter = EventsPresenter(this, AttendeeRepository(this))
        swipeRefresh = findViewById(R.id.swipeRefreshEvents)
        recyclerView = findViewById(R.id.recyclerEvents)
        emptyState = findViewById(R.id.txtEventsEmpty)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        chipAll = findViewById(R.id.chipAll)
        chipUpcoming = findViewById(R.id.chipUpcoming)
        chipActive = findViewById(R.id.chipActive)
        chipCompleted = findViewById(R.id.chipCompleted)
        adapter = AttendeeEventAdapter { event -> openEventDetail(event) }

        countView = findViewById<TextView>(R.id.passHeadTitleCount).apply { visibility = View.VISIBLE }
        PassHeadBinder.bindCompact(
            findViewById(R.id.passHead),
            title = getString(R.string.ui_attendee_discover),
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { presenter.loadEvents() }
        chipAll.setOnClickListener { selectFilter(EventFilter.ALL) }
        chipUpcoming.setOnClickListener { selectFilter(EventFilter.UPCOMING) }
        chipActive.setOnClickListener { selectFilter(EventFilter.ACTIVE) }
        chipCompleted.setOnClickListener { selectFilter(EventFilter.COMPLETED) }
        updateTabs()
        presenter.loadEvents()

        findViewById<android.widget.EditText>(R.id.inputEventSearch)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString().orEmpty()
                    renderFilteredEvents()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
    }

    private fun getFilteredByStatus(): List<AttendeeEventResponse> {
        return when (selectedFilter) {
            EventFilter.ALL -> sortAll(allEvents)
            EventFilter.UPCOMING -> allEvents.filter { it.eventStartAt?.isAfter(Instant.now()) == true }.sortedBy { it.eventStartAt }
            EventFilter.ACTIVE -> allEvents.filter { isOngoingEvent(it) }.sortedBy { it.eventStartAt }
            EventFilter.COMPLETED -> allEvents.filter { isPastEvent(it) }.sortedByDescending { it.eventEndAt }
        }
    }

    private fun isOngoingEvent(item: AttendeeEventResponse): Boolean {
        val now = Instant.now()
        return item.eventStartAt != null &&
                !item.eventStartAt.isAfter(now) &&
                item.eventEndAt?.isBefore(now) != true
    }

    override fun onResume() {
        super.onResume()
        // onCreate already loads; later resumes (e.g. back from an event's detail after registering or
        // cancelling) refresh the cards so their registered counts are current.
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        presenter.loadEvents(silent = true)
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    private fun openEventDetail(event: AttendeeEventResponse) {
        startActivity(
            Intent(this, EventDetailActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, event.eventId.toString())
                .putExtra(EXTRA_EVENT_TITLE, event.title)
                .putExtra(EXTRA_EVENT_LOCATION, event.location ?: "")
                .putExtra(EXTRA_EVENT_DESCRIPTION, event.description ?: "")
                .putExtra(EXTRA_EVENT_CATEGORY, event.category ?: "")
                .putExtra(EXTRA_EVENT_START, DateFormatters.formatInstant(event.eventStartAt))
                .putExtra(EXTRA_EVENT_END, DateFormatters.formatInstant(event.eventEndAt))
                .putExtra(EXTRA_EVENT_STATUS, computedStatusLabel(event))
                .putExtra(EXTRA_EVENT_CAPACITY, event.capacity.toString())
                .putExtra(EXTRA_EVENT_COUNT, event.currentAttendeeCount.toString())
        )
    }

    override fun showLoading(isLoading: Boolean) {
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        if (isLoading) {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else {
            swipeRefresh.isRefreshing = false
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showEvents(items: List<AttendeeEventResponse>) {
        swipeRefresh.isRefreshing = false
        allEvents = items
        renderFilteredEvents()
    }

    override fun showError(message: String) {
        swipeRefresh.isRefreshing = false
        recyclerView.visibility = View.GONE
        skeletonLoading.visibility = View.GONE
        countView.text = CountLabel.format(0L, getString(R.string.ui_attendee_events_count))
        emptyState.setIcon(AppIcons.Event)
        emptyState.show(
            getString(R.string.ui_attendee_no_events_found),
            getString(R.string.ui_attendee_no_events_body),
        )
        emptyState.setAction(getString(R.string.ui_attendee_retry)) { presenter.loadEvents() }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun selectFilter(filter: EventFilter) {
        selectedFilter = filter
        updateTabs()
        renderFilteredEvents()
    }

    private fun updateTabs() {
        val chips = mapOf(
            EventFilter.ALL to chipAll,
            EventFilter.UPCOMING to chipUpcoming,
            EventFilter.ACTIVE to chipActive,
            EventFilter.COMPLETED to chipCompleted,
        )
        for ((filter, chip) in chips) {
            val isSelected = filter == selectedFilter
            chip.isSelected = isSelected
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.ui_on_primary else R.color.ui_text_primary,
                ),
            )
        }
    }

    /** Status chip and search text both apply, so neither a refresh nor a chip tap drops the typed query. */
    private fun renderFilteredEvents() {
        val byStatus = getFilteredByStatus()
        val filtered = if (searchQuery.isBlank()) {
            byStatus
        } else {
            byStatus.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        adapter.submitItems(filtered)
        countView.text = CountLabel.format(filtered.size.toLong(), getString(R.string.ui_attendee_events_count))
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        showEmptyState(filtered.isEmpty())
    }

    private fun showEmptyState(visible: Boolean) {
        if (!visible) {
            emptyState.visibility = View.GONE
            return
        }
        val title: Int
        val body: Int
        when (selectedFilter) {
            EventFilter.ALL -> {
                title = R.string.attendee_events_no_events_available_yet
                body = R.string.ui_attendee_discover_empty_body
            }
            EventFilter.UPCOMING -> {
                title = R.string.attendee_events_no_upcoming_events
                body = R.string.ui_attendee_discover_empty_body
            }
            EventFilter.ACTIVE -> {
                title = R.string.organizer_dashboard_no_active_events
                body = R.string.ui_attendee_discover_empty_body
            }
            EventFilter.COMPLETED -> {
                title = R.string.attendee_events_no_completed_events
                body = R.string.ui_attendee_discover_empty_body
            }
        }
        emptyState.setIcon(AppIcons.Event)
        emptyState.show(getString(title), getString(body))
        emptyState.setAction(null)
        emptyState.visibility = View.VISIBLE
    }

    private fun sortAll(items: List<AttendeeEventResponse>): List<AttendeeEventResponse> {
        val ongoing = items.filter { isOngoingEvent(it) }.sortedBy { it.eventStartAt }
        val upcoming = items.filter { it.eventStartAt?.isAfter(Instant.now()) == true }.sortedBy { it.eventStartAt }
        val completed = items.filter { isPastEvent(it) }.sortedByDescending { it.eventEndAt }
        val bucketed = (ongoing + upcoming + completed).toSet()
        // Events with null start/end dates fall in no bucket; keep them at the end instead of dropping them.
        val undated = items.filter { it !in bucketed }
        return ongoing + upcoming + completed + undated
    }

    private fun isPastEvent(item: AttendeeEventResponse): Boolean {
        val now = Instant.now()
        return item.eventEndAt?.isBefore(now) == true
    }
}
