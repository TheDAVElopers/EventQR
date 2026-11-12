package com.thedavelopers.eventqr.features.organizer.attendees

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpAttendee
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.intentEventTitle
import com.thedavelopers.eventqr.features.organizer.matchesOrganizerAttendeeQuery
import com.thedavelopers.eventqr.features.organizer.resolveSelectedEvent
import com.thedavelopers.eventqr.features.organizer.selectedEventId
import com.thedavelopers.eventqr.features.organizer.showEventsLoadErrorScreen
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.asFilterChip
import com.thedavelopers.eventqr.ui.icons.FieldIcons
import com.thedavelopers.eventqr.ui.theme.color
import kotlinx.coroutines.launch

open class SearchAttendeesActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var adapter: SearchAttendeesAdapter
    private lateinit var searchInput: EditText
    private lateinit var emptyState: EventQrEmptyState
    private lateinit var progressBar: ProgressBar
    private lateinit var filterChips: Map<String, TextView>
    private var attendees: List<OrganizerMvpAttendee> = emptyList()
    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_attendees)
        ScreenInsets.padContentForNavigationBar(this)

        repository = OrganizerRepository(this)

        findViewById<View>(R.id.topBarBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.topBarTitle).text = getString(R.string.ui_organizer_search_attendees)

        // Set initial dynamic title from Intent while fetching the full event object
        val initialTitle = intentEventTitle()?.takeIf { it.isNotBlank() }
        if (!initialTitle.isNullOrBlank()) {
            findViewById<TextView>(R.id.txtSearchSubtitle).text = initialTitle
        }

        val eventId = intentEventId() ?: selectedEventId().takeIf { it.isNotBlank() }
            ?: return showMissingEventScreen(getString(R.string.search_attendees_title))

        lifecycleScope.launch {
            val eventsLoad = repository.loadApprovedOrganizerEvents()
            if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
                showEventsLoadErrorScreen(getString(R.string.search_attendees_title), eventsLoad.message) { recreate() }
                return@launch
            }
            selectedEvent = resolveSelectedEvent(eventsLoad.data, eventId)
                ?: run {
                    showMissingEventScreen(getString(R.string.search_attendees_title))
                    return@launch
                }

            // Always dynamically display the chosen event's title
            findViewById<TextView>(R.id.txtSearchSubtitle).text = selectedEvent.title.ifBlank { getString(R.string.organizer_selected_event) }

            searchInput = findViewById(R.id.edtSearchAttendees)
            FieldIcons.apply(searchInput)
            emptyState = findViewById(R.id.txtSearchEmpty)

            adapter = SearchAttendeesAdapter { openDetails(it) }
            findViewById<RecyclerView>(R.id.recyclerSearchAttendees).apply {
                layoutManager = LinearLayoutManager(this@SearchAttendeesActivity)
                adapter = this@SearchAttendeesActivity.adapter
            }

            filterChips = mapOf(
                "All" to findViewById(R.id.chipAll),
                "Registered" to findViewById(R.id.chipRegistered),
                "Checked In" to findViewById(R.id.chipCheckedIn),
                "Exited" to findViewById(R.id.chipExited),
                "Cancelled" to findViewById(R.id.chipCancelled),
                // No "No Show" chip: nothing in the system sets RegistrationStatus.NO_SHOW, so it would always be empty.
            )
            filterChips.forEach { (label, chip) ->
                chip.asFilterChip()
                chip.setOnClickListener {
                    currentFilter = label
                    updateChips()
                    render()
                }
            }

            searchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
                override fun afterTextChanged(s: Editable?) = Unit
            })

            updateChips()
            loadAttendees()
        }
    }

    private fun loadAttendees() {
        lifecycleScope.launch {
            val load = repository.loadAttendeesForMvp(selectedEvent.id)
            attendees = load.data
            render()
        }
    }

    private fun render() {
        val query = searchInput.text?.toString().orEmpty()
        val filtered = attendees.filter { it.matchesOrganizerAttendeeQuery(query, currentFilter) }
        adapter.submitItems(filtered)
        emptyState.setContent(
            getString(
                if (attendees.isEmpty()) R.string.search_attendees_no_attendees_found_for_this_event
                else R.string.search_attendees_no_attendees_match_your_search,
            ),
        )
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateChips() {
        filterChips.forEach { (label, chip) ->
            val selected = label == currentFilter
            chip.isSelected = selected
        }
    }

    private fun openDetails(attendee: OrganizerMvpAttendee) {
        startActivity(
            Intent(this, AttendeeDetailsActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, selectedEvent.id)
                .putExtra(EXTRA_EVENT_TITLE, selectedEvent.title)
                .putExtra(EXTRA_ATTENDEE_ID, attendee.id)
        )
    }

    companion object {
        const val EXTRA_ATTENDEE_ID = "extra_attendee_id"
    }
}
