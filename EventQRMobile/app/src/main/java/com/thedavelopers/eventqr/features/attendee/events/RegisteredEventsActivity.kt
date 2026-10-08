package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.registrations.RegisteredEventAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import java.time.Instant

open class RegisteredEventsActivity : AppCompatActivity(), RegisteredEventsContract.View {
    private lateinit var presenter: RegisteredEventsPresenter
    private lateinit var adapter: RegisteredEventAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var chipAll: Chip
    private lateinit var chipUpcoming: Chip
    private lateinit var chipActive: Chip
    private lateinit var chipCompleted: Chip

    private lateinit var emptyState: EventQrEmptyState
    private lateinit var defaultEmptyTitle: CharSequence

    private var allItems: List<RegistrationResponse> = emptyList()
    private var selectedFilter: RegisteredEventFilter = RegisteredEventFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_registered_events)
        applyEventQrSystemBarAppearance()
        configureAttendeeBottomNav(AttendeeBottomNavItem.REGISTERED)

        presenter = RegisteredEventsPresenter(this, AttendeeRepository(this))
        swipeRefresh = findViewById(R.id.swipeRefreshRegisteredEvents)
        skeletonLoading = findViewById(R.id.skeletonLoading)

        emptyState = findViewById(R.id.txtRegisteredEventsEmpty)
        defaultEmptyTitle = emptyState.text ?: ""
        chipAll = findViewById(R.id.chipAll)
        chipUpcoming = findViewById(R.id.chipUpcoming)
        chipActive = findViewById(R.id.chipActive)
        chipCompleted = findViewById(R.id.chipCompleted)

        chipAll.setOnClickListener { selectFilter(RegisteredEventFilter.ALL) }
        chipUpcoming.setOnClickListener { selectFilter(RegisteredEventFilter.UPCOMING) }
        chipActive.setOnClickListener { selectFilter(RegisteredEventFilter.ACTIVE) }
        chipCompleted.setOnClickListener { selectFilter(RegisteredEventFilter.COMPLETED) }
        swipeRefresh.setOnRefreshListener { presenter.load() }

        adapter = RegisteredEventAdapter()
        findViewById<RecyclerView>(R.id.recyclerRegisteredEvents).apply {
            layoutManager = LinearLayoutManager(this@RegisteredEventsActivity)
            adapter = this@RegisteredEventsActivity.adapter
        }

        updateFilterUI()
        presenter.load()
    }

    private fun selectFilter(filter: RegisteredEventFilter) {
        selectedFilter = filter
        updateFilterUI()
        renderFilteredEvents()
    }

    private fun updateFilterUI() {
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)

        val chips = mapOf(
            RegisteredEventFilter.ALL to chipAll,
            RegisteredEventFilter.UPCOMING to chipUpcoming,
            RegisteredEventFilter.ACTIVE to chipActive,
            RegisteredEventFilter.COMPLETED to chipCompleted,
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

    // Same three states the card badge shows (Upcoming / Active / Completed), derived from the event window.
    private fun isUpcoming(item: RegistrationResponse, now: Instant) = item.eventStartAt?.isAfter(now) == true

    private fun isCompleted(item: RegistrationResponse, now: Instant) = item.eventEndAt?.isBefore(now) == true

    private fun isActive(item: RegistrationResponse, now: Instant) = !isUpcoming(item, now) && !isCompleted(item, now)

    private fun renderFilteredEvents() {
        val now = Instant.now()
        val filtered = when (selectedFilter) {
            RegisteredEventFilter.ALL -> {
                val ongoing = allItems.filter { isActive(it, now) }.sortedBy { it.eventStartAt }
                val upcoming = allItems.filter { isUpcoming(it, now) }.sortedBy { it.eventStartAt }
                val completed = allItems.filter { isCompleted(it, now) }.sortedByDescending { it.eventEndAt }
                ongoing + upcoming + completed
            }
            RegisteredEventFilter.UPCOMING -> allItems.filter { isUpcoming(it, now) }.sortedBy { it.eventStartAt }
            RegisteredEventFilter.ACTIVE -> allItems.filter { isActive(it, now) }.sortedBy { it.eventStartAt }
            RegisteredEventFilter.COMPLETED -> allItems.filter { isCompleted(it, now) }.sortedByDescending { it.eventEndAt }
        }
        adapter.submitItems(filtered)
        // A filter with no matches must not claim the user has no registrations at all. The empty state hides its
        // subtitle automatically whenever the title differs from the layout default.
        emptyState.text = when (selectedFilter) {
            RegisteredEventFilter.ALL -> defaultEmptyTitle
            RegisteredEventFilter.UPCOMING -> getString(R.string.registered_events_none_upcoming)
            RegisteredEventFilter.ACTIVE -> getString(R.string.registered_events_none_active)
            RegisteredEventFilter.COMPLETED -> getString(R.string.registered_events_none_completed)
        }
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        chipAll.visibility = View.VISIBLE
        chipUpcoming.visibility = View.VISIBLE
        chipActive.visibility = View.VISIBLE
        chipCompleted.visibility = View.VISIBLE

        chipAll.text = getString(R.string.common_all)
        chipUpcoming.text = getString(R.string.common_upcoming)
        chipActive.text = getString(R.string.common_active)
        chipCompleted.text = getString(R.string.common_completed)
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        findViewById<RecyclerView>(R.id.recyclerRegisteredEvents).visibility = if (isLoading) View.GONE else View.VISIBLE
        if (!isLoading) {
            swipeRefresh.isRefreshing = false
        }
    }

    override fun showMessage(message: String) {
        swipeRefresh.isRefreshing = false
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showRegisteredEvents(items: List<RegistrationResponse>) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        allItems = items
        renderFilteredEvents()
    }
}
