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
    private lateinit var chipRegistered: Chip
    private lateinit var chipCompleted: Chip

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

        chipAll = findViewById(R.id.chipAll)
        chipRegistered = findViewById(R.id.chipRegistered)
        chipCompleted = findViewById(R.id.chipCompleted)

        chipAll.setOnClickListener { selectFilter(RegisteredEventFilter.ALL) }
        chipRegistered.setOnClickListener { selectFilter(RegisteredEventFilter.REGISTERED) }
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
            RegisteredEventFilter.REGISTERED to chipRegistered,
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

    private fun renderFilteredEvents() {
        val now = Instant.now()
        val filtered = when (selectedFilter) {
            RegisteredEventFilter.ALL -> {
                val ongoing = allItems
                    .filter { it.eventStartAt?.isAfter(now) != true && it.eventEndAt?.isBefore(now) != true }
                    .sortedBy { it.eventStartAt }
                val upcoming = allItems.filter { it.eventStartAt?.isAfter(now) == true }.sortedBy { it.eventStartAt }
                val completed = allItems.filter { it.eventEndAt?.isBefore(now) == true }.sortedByDescending { it.eventEndAt }
                ongoing + upcoming + completed
            }
            RegisteredEventFilter.REGISTERED -> allItems.filter { it.eventEndAt?.isBefore(now) != true }
            RegisteredEventFilter.COMPLETED -> allItems.filter { it.eventEndAt?.isBefore(now) == true }
        }
        adapter.submitItems(filtered)
        findViewById<View>(R.id.txtRegisteredEventsEmpty).visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        chipAll.visibility = View.VISIBLE
        chipRegistered.visibility = View.VISIBLE
        chipCompleted.visibility = View.VISIBLE

        chipAll.text = "All"
        chipRegistered.text = "Registered"
        chipCompleted.text = "Completed"
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
