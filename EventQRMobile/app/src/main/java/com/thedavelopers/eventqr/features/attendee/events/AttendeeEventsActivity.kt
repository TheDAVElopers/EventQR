package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import java.time.Instant

open class AttendeeEventsActivity : AppCompatActivity(), EventsContract.View {
    private lateinit var presenter: EventsPresenter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var loadingView: TextView
    private lateinit var retryButton: Button
    private lateinit var allTab: TextView
    private lateinit var upcomingTab: TextView
    private lateinit var pastTab: TextView
    private lateinit var adapter: AttendeeEventAdapter
    private var allEvents: List<AttendeeEventResponse> = emptyList()
    private var selectedFilter: EventFilter = EventFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_events)
        configureAttendeeBottomNav(AttendeeBottomNavItem.EVENTS)

        presenter = EventsPresenter(this, AttendeeRepository(this))
        recyclerView = findViewById(R.id.recyclerEvents)
        emptyView = findViewById(R.id.txtEventsEmpty)
        loadingView = findViewById(R.id.txtEventsLoading)
        retryButton = findViewById(R.id.btnRefreshEvents)
        allTab = findViewById(R.id.tabEventsAll)
        upcomingTab = findViewById(R.id.tabEventsUpcoming)
        pastTab = findViewById(R.id.tabEventsPast)
        adapter = AttendeeEventAdapter { event -> openEventDetail(event) }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        retryButton.setOnClickListener { presenter.loadEvents() }
        allTab.setOnClickListener { selectFilter(EventFilter.ALL) }
        upcomingTab.setOnClickListener { selectFilter(EventFilter.UPCOMING) }
        pastTab.setOnClickListener { selectFilter(EventFilter.PAST) }
        updateTabs()
        presenter.loadEvents()
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
        loadingView.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            emptyView.visibility = View.GONE
            retryButton.visibility = View.GONE
            recyclerView.visibility = View.GONE
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showEvents(items: List<AttendeeEventResponse>) {
        allEvents = items
        retryButton.visibility = View.GONE
        renderFilteredEvents()
    }

    override fun showError(message: String) {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        emptyView.text = when (selectedFilter) {
            EventFilter.ALL -> "No events available yet."
            EventFilter.UPCOMING -> "No upcoming events yet."
            EventFilter.PAST -> "No past events yet."
        }
        retryButton.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun selectFilter(filter: EventFilter) {
        selectedFilter = filter
        updateTabs()
        renderFilteredEvents()
    }

    private fun updateTabs() {
        allTab.setBackgroundResource(if (selectedFilter == EventFilter.ALL) R.drawable.bg_segment_selected else 0)
        upcomingTab.setBackgroundResource(if (selectedFilter == EventFilter.UPCOMING) R.drawable.bg_segment_selected else 0)
        pastTab.setBackgroundResource(if (selectedFilter == EventFilter.PAST) R.drawable.bg_segment_selected else 0)
    }

    private fun renderFilteredEvents() {
        val filtered = when (selectedFilter) {
            EventFilter.ALL -> sortAll(allEvents)
            EventFilter.UPCOMING -> allEvents.filter { isUpcomingOrOngoingEvent(it) }.sortedWith(compareBy(nullsLast()) { it.eventStartAt })
            EventFilter.PAST -> allEvents.filter { isPastEvent(it) }.sortedWith(compareByDescending<AttendeeEventResponse> { it.eventStartAt ?: it.eventEndAt ?: Instant.EPOCH })
        }
        adapter.submitItems(filtered)
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = when (selectedFilter) {
            EventFilter.ALL -> "No events available yet."
            EventFilter.UPCOMING -> "No upcoming events yet."
            EventFilter.PAST -> "No past events yet."
        }
    }

    private fun sortAll(items: List<AttendeeEventResponse>): List<AttendeeEventResponse> {
        val upcoming = items.filter { isUpcomingOrOngoingEvent(it) }.sortedWith(compareBy(nullsLast()) { it.eventStartAt })
        val scheduled = items.filter { !isUpcomingOrOngoingEvent(it) && !isPastEvent(it) }.sortedWith(compareBy(nullsLast()) { it.eventStartAt })
        val past = items.filter { isPastEvent(it) }.sortedWith(compareByDescending<AttendeeEventResponse> { it.eventStartAt ?: it.eventEndAt ?: Instant.EPOCH })
        return upcoming + scheduled + past
    }

    private fun isPastEvent(item: AttendeeEventResponse): Boolean {
        val now = Instant.now()
        return item.eventEndAt?.isBefore(now) == true
    }

    private fun isUpcomingOrOngoingEvent(item: AttendeeEventResponse): Boolean {
        val now = Instant.now()
        return item.eventStartAt?.isAfter(now) == true ||
            (item.eventStartAt != null && item.eventEndAt != null &&
                !item.eventStartAt.isAfter(now) && !item.eventEndAt.isBefore(now))
    }
}
