package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.ui.components.CountLabel
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.asFilterChips
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Assigned events (§6.3). Compact pass head with a count, a chip row of four filters, and the
 * shared EventCard rows. Filter state is the chip fill only - no check icon, no stroke swap.
 */
open class StaffAssignedEventsActivity : AppCompatActivity() {
    private lateinit var repository: StaffRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StaffAssignedEventAdapter
    private lateinit var emptyState: EventQrEmptyState
    private lateinit var skeletonLoading: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var chipAll: TextView
    private lateinit var chipUpcoming: TextView
    private lateinit var chipActive: TextView
    private lateinit var chipCompleted: TextView
    private lateinit var countView: TextView

    private var isFirstResume = true
    private var selectedFilter: StaffEventFilter = StaffEventFilter.ALL
    private var allEvents: List<StaffAssignedEventResponse> = emptyList()
    private var loadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, this.getString(R.string.staff_dashboard_access_denied_staff_or_above), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_assigned_events)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        repository = StaffRepository(this)

        countView = findViewById<TextView>(R.id.passHeadTitleCount).apply { visibility = View.VISIBLE }
        PassHeadBinder.bindCompact(
            findViewById(R.id.passHead),
            title = getString(R.string.ui_staff_assigned_events),
            eyebrow = getString(R.string.ui_staff_role),
        )

        recyclerView = findViewById(R.id.recyclerAssignedEvents)
        emptyState = findViewById(R.id.txtAssignedEventsEmpty)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        swipeRefresh = findViewById(R.id.swipeRefreshAssignedEvents)
        swipeRefresh.setOnRefreshListener {
            repository.invalidateEvents()
            loadEvents(showLoading = false)
        }

        chipAll = findViewById(R.id.chipAll)
        chipUpcoming = findViewById(R.id.chipUpcoming)
        chipActive = findViewById(R.id.chipActive)
        chipCompleted = findViewById(R.id.chipCompleted)
        asFilterChips(chipAll, chipUpcoming, chipActive, chipCompleted)

        chipAll.setOnClickListener { selectFilter(StaffEventFilter.ALL) }
        chipUpcoming.setOnClickListener { selectFilter(StaffEventFilter.UPCOMING) }
        chipActive.setOnClickListener { selectFilter(StaffEventFilter.ACTIVE) }
        chipCompleted.setOnClickListener { selectFilter(StaffEventFilter.COMPLETED) }

        adapter = StaffAssignedEventAdapter(
            onClick = { event -> openAttendees(event) },
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        configureStaffBottomNav(StaffBottomNavItem.EVENTS)
        updateFilterUI()
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

    private fun selectFilter(filter: StaffEventFilter) {
        selectedFilter = filter
        updateFilterUI()
        // The error state belongs to loadEvents; a filter tap must not overwrite it with empty copy.
        if (loadFailed) return
        renderFilteredEvents()
    }

    /** The fill is the state (§5.6): selected = primary, unselected = surface + interactive stroke. */
    private fun updateFilterUI() {
        val chips = mapOf(
            StaffEventFilter.ALL to chipAll,
            StaffEventFilter.UPCOMING to chipUpcoming,
            StaffEventFilter.ACTIVE to chipActive,
            StaffEventFilter.COMPLETED to chipCompleted,
        )
        for ((filter, chip) in chips) {
            chip.isSelected = filter == selectedFilter
            chip.contentDescription = chip.text
        }
    }

    private fun loadEvents(showLoading: Boolean = true) {
        if (showLoading && !swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = View.VISIBLE
        }
        lifecycleScope.launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> {
                    allEvents = result.data
                    loadFailed = false
                    renderFilteredEvents()
                }
                is NetworkResult.Error -> {
                    loadFailed = true
                    allEvents = emptyList()
                    adapter.submitItems(emptyList())
                    emptyState.setIcon(AppIcons.Warning)
                    emptyState.show(
                        getString(R.string.ui_staff_events_error_title),
                        result.message.ifBlank { getString(R.string.ui_staff_load_failed_body) },
                    )
                    emptyState.setAction(getString(R.string.common_retry)) { loadEvents() }
                    emptyState.visibility = View.VISIBLE
                    countView.text = ""
                    skeletonLoading.visibility = View.GONE
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

        countView.text = if (filtered.isEmpty()) {
            ""
        } else {
            CountLabel.format(filtered.size.toLong(), getString(R.string.ui_staff_events_count))
        }
        emptyState.setIcon(AppIcons.Event)
        // Reset the copy too: a previous load failure must not survive a successful render.
        emptyState.setContent(
            getString(R.string.ui_staff_no_assigned_events),
            getString(R.string.ui_staff_no_assigned_events_body),
        )
        emptyState.setAction(null)
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        adapter.submitItems(filtered)
    }

    private fun openAttendees(event: StaffAssignedEventResponse) {
        startActivity(Intent(this, EventRegistrationsActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, event.eventId.toString())
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, event.title)
        })
    }

    private enum class StaffEventFilter { ALL, UPCOMING, ACTIVE, COMPLETED }
}
