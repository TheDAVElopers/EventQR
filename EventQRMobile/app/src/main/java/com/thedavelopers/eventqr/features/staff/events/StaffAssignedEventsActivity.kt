package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import android.content.Intent
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
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import java.time.Instant
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class StaffAssignedEventsActivity : AppCompatActivity() {
    private lateinit var repository: StaffRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StaffAssignedEventAdapter
    private lateinit var emptyState: EventQrEmptyState
    private lateinit var skeletonLoading: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var chipAll: Chip
    private lateinit var chipUpcoming: Chip
    private lateinit var chipActive: Chip
    private lateinit var chipCompleted: Chip

    private var isFirstResume = true
    private var selectedFilter: StaffEventFilter = StaffEventFilter.ALL
    private var allEvents: List<StaffAssignedEventResponse> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, this.getString(R.string.staff_dashboard_access_denied_staff_or_above), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_assigned_events)
        applyEventQrSystemBarAppearance()
        repository = StaffRepository(this)

        recyclerView = findViewById(R.id.recyclerAssignedEvents)
        emptyState = findViewById(R.id.txtAssignedEventsEmpty)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        swipeRefresh = findViewById(R.id.swipeRefreshAssignedEvents)
        swipeRefresh.setOnRefreshListener { loadEvents(showLoading = false) }

        chipAll = findViewById(R.id.chipAll)
        chipUpcoming = findViewById(R.id.chipUpcoming)
        chipActive = findViewById(R.id.chipActive)
        chipCompleted = findViewById(R.id.chipCompleted)

        chipAll.setOnClickListener { selectFilter(StaffEventFilter.ALL) }
        chipUpcoming.setOnClickListener { selectFilter(StaffEventFilter.UPCOMING) }
        chipActive.setOnClickListener { selectFilter(StaffEventFilter.ACTIVE) }
        chipCompleted.setOnClickListener { selectFilter(StaffEventFilter.COMPLETED) }

        adapter = StaffAssignedEventAdapter(
            onScanClick = { event -> openScanner(event) },
            onAttendeesClick = { event -> openAttendees(event) },
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupBottomNav()
        loadEvents()
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        loadEvents()
    }

    private fun setupBottomNav() {
        configureStaffBottomNav(StaffBottomNavItem.EVENTS)
    }

    private fun selectFilter(filter: StaffEventFilter) {
        selectedFilter = filter
        updateFilterUI()
        renderFilteredEvents()
    }

    private fun updateFilterUI() {
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)

        val chips = mapOf(
            StaffEventFilter.ALL to chipAll,
            StaffEventFilter.UPCOMING to chipUpcoming,
            StaffEventFilter.ACTIVE to chipActive,
            StaffEventFilter.COMPLETED to chipCompleted,
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

    private fun loadEvents(showLoading: Boolean = true) {
        if (showLoading && !swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = View.VISIBLE
        }
        MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> {
                    allEvents = result.data
                    renderFilteredEvents()
                }
                is NetworkResult.Error -> {
                    allEvents = emptyList()
                    adapter.submitItems(emptyList())
                    emptyState.text = result.message
                    emptyState.visibility = View.VISIBLE
                    skeletonLoading.visibility = View.GONE
                    Toast.makeText(this@StaffAssignedEventsActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                NetworkResult.Loading -> Unit
            }
            swipeRefresh.isRefreshing = false
        }
    }

    private fun renderFilteredEvents() {
        skeletonLoading.visibility = View.GONE
        val now = Instant.now()
        val active = allEvents.filter {
            it.eventStartAt?.isBefore(now) == true && it.eventEndAt?.isBefore(now) != true
        }.sortedBy { it.eventStartAt }
        val upcoming = allEvents.filter {
            it.eventStartAt?.isAfter(now) == true
        }.sortedBy { it.eventStartAt }
        val completed = allEvents.filter {
            it.eventEndAt?.isBefore(now) == true
        }.sortedByDescending { it.eventEndAt ?: it.eventStartAt }

        val ordered = active + upcoming + completed

        val filtered = when (selectedFilter) {
            StaffEventFilter.ALL -> ordered
            StaffEventFilter.ACTIVE -> active
            StaffEventFilter.UPCOMING -> upcoming
            StaffEventFilter.COMPLETED -> completed
        }

        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        adapter.submitItems(filtered)
    }

    private fun openScanner(event: StaffAssignedEventResponse) {
        startActivity(Intent(this, ScannerActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, event.eventId.toString())
        })
    }

    private fun openAttendees(event: StaffAssignedEventResponse) {
        startActivity(Intent(this, EventRegistrationsActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, event.eventId.toString())
        })
    }

    private enum class StaffEventFilter { ALL, UPCOMING, ACTIVE, COMPLETED }
}
