package com.thedavelopers.eventqr.features.organizer.events

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

open class ManageEventsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var eventList: LinearLayout
    private lateinit var filterRow: LinearLayout
    private var selectedFilter = "All"
    private var eventsSource: OrganizerMvpLoad<List<OrganizerMvpEvent>> =
        OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)
    private val organizerZone: ZoneId = ZoneId.of("Asia/Manila")
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
    private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val content = organizerShell("My Events", selectedNav = NAV_EVENTS)
        filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
        eventList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(filterRow)
        content.addView(eventList)
        renderFilters()
        eventList.addView(loadingState("Loading events..."))
        loadEvents()
    }

    private fun renderFilters() {
        filterRow.removeAllViews()
        listOf("All", "Upcoming", "Active", "Completed").forEach { label ->
            val isActive = selectedFilter == label
            filterRow.addView(chip(label, isActive, PURPLE).apply {
                if (!isActive) setTextColor(Color.BLACK)
                setOnClickListener {
                    selectedFilter = label
                    renderFilters()
                    render()
                }
            })
        }
    }

    private fun loadEvents() {
        MainScope().launch {
            eventsSource = repository.loadEventsForMvp()
            render()
        }
    }

    private fun render() {
        val events = eventsSource.data.approvedOnly().filter {
            selectedFilter == "All" || it.lifecycleStatus() == selectedFilter
        }
        eventList.removeAllViews()
        dataSourceBanner(eventsSource)?.let { eventList.addView(it) }
        if (events.isEmpty()) {
            eventList.addView(
                if (eventsSource.source == OrganizerMvpDataSource.ERROR) {
                    errorState(eventsSource.message ?: "Organizer events could not be loaded.") { loadEvents() }
                } else {
                    emptyState("No events available for the selected filter.")
                }
            )
            return
        }
        events.forEach { event ->
            eventList.addView(myEventsEventCard(event) {
                openOrganizerPage(EventManagementHubActivity::class.java, event.id, event.title)
            })
        }
    }

    private fun myEventsEventCard(
        event: OrganizerMvpEvent,
        onClick: () -> Unit,
    ): LinearLayout {
        val density = resources.displayMetrics.density
        val parsedStart = parseEventStartDateTime(event)
        val parsedDate = parsedStart?.toLocalDate() ?: parseEventDateOnly(event)
        val day = parsedDate?.format(dayFormatter) ?: "--"
        val month = parsedDate?.format(monthFormatter)?.uppercase(Locale.ENGLISH) ?: "---"

        val timeText = parsedStart?.format(timeFormatter)
        val locationText = event.venue.takeIf { it.isNotBlank() && it != "Venue not set" }
        val timeAndLocation = when {
            !timeText.isNullOrBlank() && !locationText.isNullOrBlank() -> "$timeText · $locationText"
            !timeText.isNullOrBlank() -> timeText
            !locationText.isNullOrBlank() -> locationText
            else -> "-"
        }

        val capacity = event.capacity.coerceAtLeast(0)
        val currentAttendeeCount = event.currentAttendeeCount.coerceAtLeast(0)
        val ratio = if (capacity > 0) {
            (currentAttendeeCount.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val percent = if (capacity > 0) min((ratio * 100f).roundToInt(), 100) else 0

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(8), 0, dp(4)) }
            background = rounded(Color.WHITE, 14, Color.parseColor("#E5E7EB"), density = density)
            elevation = dp(1).toFloat()
            setOnClickListener { onClick() }

            addView(View(this@ManageEventsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4))
                background = rounded(PURPLE, 10, null, density = density)
            })

            addView(LinearLayout(this@ManageEventsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))

                addView(LinearLayout(this@ManageEventsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
                    background = rounded(Color.parseColor("#EDE9FE"), 10, null, density = density)
                    addView(text(day, 16, true, PURPLE).apply { gravity = Gravity.CENTER })
                    addView(text(month, 10, true, PURPLE).apply { gravity = Gravity.CENTER })
                })

                addView(LinearLayout(this@ManageEventsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(12), 0, 0, 0)
                    }

                    addView(LinearLayout(this@ManageEventsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(text(event.title, 16, true).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(statusBadge(event.lifecycleStatus()))
                    })

                    addView(text(timeAndLocation, 12, false, MUTED).apply {
                        setPadding(0, dp(3), 0, 0)
                    })

                    addView(LinearLayout(this@ManageEventsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(6) }

                        addView(text("${formatCount(currentAttendeeCount)} / ${formatCount(capacity)} registered", 12, false, MUTED).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(text("$percent%", 12, false, MUTED))
                    })

                    addView(LinearLayout(this@ManageEventsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(5),
                        ).apply { topMargin = dp(4) }

                        addView(View(this@ManageEventsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, ratio)
                            background = rounded(PURPLE, 8, null, density = density)
                        })
                        addView(View(this@ManageEventsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - ratio)
                            background = rounded(Color.parseColor("#E5E7EB"), 8, null, density = density)
                        })
                    })
                })
            })
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
}
