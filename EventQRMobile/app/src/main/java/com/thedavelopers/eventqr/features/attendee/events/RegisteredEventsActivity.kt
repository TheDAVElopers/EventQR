package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.registrations.RegisteredEventAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import java.time.Instant

open class RegisteredEventsActivity : AppCompatActivity(), RegisteredEventsContract.View {
    private lateinit var presenter: RegisteredEventsPresenter
    private lateinit var adapter: RegisteredEventAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var chipRow: View
    private lateinit var chipAll: TextView
    private lateinit var chipUpcoming: TextView
    private lateinit var chipActive: TextView
    private lateinit var chipCompleted: TextView

    private lateinit var emptyState: EventQrEmptyState
    private lateinit var defaultEmptyTitle: CharSequence

    private var allItems: List<RegistrationResponse> = emptyList()
    private var selectedFilter: RegisteredEventFilter = RegisteredEventFilter.ALL
    // A zero-row load failure owns the empty-state slot (error + Retry); until a load succeeds the
    // filters must not replace it with their empty copy, which carries no way back.
    private var loadFailed = false
    private var isFirstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_registered_events)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        configureAttendeeBottomNav(AttendeeBottomNavItem.REGISTERED)

        PassHeadBinder.bindCompact(
            findViewById(R.id.passHead),
            title = getString(R.string.ui_attendee_my_passes),
        )

        presenter = RegisteredEventsPresenter(this, AttendeeRepository(this))
        swipeRefresh = findViewById(R.id.swipeRefreshRegisteredEvents)
        skeletonLoading = findViewById(R.id.skeletonLoading)

        emptyState = findViewById(R.id.txtRegisteredEventsEmpty)
        emptyState.setIcon(AppIcons.Pass)
        emptyState.setContent(
            getString(R.string.common_no_registered_events_yet),
            getString(R.string.user_registered_events_events_you_register_for_will_show_up),
        )
        defaultEmptyTitle = getString(R.string.common_no_registered_events_yet)
        chipAll = findViewById(R.id.chipAll)
        chipUpcoming = findViewById(R.id.chipUpcoming)
        chipActive = findViewById(R.id.chipActive)
        chipCompleted = findViewById(R.id.chipCompleted)
        chipRow = findViewById(R.id.chipRowRegisteredEvents)

        chipAll.setOnClickListener { selectFilter(RegisteredEventFilter.ALL) }
        chipUpcoming.setOnClickListener { selectFilter(RegisteredEventFilter.UPCOMING) }
        chipActive.setOnClickListener { selectFilter(RegisteredEventFilter.ACTIVE) }
        chipCompleted.setOnClickListener { selectFilter(RegisteredEventFilter.COMPLETED) }
        swipeRefresh.setOnRefreshListener { presenter.load() }

        adapter = RegisteredEventAdapter { registration -> openEventDetail(registration) }
        recyclerView = findViewById(R.id.recyclerRegisteredEvents)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RegisteredEventsActivity)
            adapter = this@RegisteredEventsActivity.adapter
        }

        updateFilterUI()
        presenter.load()
    }

    override fun onResume() {
        super.onResume()
        // The nav reorders this instance to the front instead of recreating it, so a pass booked
        // since the last visit only appears if the data is reloaded here. Existing rows stay on
        // screen while it runs (showLoading keeps them).
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        presenter.load()
    }

    private fun selectFilter(filter: RegisteredEventFilter) {
        // Keep the error state and its Retry: the only rows a filter could draw are none.
        if (loadFailed && allItems.isEmpty()) return
        selectedFilter = filter
        updateFilterUI()
        renderFilteredEvents()
    }

    private fun updateFilterUI() {
        val chips = mapOf(
            RegisteredEventFilter.ALL to chipAll,
            RegisteredEventFilter.UPCOMING to chipUpcoming,
            RegisteredEventFilter.ACTIVE to chipActive,
            RegisteredEventFilter.COMPLETED to chipCompleted,
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

    private fun openEventDetail(registration: RegistrationResponse) {
        startActivity(
            Intent(this, EventDetailActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, registration.eventId.toString())
                .putExtra(
                    EXTRA_EVENT_TITLE,
                    registration.eventTitle?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.common_untitled_event),
                ),
        )
    }

    // Same three states the row badge shows (Upcoming / Active / Completed), derived from the event window.
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
        // A successful load restores the pass empty state, so a failed-load variant never survives it.
        emptyState.setIcon(AppIcons.Pass)
        emptyState.setAction(null)
        emptyState.subtext = getString(R.string.user_registered_events_events_you_register_for_will_show_up)
        // A filter with no matches must not claim the user has no registrations at all. The empty state hides its
        // subtitle automatically whenever the title differs from the layout default.
        emptyState.text = when (selectedFilter) {
            RegisteredEventFilter.ALL -> defaultEmptyTitle
            RegisteredEventFilter.UPCOMING -> getString(R.string.registered_events_none_upcoming)
            RegisteredEventFilter.ACTIVE -> getString(R.string.registered_events_none_active)
            RegisteredEventFilter.COMPLETED -> getString(R.string.registered_events_none_completed)
        }
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE

        // No passes at all: the filters have nothing to filter. A filter that merely matches
        // nothing keeps the row so the user can switch back.
        chipRow.visibility = if (allItems.isEmpty()) View.GONE else View.VISIBLE
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
        val hasRows = adapter.itemCount > 0
        if (!swipeRefresh.isRefreshing) {
            // A refresh over readable rows keeps them on screen; the skeleton is a first-load state.
            skeletonLoading.visibility = if (isLoading && !hasRows) View.VISIBLE else View.GONE
        }
        if (isLoading) {
            emptyState.visibility = View.GONE
            if (!hasRows) recyclerView.visibility = View.GONE
        } else {
            swipeRefresh.isRefreshing = false
        }
    }

    override fun showMessage(message: String) {
        swipeRefresh.isRefreshing = false
        // A zero-row failure must not leave a blank list area under a transient toast: the
        // empty-state slot carries the error and its Retry (§5.10 item 6, delivery invariant 2).
        // With rows on screen the previous list stays readable and the toast is the only notice.
        if (allItems.isEmpty()) {
            loadFailed = true
            emptyState.setIcon(AppIcons.Warning)
            emptyState.show(
                getString(R.string.ui_attendee_registrations_error_title),
                getString(R.string.ui_attendee_load_error_body),
            )
            emptyState.setAction(getString(R.string.ui_attendee_retry)) { presenter.load() }
            recyclerView.visibility = View.GONE
            chipRow.visibility = View.GONE
        } else {
            // A refresh over readable rows keeps them; the failure is a background notice.
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun showRegisteredEvents(items: List<RegistrationResponse>) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        loadFailed = false
        allItems = items
        renderFilteredEvents()
    }
}
