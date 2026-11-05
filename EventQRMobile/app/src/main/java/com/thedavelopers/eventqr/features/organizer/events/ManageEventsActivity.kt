package com.thedavelopers.eventqr.features.organizer.events

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.features.organizer.NAV_EVENTS
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.approvedOnly
import com.thedavelopers.eventqr.features.organizer.bottomNav
import com.thedavelopers.eventqr.features.organizer.lifecycleStatus
import com.thedavelopers.eventqr.features.organizer.openOrganizerPage
import com.thedavelopers.eventqr.ui.components.CountLabel
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Organizer event list (§6.4). Compact pass head + 48dp search + chip row + 96dp date rows.
 * Every event is a row on the page with a hairline divider; there is no card per event.
 */
open class ManageEventsActivity : AppCompatActivity() {

    private lateinit var repository: OrganizerRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: EventQrEmptyState
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var adapter: OrganizerEventAdapter
    private lateinit var countView: TextView
    private var allEvents: List<OrganizerMvpEvent> = emptyList()
    private var selectedFilter = FILTER_ALL
    private var isFirstResume = true
    private var eventsSource: OrganizerMvpLoad<List<OrganizerMvpEvent>> =
        OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
    private val organizerZone: ZoneId = ZoneId.of("Asia/Manila")
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
    private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        setContentView(R.layout.activity_organizer_events)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()

        swipeRefresh = findViewById(R.id.swipeRefreshEvents)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        recyclerView = findViewById(R.id.recyclerEvents)
        emptyState = findViewById(R.id.layoutEventsEmpty)

        val passHead = findViewById<View>(R.id.passHead)
        countView = findViewById<TextView>(R.id.passHeadTitleCount).apply { visibility = View.VISIBLE }
        PassHeadBinder.bindCompact(
            root = passHead,
            title = getString(R.string.ui_organizer_events),
            eyebrow = getString(R.string.ui_organizer_role),
        )

        adapter = OrganizerEventAdapter { event ->
            openOrganizerPage(EventManagementHubActivity::class.java, event.id, event.title)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { loadEvents() }
        emptyState.setAction(getString(R.string.common_retry)) { loadEvents() }
        findViewById<View>(R.id.chipAll).setOnClickListener { selectFilter(FILTER_ALL) }
        findViewById<View>(R.id.chipUpcoming).setOnClickListener { selectFilter("Upcoming") }
        findViewById<View>(R.id.chipActive).setOnClickListener { selectFilter("Active") }
        findViewById<View>(R.id.chipCompleted).setOnClickListener { selectFilter("Completed") }

        findViewById<EditText>(R.id.inputEventSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderFilteredEvents()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<LinearLayout>(R.id.layoutOrganizerBottomNav).addView(bottomNav(NAV_EVENTS))

        updateChips()
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

    private fun selectFilter(filter: String) {
        selectedFilter = filter
        updateChips()
        renderFilteredEvents()
    }

    /** §5.6: the fill is the state; there is no check icon and no pill. */
    private fun updateChips() {
        val chips = mapOf(
            FILTER_ALL to findViewById<TextView>(R.id.chipAll),
            "Upcoming" to findViewById<TextView>(R.id.chipUpcoming),
            "Active" to findViewById<TextView>(R.id.chipActive),
            "Completed" to findViewById<TextView>(R.id.chipCompleted),
        )
        for ((label, chip) in chips) {
            val selected = label == selectedFilter
            chip.isSelected = selected
            chip.setBackgroundResource(
                if (selected) R.drawable.bg_ui_chip_selected else R.drawable.bg_ui_chip_unselected,
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (selected) R.color.ui_on_primary else R.color.ui_text_primary,
                ),
            )
        }
    }

    private fun loadEvents() {
        if (!swipeRefresh.isRefreshing) {
            showLoading()
        }
        lifecycleScope.launch {
            eventsSource = repository.loadEventsForMvp()
            swipeRefresh.isRefreshing = false
            allEvents = eventsSource.data
            renderFilteredEvents()
        }
    }

    private fun getFilteredByStatus(): List<OrganizerMvpEvent> {
        val approved = allEvents.approvedOnly()
        return when (selectedFilter) {
            FILTER_ALL -> {
                val active = approved.filter { it.lifecycleStatus() == "Active" }
                    .sortedBy { parseEventStartDateTime(it) }
                val upcoming = approved.filter { it.lifecycleStatus() == "Upcoming" }
                    .sortedBy { parseEventStartDateTime(it) }
                val completed = approved.filter { it.lifecycleStatus() == "Completed" }
                    .sortedByDescending { parseEventStartDateTime(it) }
                active + upcoming + completed
            }
            "Upcoming" -> approved.filter { it.lifecycleStatus() == "Upcoming" }
            "Active" -> approved.filter { it.lifecycleStatus() == "Active" }
            "Completed" -> approved.filter { it.lifecycleStatus() == "Completed" }
            else -> approved
        }
    }

    private fun getSearchQuery(): String =
        findViewById<EditText>(R.id.inputEventSearch).text.toString()

    private fun renderFilteredEvents() {
        val query = getSearchQuery()
        val filtered = getFilteredByStatus().let { list ->
            if (query.isBlank()) list
            else list.filter { it.title.contains(query, ignoreCase = true) }
        }
        adapter.submitItems(filtered)
        countView.text = CountLabel.format(filtered.size.toLong(), getString(R.string.ui_organizer_events_count))
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        showEmptyOrError(filtered.isEmpty())
    }

    private fun showLoading() {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = View.VISIBLE
        }
    }

    private fun showEmptyOrError(isEmpty: Boolean) {
        skeletonLoading.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        if (!isEmpty) {
            emptyState.visibility = View.GONE
            return
        }
        emptyState.visibility = View.VISIBLE
        if (eventsSource.source == OrganizerMvpDataSource.ERROR) {
            emptyState.show(
                eventsSource.message ?: getString(R.string.organizer_dashboard_events_load_failed),
                getString(R.string.ui_organizer_no_events_body),
            )
        } else {
            emptyState.show(
                getString(R.string.organizer_events_no_events_available_for_the_selected),
                getString(R.string.common_try_adjusting_your_search_or_filters),
            )
        }
    }

    private fun parseEventStartDateTime(event: OrganizerMvpEvent): LocalDateTime? {
        val candidates = listOfNotNull(event.dateTime, event.shortDate)
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "-" }
        candidates.forEach { raw ->
            val firstPart = raw.substringBefore(" - ").trim()
            parseDateTimeValue(firstPart)?.let { return it }
            parseDateTimeValue(raw)?.let { return it }
        }
        return null
    }

    private fun parseEventDateOnly(event: OrganizerMvpEvent): LocalDate? {
        val candidates = listOfNotNull(event.shortDate, event.dateTime)
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "-" }
        candidates.forEach { raw ->
            val firstPart = raw.substringBefore(" - ").trim()
            parseDateValue(firstPart)?.let { return it }
            parseDateValue(raw)?.let { return it }
        }
        return null
    }

    private fun parseDateTimeValue(value: String): LocalDateTime? {
        val normalized = value.replace("•", "").replace("  ", " ").trim()
        return runCatching { Instant.parse(normalized).atZone(organizerZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(normalized).atZoneSameInstant(organizerZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(normalized).withZoneSameInstant(organizerZone).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("d MMM yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) }.getOrNull()
    }

    private fun parseDateValue(value: String): LocalDate? {
        val normalized = value.replace("•", "").replace("  ", " ").trim()
        return runCatching { LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            ?: runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) }.getOrNull()
            ?: parseDateTimeValue(normalized)?.toLocalDate()
    }

    /** One event = one `list_row_date` (§5.5). The row is the target. */
    inner class OrganizerEventAdapter(
        private val onClick: (OrganizerMvpEvent) -> Unit,
    ) : RecyclerView.Adapter<OrganizerEventAdapter.ViewHolder>() {

        private val items = mutableListOf<OrganizerMvpEvent>()

        fun submitItems(newItems: List<OrganizerMvpEvent>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_row_date, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position == items.lastIndex)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val row: View) : RecyclerView.ViewHolder(row) {

            fun bind(event: OrganizerMvpEvent, isLast: Boolean) {
                val parsedStart = parseEventStartDateTime(event)
                val parsedDate = parsedStart?.toLocalDate() ?: parseEventDateOnly(event)
                row.findViewById<TextView>(R.id.listRowDay).text =
                    parsedDate?.format(dayFormatter) ?: EventCardPresenter.UNKNOWN_DAY
                row.findViewById<TextView>(R.id.listRowMonth).text =
                    parsedDate?.format(monthFormatter)?.uppercase(Locale.ENGLISH) ?: EventCardPresenter.UNKNOWN_MONTH
                row.findViewById<TextView>(R.id.listRowTitle).text = event.title
                val time = parsedStart?.format(timeFormatter) ?: EventCardPresenter.UNKNOWN_TIME
                val location = event.venue.takeIf { it.isNotBlank() && it != "Venue not set" }
                    ?: EventCardPresenter.UNKNOWN_LOCATION
                row.findViewById<TextView>(R.id.listRowMeta).text = listOf(time, location)
                    .filter {
                        it != EventCardPresenter.UNKNOWN_TIME && it != EventCardPresenter.UNKNOWN_LOCATION
                    }
                    .joinToString(" - ")

                val badge = row.findViewById<TextView>(R.id.listRowBadge)
                val status = event.lifecycleStatus()
                badge.text = status
                val (bg, fg) = when (status) {
                    "Active" -> R.color.ui_accent to R.color.ui_on_accent
                    "Upcoming" -> R.color.ui_info_container to R.color.ui_on_info_container
                    "Draft", "Pending Review" -> R.color.ui_warning_container to R.color.ui_on_warning_container
                    "Cancelled", "Rejected" -> R.color.ui_error_container to R.color.ui_on_error_container
                    else -> R.color.ui_surface_sunken to R.color.ui_text_secondary
                }
                badge.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(ContextCompat.getColor(row.context, bg))
                badge.setTextColor(ContextCompat.getColor(row.context, fg))

                row.findViewById<View>(R.id.listRowContainer).setOnClickListener { onClick(event) }
                row.findViewById<View>(R.id.listRowDivider).visibility =
                    if (isLast) View.GONE else View.VISIBLE
            }
        }
    }

    private companion object {
        const val FILTER_ALL = "All"
    }
}
