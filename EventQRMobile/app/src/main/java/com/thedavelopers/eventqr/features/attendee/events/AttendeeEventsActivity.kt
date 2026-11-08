package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
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
import java.time.LocalDate
import java.time.YearMonth

/**
 * Discover (§6.2). Compact pass head + 48dp search + an optional date-filter chip + a chip row; the
 * list is EventCard rows on the flat page. The old radial-glow header, TextInputLayout search box
 * and pill chips are gone.
 *
 * Status is required (All by default); the date filter is optional. They AND with the search text,
 * and the sort order is never touched by a filter.
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
    private lateinit var chipDateFilter: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var countView: TextView
    private lateinit var adapter: AttendeeEventAdapter
    private var allEvents: List<AttendeeEventResponse> = emptyList()
    private var selectedFilter: EventFilter = EventFilter.ALL
    private var isFirstResume = true
    private var searchQuery: String = ""

    /**
     * The applied date filter, or null for Any date. Restored across a refresh, rotation, and
     * system-initiated process recreation; not a fresh launch or a force-stop.
     */
    @VisibleForTesting
    internal var dateFilter: DiscoverDateFilter? = null

    /** The open sheet, if any. Its draft is uncommitted until Apply. */
    @VisibleForTesting
    internal var dateSheet: DateFilterSheet? = null
    private var pendingSheetDraft: DateFilterDraft? = null

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
        chipDateFilter = findViewById(R.id.chipDateFilter)
        adapter = AttendeeEventAdapter { event -> openEventDetail(event) }

        reflowSearchRowForFontScale()

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
        chipDateFilter.setOnClickListener { openDateSheet(draftFromApplied()) }
        restoreFilterState(savedInstanceState)
        updateTabs()
        updateDateChip()
        presenter.loadEvents()
        // The sheet survives rotation too: its draft is restored before the first render and the
        // sheet is reopened on top of the reloaded list.
        pendingSheetDraft?.let { draft ->
            pendingSheetDraft = null
            openDateSheet(draft)
        }

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

    /**
     * At `fontScale` 1.3 and above the single row cannot hold both labels: the search field would
     * collapse toward zero behind the date chip. Stack the chip under a full-width field instead
     * (end aligned, one 8dp gap), keeping the plan's single row at normal font scales.
     */
    private fun reflowSearchRowForFontScale() {
        if (resources.configuration.fontScale < SEARCH_ROW_REFLOW_FONT_SCALE) return
        val row = findViewById<LinearLayout>(R.id.searchRow)
        val field = findViewById<EditText>(R.id.inputEventSearch)
        row.orientation = LinearLayout.VERTICAL
        field.layoutParams = (field.layoutParams as LinearLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            weight = 0f
        }
        chipDateFilter.layoutParams = (chipDateFilter.layoutParams as LinearLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            marginStart = 0
            topMargin = resources.getDimensionPixelSize(R.dimen.space_sm)
            gravity = Gravity.END
        }
    }

    private fun statusMatches(item: AttendeeEventResponse): Boolean = when (selectedFilter) {
        EventFilter.ALL -> true
        EventFilter.UPCOMING -> item.eventStartAt?.isAfter(Instant.now()) == true
        EventFilter.ACTIVE -> isOngoingEvent(item)
        EventFilter.COMPLETED -> isPastEvent(item)
    }

    private fun getFilteredByStatus(): List<AttendeeEventResponse> {
        return when (selectedFilter) {
            EventFilter.ALL -> sortAll(allEvents)
            EventFilter.UPCOMING -> allEvents.filter { statusMatches(it) }.sortedBy { it.eventStartAt }
            EventFilter.ACTIVE -> allEvents.filter { statusMatches(it) }.sortedBy { it.eventStartAt }
            EventFilter.COMPLETED -> allEvents.filter { statusMatches(it) }.sortedByDescending { it.eventEndAt }
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
        dateSheet?.dismiss()
        dateSheet = null
        presenter.detach()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FILTER_STATUS, selectedFilter.name)
        when (val filter = dateFilter) {
            is DiscoverDateFilter.MonthOf -> {
                outState.putString(STATE_FILTER_MODE, DateFilterMode.MONTH.name)
                outState.putInt(STATE_FILTER_YEAR, filter.month.year)
                outState.putInt(STATE_FILTER_MONTH, filter.month.monthValue)
            }
            is DiscoverDateFilter.DayOf -> {
                outState.putString(STATE_FILTER_MODE, DateFilterMode.DAY.name)
                outState.putInt(STATE_FILTER_YEAR, filter.day.year)
                outState.putInt(STATE_FILTER_MONTH, filter.day.monthValue)
                outState.putInt(STATE_FILTER_DAY, filter.day.dayOfMonth)
            }
            null -> Unit
        }
        val sheet = dateSheet
        if (sheet != null && sheet.isShowing) {
            outState.putBoolean(STATE_SHEET_OPEN, true)
            val draft = sheet.draft
            outState.putString(STATE_DRAFT_MODE, draft.mode.name)
            outState.putInt(STATE_DRAFT_YEAR, draft.year)
            outState.putInt(STATE_DRAFT_MONTH, draft.month)
            outState.putInt(STATE_DRAFT_DAY, draft.day ?: 0)
        }
    }

    /**
     * Restores the status chip, the applied date filter and, if the sheet was open, its draft.
     * Never seeds a filter.
     */
    private fun restoreFilterState(state: Bundle?) {
        if (state == null) return
        state.getString(STATE_FILTER_STATUS)
            ?.let { name -> runCatching { EventFilter.valueOf(name) }.getOrNull() }
            ?.let { selectedFilter = it }
        val mode = state.getString(STATE_FILTER_MODE)?.toDateFilterMode()
        val year = state.getInt(STATE_FILTER_YEAR)
        val month = state.getInt(STATE_FILTER_MONTH)
        dateFilter = when (mode) {
            DateFilterMode.MONTH ->
                runCatching { DiscoverDateFilter.MonthOf(YearMonth.of(year, month)) }.getOrNull()
            DateFilterMode.DAY ->
                runCatching {
                    DiscoverDateFilter.DayOf(LocalDate.of(year, month, state.getInt(STATE_FILTER_DAY)))
                }.getOrNull()
            else -> null
        }

        if (state.getBoolean(STATE_SHEET_OPEN)) {
            val draftMode = state.getString(STATE_DRAFT_MODE)?.toDateFilterMode() ?: DateFilterMode.ANY
            val draftMonth = state.getInt(STATE_DRAFT_MONTH)
            val draftYear = state.getInt(STATE_DRAFT_YEAR)
            val draftDay = state.getInt(STATE_DRAFT_DAY).takeIf { it > 0 }
            pendingSheetDraft = if (draftMonth in 1..12 && draftYear in 1..9999) {
                DateFilterDraft(draftMode, draftYear, draftMonth, draftDay)
            } else {
                DateFilterDraft.current(draftMode, draftDay)
            }
        }
    }

    private fun String.toDateFilterMode(): DateFilterMode? =
        runCatching { DateFilterMode.valueOf(this) }.getOrNull()

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

    /**
     * Status then date then search. Each is independent and they AND; the sort order comes from
     * [getFilteredByStatus] and is never re-run by a filter. The chip is refreshed here because the
     * filter can also change from the sheet (Apply) rather than a tap.
     *
     * The pipeline runs once: the status rows also prove whether clearing the date would reveal
     * rows, and the date rows prove whether the window itself is what emptied the list.
     */
    private fun renderFilteredEvents() {
        val statusRows = getFilteredByStatus()
        val appliedDate = dateFilter
        val inDateRows = appliedDate
            ?.let { filter -> statusRows.filter { DateFilterRanges.matches(filter, it.eventStartAt) } }
            ?: statusRows
        // Clearing the date is only a fix when the status chip and search match something.
        val clearReveals = statusRows.any { it.title.contains(searchQuery, ignoreCase = true) }
        val filtered = if (searchQuery.isBlank()) {
            inDateRows
        } else {
            inDateRows.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        adapter.submitItems(filtered)
        countView.text = CountLabel.format(filtered.size.toLong(), getString(R.string.ui_attendee_events_count))
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        updateDateChip()
        // Date-specific copy only when the window is what emptied the list AND clearing the date
        // actually reveals rows; a search with no match keeps the ordinary copy.
        val dateEmpty = appliedDate != null && inDateRows.isEmpty() && clearReveals
        showEmptyState(filtered.isEmpty(), appliedDate, dateEmpty)
    }

    /** Applies (or clears) the date filter and re-renders. The only write path for the chip. */
    @VisibleForTesting
    internal fun applyDateFilter(filter: DiscoverDateFilter?) {
        dateFilter = filter
        renderFilteredEvents()
    }

    private fun clearDateFilter() = applyDateFilter(null)

    private fun updateDateChip() {
        val label = when (val filter = dateFilter) {
            null -> getString(R.string.ui_attendee_date)
            is DiscoverDateFilter.MonthOf -> DateFormatters.formatMonthYear(filter.month)
            is DiscoverDateFilter.DayOf -> dayLabel(filter.day)
        }
        // Text colour and the trailing glyph come from the chip selectors; the fill is the state.
        chipDateFilter.text = label
        chipDateFilter.isSelected = dateFilter != null
        chipDateFilter.contentDescription = if (dateFilter == null) {
            getString(R.string.ui_attendee_filter_date)
        } else {
            getString(R.string.ui_attendee_filter_date_value, label)
        }
    }

    private fun dayLabel(day: LocalDate): String =
        DateFormatters.formatEventDate(day.atStartOfDay(DateFilterRanges.ZONE).toInstant())

    private fun openDateSheet(draft: DateFilterDraft) {
        if (dateSheet != null) return
        // The sheet is a modal choice: drop the search caret and the keyboard behind it so the
        // sheet and its picker own the window.
        currentFocus?.clearFocus()
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(chipDateFilter.windowToken, 0)
        val sheet = DateFilterSheet(this)
        sheet.show(
            draft = draft,
            years = filterYears(draft.year),
            onApply = { filter -> applyDateFilter(filter) },
            onDismiss = {
                dateSheet = null
                // TalkBack lands back on the control that opened the sheet, after its own state
                // (applied or cleared) has been re-rendered.
                chipDateFilter.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null,
                )
            },
        )
        // Only a sheet that is actually up counts; a failed show() must not block the next open.
        dateSheet = sheet
    }

    /** Event start years, plus the current year, the applied filter's year and the draft's. */
    private fun filterYears(draftYear: Int): List<Int> {
        val currentYear = YearMonth.now(DateFilterRanges.ZONE).year
        val appliedYear = when (val filter = dateFilter) {
            is DiscoverDateFilter.MonthOf -> filter.month.year
            is DiscoverDateFilter.DayOf -> filter.day.year
            null -> null
        }
        val eventYears = allEvents.mapNotNull { it.eventStartAt?.atZone(DateFilterRanges.ZONE)?.year }
        return (eventYears + listOfNotNull(currentYear, appliedYear, draftYear)).distinct().sorted()
    }

    private fun draftFromApplied(): DateFilterDraft = when (val filter = dateFilter) {
        is DiscoverDateFilter.MonthOf ->
            DateFilterDraft(DateFilterMode.MONTH, filter.month.year, filter.month.monthValue)
        is DiscoverDateFilter.DayOf ->
            DateFilterDraft(DateFilterMode.DAY, filter.day.year, filter.day.monthValue, filter.day.dayOfMonth)
        null -> DateFilterDraft.current()
    }

    private fun showEmptyState(
        visible: Boolean,
        appliedDate: DiscoverDateFilter?,
        dateEmpty: Boolean,
    ) {
        if (!visible) {
            emptyState.visibility = View.GONE
            return
        }
        // Date-specific copy only earns its "Clear date" action when clearing really reveals rows.
        if (dateEmpty && appliedDate != null) {
            val title = when (appliedDate) {
                is DiscoverDateFilter.MonthOf ->
                    getString(R.string.ui_attendee_filter_empty_month, DateFormatters.formatMonthYear(appliedDate.month))
                is DiscoverDateFilter.DayOf ->
                    getString(R.string.ui_attendee_filter_empty_day, dayLabel(appliedDate.day))
            }
            emptyState.setIcon(AppIcons.Event)
            emptyState.show(title, getString(R.string.ui_attendee_filter_empty_body))
            emptyState.setAction(getString(R.string.ui_attendee_filter_clear_date)) { clearDateFilter() }
            emptyState.visibility = View.VISIBLE
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

    private companion object {
        const val SEARCH_ROW_REFLOW_FONT_SCALE = 1.3f
        const val STATE_FILTER_STATUS = "discover.filter.status"
        const val STATE_FILTER_MODE = "discover.dateFilter.mode"
        const val STATE_FILTER_YEAR = "discover.dateFilter.year"
        const val STATE_FILTER_MONTH = "discover.dateFilter.month"
        const val STATE_FILTER_DAY = "discover.dateFilter.day"
        const val STATE_SHEET_OPEN = "discover.dateFilter.sheetOpen"
        const val STATE_DRAFT_MODE = "discover.dateFilter.draftMode"
        const val STATE_DRAFT_YEAR = "discover.dateFilter.draftYear"
        const val STATE_DRAFT_MONTH = "discover.dateFilter.draftMonth"
        const val STATE_DRAFT_DAY = "discover.dateFilter.draftDay"
    }
}
