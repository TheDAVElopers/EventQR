package com.thedavelopers.eventqr.features.organizer.events

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.attendees.AttendeeManagementActivity
import com.thedavelopers.eventqr.features.organizer.formatCount
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.organizer.openOrganizerPage
import com.thedavelopers.eventqr.features.organizer.reports.EventReportsActivity
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import kotlinx.coroutines.launch

/**
 * Organizer event details, read-only (§6.4 v1.7). The hub routes here for any event the organizer can
 * no longer edit, so this screen never renders form fields and has no Save action: it states what the
 * event is, then offers the one next action (attendance) plus reports as its ghost.
 *
 * Seven blocks: top bar, the shared identity head, the registration stub, a three-row spec table, the
 * review note, About, and the action stack. Date / Time / Venue are the head's `when` and `where`,
 * never rows.
 *
 * Editing stays in [EditEventDetailsActivity]; that screen is untouched by this one.
 */
class OrganizerEventDetailsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var content: View
    private lateinit var skeleton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_event_details)
        ScreenInsets.padContentForNavigationBar(this)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        repository = OrganizerRepository(this)
        content = findViewById(R.id.oedContent)
        skeleton = findViewById(R.id.oedSkeleton)

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
        val status = EventStatusBadgeStyler.resolve(
            EventStatusBadgeStyler.fromLabel(event.status),
            event.eventStartAt,
            event.eventEndAt,
        )

        OrganizerEventHeadBinder.bind(
            root = findViewById(R.id.oeHead),
            title = event.title?.takeIf { it.isNotBlank() }
                ?: getString(R.string.organizer_untitled_event),
            status = status,
            whenText = whenLine(event),
            whereText = event.venue?.takeIf { it.isNotBlank() },
        )

        val registered = event.currentAttendeeCount.coerceAtLeast(0)
        val capacity = event.capacity
        val unavailable = getString(R.string.ui_organizer_value_unavailable)
        bindStat(
            R.id.oedStatRegistered,
            R.id.oedStatRegisteredLabel,
            formatCount(registered),
        )
        bindStat(
            R.id.oedStatCapacity,
            R.id.oedStatCapacityLabel,
            if (capacity > 0) formatCount(capacity) else unavailable,
        )
        bindStat(
            R.id.oedStatAvailable,
            R.id.oedStatAvailableLabel,
            // A 0 here reads as a full event, which is false when no cap was recorded (§6.4).
            if (capacity > 0) formatCount((capacity - registered).coerceAtLeast(0)) else unavailable,
        )

        bindRow(
            R.id.oedRowRegistration,
            R.id.oedDividerRegistration,
            R.id.oedRegistrationValue,
            registrationWindow(event),
        )
        bindRow(
            R.id.oedRowOrganizer,
            R.id.oedDividerOrganizer,
            R.id.oedOrganizerValue,
            event.organizerName?.takeIf { it.isNotBlank() },
        )
        bindRow(
            R.id.oedRowCoOrganizers,
            R.id.oedDividerCoOrganizers,
            R.id.oedCoOrganizersValue,
            event.additionalOrganizers
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .takeIf { it.isNotBlank() },
        )

        bindReviewNote(status, event.adminRemarks)

        val about = event.description?.takeIf { it.isNotBlank() }
        findViewById<View>(R.id.oedAboutBlock).visibility =
            if (about != null) View.VISIBLE else View.GONE
        about?.let { findViewById<TextView>(R.id.oedAboutBody).text = it }

        val eventTitle = event.title.orEmpty()
        val eventId = event.eventId.toString()
        findViewById<View>(R.id.oedActionAttendees).setOnClickListener {
            openOrganizerPage(AttendeeManagementActivity::class.java, eventId, eventTitle)
        }
        findViewById<View>(R.id.oedActionReports).setOnClickListener {
            openOrganizerPage(EventReportsActivity::class.java, eventId, eventTitle)
        }

        skeleton.visibility = View.GONE
        content.visibility = View.VISIBLE
    }

    /**
     * `formatEventDate(start)` on a one-day event, the range when it spans days, then the times.
     * Null when the event has no start, so the head shrinks to the title row instead of printing a
     * placeholder.
     */
    private fun whenLine(event: OrganizerEventDto): String? {
        val start = event.eventStartAt ?: return null
        val end = event.eventEndAt
        val date = if (end != null &&
            DateFormatters.formatEventDate(end) != DateFormatters.formatEventDate(start)
        ) {
            getString(
                R.string.event_detail_date_range,
                DateFormatters.formatEventDate(start),
                DateFormatters.formatEventDate(end),
            )
        } else {
            DateFormatters.formatEventDate(start)
        }
        val time = if (end != null) {
            "${DateFormatters.formatTime(start)} - ${DateFormatters.formatTime(end)}"
        } else {
            DateFormatters.formatTime(start)
        }
        return "$date, $time"
    }

    private fun registrationWindow(event: OrganizerEventDto): String? {
        val open = event.registrationOpenAt?.let { DateFormatters.displayFormatter.format(it) }
        val close = event.registrationCloseAt?.let { DateFormatters.displayFormatter.format(it) }
        return when {
            open != null && close != null -> "$open - $close"
            else -> open ?: close
        }
    }

    /** One TalkBack node per stat cell: `{value} {label}` (§9.5). The label view stays silent. */
    private fun bindStat(valueId: Int, labelId: Int, value: String) {
        val label = findViewById<TextView>(labelId).text
        findViewById<TextView>(valueId).apply {
            text = value
            contentDescription = "$value $label"
        }
    }

    /**
     * Renders `adminRemarks` once, under the stub. Tone follows the status; blank remarks or a
     * status that is neither Rejected nor Pending review hide the block entirely.
     */
    private fun bindReviewNote(status: EventStatus, remarks: String?) {
        val note = findViewById<View>(R.id.oedReviewNote)
        val containerRes = when (status) {
            EventStatus.REJECTED -> R.color.ui_error_container
            EventStatus.PENDING_REVIEW -> R.color.ui_warning_container
            else -> null
        }
        val text = remarks?.takeIf { it.isNotBlank() }
        if (containerRes == null || text == null) {
            note.visibility = View.GONE
            return
        }
        val onRes = if (status == EventStatus.REJECTED) {
            R.color.ui_on_error_container
        } else {
            R.color.ui_on_warning_container
        }
        val onColor = ContextCompat.getColor(this, onRes)
        note.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, containerRes))
        findViewById<TextView>(R.id.oedReviewNoteLabel).setTextColor(onColor)
        findViewById<TextView>(R.id.oedReviewNoteBody).apply {
            setTextColor(onColor)
            this.text = text
        }
        // Label and body are silent in XML, so the block reads once as "Review note. {remarks}".
        note.contentDescription = "${getString(R.string.ui_organizer_review_note)}. $text"
        note.visibility = View.VISIBLE
    }

    /** A blank value hides the row and its hairline - no empty label, no orphan rule. */
    private fun bindRow(rowId: Int, dividerId: Int, valueId: Int, value: String?) {
        val visible = !value.isNullOrBlank()
        findViewById<View>(rowId).visibility = if (visible) View.VISIBLE else View.GONE
        findViewById<View>(dividerId).visibility = if (visible) View.VISIBLE else View.GONE
        value?.let { findViewById<TextView>(valueId).text = it }
    }
}
