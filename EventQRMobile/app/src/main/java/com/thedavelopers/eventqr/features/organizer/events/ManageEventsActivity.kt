package com.thedavelopers.eventqr.features.organizer.events

import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.ui.components.EventCardHolder
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

open class ManageEventsActivity : AppCompatActivity() {

    private lateinit var repository: OrganizerRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: android.widget.TextView
    private lateinit var emptySubView: android.widget.TextView
    private lateinit var emptyIcon: android.widget.ImageView
    private lateinit var progressLoading: CircularProgressIndicator
    private lateinit var retryButton: android.widget.Button
    private lateinit var chipAll: Chip
    private lateinit var chipUpcoming: Chip
    private lateinit var chipActive: Chip
    private lateinit var chipCompleted: Chip
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var adapter: OrganizerEventAdapter
    private var allEvents: List<OrganizerMvpEvent> = emptyList()
    private var selectedFilter = "All"
    private var isFirstResume = true
    private var eventsSource: OrganizerMvpLoad<List<OrganizerMvpEvent>> =
        OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
    private val organizerZone: ZoneId = ZoneId.of("Asia/Manila")
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
    private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        setContentView(R.layout.activity_organizer_events)
        applyEventQrSystemBarAppearance()

        swipeRefresh = findViewById(R.id.swipeRefreshEvents)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        recyclerView = findViewById(R.id.recyclerEvents)
        emptyView = findViewById(R.id.txtEventsEmpty)
        emptySubView = findViewById(R.id.txtEventsEmptySub)
        emptyIcon = findViewById(R.id.imgEmptyIcon)
        progressLoading = findViewById(R.id.progressLoading)
        retryButton = findViewById(R.id.btnRefreshEvents)
        chipAll = findViewById(R.id.chipAll)
        chipUpcoming = findViewById(R.id.chipUpcoming)
        chipActive = findViewById(R.id.chipActive)
        chipCompleted = findViewById(R.id.chipCompleted)

        adapter = OrganizerEventAdapter { event ->
            openOrganizerPage(EventManagementHubActivity::class.java, event.id, event.title)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { loadEvents() }
        retryButton.setOnClickListener { loadEvents() }
        chipAll.setOnClickListener { selectFilter("All") }
        chipUpcoming.setOnClickListener { selectFilter("Upcoming") }
        chipActive.setOnClickListener { selectFilter("Active") }
        chipCompleted.setOnClickListener { selectFilter("Completed") }

        findViewById<EditText>(R.id.inputEventSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderFilteredEvents()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val navContainer = findViewById<LinearLayout>(R.id.layoutOrganizerBottomNav)
        navContainer.addView(bottomNav(NAV_EVENTS))

        updateTabs()
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
        updateTabs()
        renderFilteredEvents()
    }

    private fun updateTabs() {
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)
        val chips = mapOf(
            "All" to chipAll,
            "Upcoming" to chipUpcoming,
            "Active" to chipActive,
            "Completed" to chipCompleted,
        )
        for ((label, chip) in chips) {
            val isSelected = label == selectedFilter
            chip.isChecked = isSelected
            chip.setTextColor(if (isSelected) ContextCompat.getColor(this, R.color.brand_on_primary) else textSecondary)
            chip.setChipBackgroundColorResource(if (isSelected) R.color.eventqr_indigo else R.color.surface)
            chip.chipStrokeWidth = if (isSelected) 0f else resources.displayMetrics.density
            chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.outline)
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
            retryButton.visibility = View.GONE
            renderFilteredEvents()
        }
    }

    private fun getFilteredByStatus(): List<OrganizerMvpEvent> {
        val approved = allEvents.approvedOnly()
        return when (selectedFilter) {
            "All" -> {
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

    private fun getSearchQuery(): String {
        val editText = findViewById<EditText>(R.id.inputEventSearch)
        return editText.text.toString()
    }

    private fun renderFilteredEvents() {
        val query = getSearchQuery()
        val filtered = getFilteredByStatus().let { list ->
            if (query.isBlank()) list
            else list.filter { it.title.contains(query, ignoreCase = true) }
        }
        adapter.submitItems(filtered)
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        showEmptyOrError(filtered.isEmpty())
    }

    private fun showLoading() {
        recyclerView.visibility = View.GONE
        emptyIcon.visibility = View.GONE
        emptyView.visibility = View.GONE
        emptySubView.visibility = View.GONE
        retryButton.visibility = View.GONE
        progressLoading.visibility = View.GONE
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = View.VISIBLE
        }
    }

    private fun showEmptyOrError(isEmpty: Boolean) {
        progressLoading.visibility = View.GONE
        skeletonLoading.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        if (!isEmpty) {
            emptyIcon.visibility = View.GONE
            emptyView.visibility = View.GONE
            emptySubView.visibility = View.GONE
            retryButton.visibility = View.GONE
            return
        }
        emptyIcon.visibility = View.VISIBLE
        emptyView.visibility = View.VISIBLE
        emptySubView.visibility = View.VISIBLE
        if (eventsSource.source == OrganizerMvpDataSource.ERROR) {
            emptyView.text = eventsSource.message ?: "Organizer events could be loaded."
            emptySubText = "Pull down to refresh or tap retry"
            retryButton.visibility = View.VISIBLE
        } else {
            emptyView.text = getString(R.string.organizer_events_no_events_available_for_the_selected)
            emptySubText = "Try adjusting your search or filters"
            retryButton.visibility = View.GONE
        }
    }

    private var emptySubText: String
        get() = emptySubView.text.toString()
        set(value) { emptySubView.text = value }

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
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) }.getOrNull()
    }

    private fun parseDateValue(value: String): LocalDate? {
        val normalized = value.replace("•", "").replace("  ", " ").trim()
        return runCatching { LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            ?: runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)) }.getOrNull()
            ?: parseDateTimeValue(normalized)?.toLocalDate()
    }

    inner class OrganizerEventAdapter(
        private val onClick: (OrganizerMvpEvent) -> Unit,
    ) : RecyclerView.Adapter<OrganizerEventAdapter.ViewHolder>() {

        private val items = mutableListOf<OrganizerMvpEvent>()

        fun submitItems(newItems: List<OrganizerMvpEvent>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(EventCardHolder(parent.context))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val card: EventCardHolder) :
            RecyclerView.ViewHolder(card.view) {

            fun bind(event: OrganizerMvpEvent) {
                val parsedStart = parseEventStartDateTime(event)
                val parsedDate = parsedStart?.toLocalDate() ?: parseEventDateOnly(event)
                val day = parsedDate?.format(dayFormatter) ?: EventCardPresenter.UNKNOWN_DAY
                val month = parsedDate?.format(monthFormatter)?.uppercase(Locale.ENGLISH) ?: EventCardPresenter.UNKNOWN_MONTH
                val time = parsedStart?.format(timeFormatter) ?: EventCardPresenter.UNKNOWN_TIME

                card.update(
                    title = event.title,
                    status = event.lifecycleStatus(),
                    day = day,
                    month = month,
                    time = time,
                    location = event.venue.takeIf { it.isNotBlank() && it != "Venue not set" } ?: EventCardPresenter.UNKNOWN_LOCATION,
                    count = event.currentAttendeeCount.coerceAtLeast(0),
                    capacity = EventCardPresenter.capacity(event.capacity),
                    onClick = { onClick(event) },
                )
            }
        }
    }
}
