package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import java.time.Instant

open class AttendeeEventsActivity : AppCompatActivity(), EventsContract.View {
    private lateinit var presenter: EventsPresenter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var emptySubView: TextView
    private lateinit var emptyIcon: ImageView
    private lateinit var skeletonLoading: View
    private lateinit var retryButton: Button
    private lateinit var chipAll: Chip
    private lateinit var chipUpcoming: Chip
    private lateinit var chipActive: Chip
    private lateinit var chipCompleted: Chip
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: AttendeeEventAdapter
    private var allEvents: List<AttendeeEventResponse> = emptyList()
    private var selectedFilter: EventFilter = EventFilter.ALL

    enum class EventFilter { ALL, UPCOMING, ACTIVE, COMPLETED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_events)
        applyEventQrSystemBarAppearance()
        configureAttendeeBottomNav(AttendeeBottomNavItem.EVENTS)

        presenter = EventsPresenter(this, AttendeeRepository(this))
        swipeRefresh = findViewById(R.id.swipeRefreshEvents)
        recyclerView = findViewById(R.id.recyclerEvents)
        emptyView = findViewById(R.id.txtEventsEmpty)
        emptySubView = findViewById(R.id.txtEventsEmptySub)
        emptyIcon = findViewById(R.id.imgEmptyIcon)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        retryButton = findViewById(R.id.btnRefreshEvents)
        chipAll = findViewById(R.id.chipAll)
        chipUpcoming = findViewById(R.id.chipUpcoming)
        chipActive = findViewById(R.id.chipActive)
        chipCompleted = findViewById(R.id.chipCompleted)
        adapter = AttendeeEventAdapter { event -> openEventDetail(event) }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { presenter.loadEvents() }
        retryButton.setOnClickListener { presenter.loadEvents() }
        chipAll.setOnClickListener { selectFilter(EventFilter.ALL) }
        chipUpcoming.setOnClickListener { selectFilter(EventFilter.UPCOMING) }
        chipActive.setOnClickListener { selectFilter(EventFilter.ACTIVE) }
        chipCompleted.setOnClickListener { selectFilter(EventFilter.COMPLETED) }
        updateTabs()
        presenter.loadEvents()

        findViewById<android.widget.EditText>(R.id.inputEventSearch).addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterBySearch(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun filterBySearch(query: String) {
        val filtered = if (query.isBlank()) {
            getFilteredByStatus()
        } else {
            getFilteredByStatus().filter { it.title.contains(query, ignoreCase = true) }
        }
        adapter.submitItems(filtered)
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        showEmptyState(filtered.isEmpty())
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
            emptyIcon.visibility = View.GONE
            emptyView.visibility = View.GONE
            emptySubView.visibility = View.GONE
            retryButton.visibility = View.GONE
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
        retryButton.visibility = View.GONE
        renderFilteredEvents()
    }

    override fun showError(message: String) {
        swipeRefresh.isRefreshing = false
        recyclerView.visibility = View.GONE
        skeletonLoading.visibility = View.GONE
        showEmptyState(true)
        retryButton.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun selectFilter(filter: EventFilter) {
        selectedFilter = filter
        updateTabs()
        renderFilteredEvents()
    }

    private fun updateTabs() {
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)

        val chips = mapOf(
            EventFilter.ALL to chipAll,
            EventFilter.UPCOMING to chipUpcoming,
            EventFilter.ACTIVE to chipActive,
            EventFilter.COMPLETED to chipCompleted,
        )

        for ((filter, chip) in chips) {
            val isSelected = filter == selectedFilter
            chip.isChecked = isSelected
            chip.setTextColor(if (isSelected) ContextCompat.getColor(this, R.color.brand_on_primary) else textSecondary)
            chip.setChipBackgroundColorResource(if (isSelected) R.color.eventqr_indigo else R.color.surface)
            chip.chipStrokeWidth = if (isSelected) 0f else resources.displayMetrics.density
            chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.outline)
        }
    }

    private fun renderFilteredEvents() {
        val filtered = getFilteredByStatus()
        adapter.submitItems(filtered)
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        showEmptyState(filtered.isEmpty())
    }

    private fun showEmptyState(visible: Boolean) {
        if (!visible) {
            emptyIcon.visibility = View.GONE
            emptyView.visibility = View.GONE
            emptySubView.visibility = View.GONE
            return
        }
        emptyIcon.visibility = View.VISIBLE
        emptyView.visibility = View.VISIBLE
        emptySubView.visibility = View.VISIBLE
        when (selectedFilter) {
            EventFilter.ALL -> {
                emptyView.text = "No events available yet"
                emptySubText = "Check back later for upcoming events"
            }
            EventFilter.UPCOMING -> {
                emptyView.text = "No upcoming events"
                emptySubText = "There are no upcoming events to show"
            }
            EventFilter.ACTIVE -> {
                emptyView.text = "No active events"
                emptySubText = "No events are currently in progress"
            }
            EventFilter.COMPLETED -> {
                emptyView.text = "No completed events"
                emptySubText = "Completed events will appear here"
            }
        }
    }

    private var emptySubText: String
        get() = emptySubView.text.toString()
        set(value) { emptySubView.text = value }

    private fun sortAll(items: List<AttendeeEventResponse>): List<AttendeeEventResponse> {
        val ongoing = items.filter { isOngoingEvent(it) }.sortedBy { it.eventStartAt }
        val upcoming = items.filter { it.eventStartAt?.isAfter(Instant.now()) == true }.sortedBy { it.eventStartAt }
        val completed = items.filter { isPastEvent(it) }.sortedByDescending { it.eventEndAt }
        return ongoing + upcoming + completed
    }

    private fun isPastEvent(item: AttendeeEventResponse): Boolean {
        val now = Instant.now()
        return item.eventEndAt?.isBefore(now) == true
    }
}
