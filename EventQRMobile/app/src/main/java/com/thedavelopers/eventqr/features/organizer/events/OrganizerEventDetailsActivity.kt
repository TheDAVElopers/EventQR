package com.thedavelopers.eventqr.features.organizer.events

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.attendees.AttendeeManagementActivity
import com.thedavelopers.eventqr.features.organizer.bindEventBadge
import com.thedavelopers.eventqr.features.organizer.formatCount
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.organizer.openOrganizerPage
import com.thedavelopers.eventqr.features.organizer.reports.EventReportsActivity
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import kotlinx.coroutines.launch

/**
 * Organizer event details, read-only (§6.4). The hub routes here for any event the organizer can no
 * longer edit, so this screen never renders form fields and has no Save action: it states what the
 * event is, then offers the two things still worth doing (Attendees, Reports).
 *
 * Editing stays in [EditEventDetailsActivity]; that screen is untouched by this one.
 */
class OrganizerEventDetailsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_event_details)
        ScreenInsets.padContentForNavigationBar(this)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        repository = OrganizerRepository(this)

        val topBar = findViewById<View>(R.id.topBar)
        topBar.findViewById<View>(R.id.topBarBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        topBar.findViewById<TextView>(R.id.topBarTitle).text =
            getString(R.string.ui_organizer_event_details_title)

        val eventId = intentEventId()
            ?: return showMissingEventScreen(getString(R.string.ui_organizer_event_details_title))
        render(eventId)
    }

    private fun render(eventId: String) {
        lifecycleScope.launch {
            when (val result = repository.fetchOrganizerEvent(eventId)) {
                is NetworkResult.Success -> bind(result.data)
                is NetworkResult.Error -> showMissingEventScreen(
                    getString(R.string.ui_organizer_event_details_title),
                    result.message ?: getString(R.string.event_management_hub_details_load_failed),
                )
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun bind(event: OrganizerEventDto) {
        findViewById<TextView>(R.id.oedTitle).text =
            event.title?.takeIf { it.isNotBlank() } ?: getString(R.string.organizer_untitled_event)
        findViewById<TextView>(R.id.oedStatusBadge).bindEventBadge(
            EventStatusBadgeStyler.resolve(
                EventStatusBadgeStyler.fromLabel(event.status),
                event.eventStartAt,
                event.eventEndAt,
            ),
        )

        val registered = event.currentAttendeeCount.coerceAtLeast(0)
        val capacity = event.capacity.coerceAtLeast(0)
        val available = (capacity - registered).coerceAtLeast(0)
        findViewById<TextView>(R.id.oedStatRegistered).text = formatCount(registered)
        findViewById<TextView>(R.id.oedStatCapacity).text = formatCount(capacity)
        findViewById<TextView>(R.id.oedStatAvailable).text = formatCount(available)

        val start = event.eventStartAt
        val end = event.eventEndAt
        val date = start?.let { startAt ->
            if (end != null && DateFormatters.formatEventDate(end) != DateFormatters.formatEventDate(startAt)) {
                getString(
                    R.string.event_detail_date_range,
                    DateFormatters.formatEventDate(startAt),
                    DateFormatters.formatEventDate(end),
                )
            } else {
                DateFormatters.formatEventDate(startAt)
            }
        }
        val time = start?.let { startAt ->
            end?.let { endAt -> "${DateFormatters.formatTime(startAt)} - ${DateFormatters.formatTime(endAt)}" }
                ?: DateFormatters.formatTime(startAt)
        }
        val registrationOpen = event.registrationOpenAt?.let { DateFormatters.displayFormatter.format(it) }
        val registrationClose = event.registrationCloseAt?.let { DateFormatters.displayFormatter.format(it) }
        val registrationWindow = if (registrationOpen != null && registrationClose != null) {
            "$registrationOpen - $registrationClose"
        } else {
            registrationOpen
        }
        val coOrganizers = event.additionalOrganizers
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }

        bindRow(R.id.oedRowDate, R.id.oedDividerDate, R.id.oedDateValue, date)
        bindRow(R.id.oedRowTime, R.id.oedDividerTime, R.id.oedTimeValue, time)
        bindRow(R.id.oedRowVenue, R.id.oedDividerVenue, R.id.oedVenueValue, event.venue?.takeIf { it.isNotBlank() })
        bindRow(R.id.oedRowRegistration, R.id.oedDividerRegistration, R.id.oedRegistrationValue, registrationWindow)
        bindRow(R.id.oedRowOrganizer, R.id.oedDividerOrganizer, R.id.oedOrganizerValue, event.organizerName?.takeIf { it.isNotBlank() })
        bindRow(R.id.oedRowCoOrganizers, R.id.oedDividerCoOrganizers, R.id.oedCoOrganizersValue, coOrganizers)

        val about = event.description?.takeIf { it.isNotBlank() }
        findViewById<View>(R.id.oedAboutBlock).visibility = if (about != null) View.VISIBLE else View.GONE
        about?.let { findViewById<TextView>(R.id.oedAboutBody).text = it }

        val eventTitle = event.title.orEmpty()
        val eventId = event.eventId.toString()
        findViewById<View>(R.id.oedActionAttendees).setOnClickListener {
            openOrganizerPage(AttendeeManagementActivity::class.java, eventId, eventTitle)
        }
        findViewById<View>(R.id.oedActionReports).setOnClickListener {
            openOrganizerPage(EventReportsActivity::class.java, eventId, eventTitle)
        }

        findViewById<View>(R.id.oedContent).visibility = View.VISIBLE
    }

    private fun bindRow(rowId: Int, dividerId: Int, valueId: Int, value: String?) {
        val visible = !value.isNullOrBlank()
        findViewById<View>(rowId).visibility = if (visible) View.VISIBLE else View.GONE
        findViewById<View>(dividerId).visibility = if (visible) View.VISIBLE else View.GONE
        value?.let { findViewById<TextView>(valueId).text = it }
    }
}
