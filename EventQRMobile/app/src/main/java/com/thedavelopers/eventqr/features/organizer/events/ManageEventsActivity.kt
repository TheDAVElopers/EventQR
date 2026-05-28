package com.thedavelopers.eventqr.features.organizer.events

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class ManageEventsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var eventList: LinearLayout
    private lateinit var filterRow: LinearLayout
    private var selectedFilter = "All"
    private var eventsSource: OrganizerMvpLoad<List<OrganizerMvpEvent>> =
        OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)

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
            filterRow.addView(chip(label, selectedFilter == label, PURPLE).apply {
                setOnClickListener {
                    selectedFilter = label
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
            eventList.addView(eventListCard(event) {
                openOrganizerPage(EventManagementHubActivity::class.java, event.id, event.title)
            })
        }
    }
}
