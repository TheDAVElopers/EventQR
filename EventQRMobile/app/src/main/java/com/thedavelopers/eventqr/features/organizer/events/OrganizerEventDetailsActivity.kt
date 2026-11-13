package com.thedavelopers.eventqr.features.organizer.events

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.format.formatEventTime
import com.thedavelopers.eventqr.core.format.formatRegistrationWindow
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.attendees.AttendeeManagementActivity
import com.thedavelopers.eventqr.features.organizer.bindEventBadge
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.organizer.openOrganizerPage
import com.thedavelopers.eventqr.features.organizer.reports.EventReportsActivity
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import com.thedavelopers.eventqr.ui.components.MetricRowStacking
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import kotlinx.coroutines.launch

/**
 * Organizer event details, read-only (§6.4). The hub routes here for any event the organizer can no
 * longer edit, so this screen never renders form fields and has no Save action: it states what the
 * event is, then offers the one next action (attendance) plus reports as its ghost.
 *
 * Five blocks: top bar, the shared identity head, the spec table (status, location, event time,
 * registration window, organizer, co-organizers, about), the review note, and the action stack.
 * The head states identity only; the badge is the table's STATUS row, and venue and schedule are
 * table rows too. There is no stats band: Registered / Capacity / Available are the Manage event
 * hub's stub, and repeating them here said the same numbers twice.
 *
 * Editing stays in [EditEventDetailsActivity]; that screen is untouched by this one.
 */
class OrganizerEventDetailsActivity : AppCompatActivity() {
    private var repositoryFactory: (Context) -> OrganizerRepository = { OrganizerRepository(it) }

    @VisibleForTesting
    internal lateinit var repository: OrganizerRepository

    @VisibleForTesting
    internal fun installRepositoryFactory(factory: (Context) -> OrganizerRepository) {
        repositoryFactory = factory
    }

    private lateinit var content: View
    private lateinit var skeleton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_event_details)
        ScreenInsets.padContentForNavigationBar(this)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        repository = repositoryFactory(this)
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

    @VisibleForTesting
    internal fun bind(event: OrganizerEventDto) {
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
            // The badge is the STATUS row here, not a header ornament; the editor keeps the header.
            showStatus = false,
        )

        // Same status -> label/tone mapping as the head binder (`bindEventBadge`), one code path.
        findViewById<TextView>(R.id.oedStatusValue).bindEventBadge(status)
        findViewById<View>(R.id.oedRowStatus).visibility = View.VISIBLE
        findViewById<View>(R.id.oedDividerStatus).visibility = View.VISIBLE

        bindRow(
            R.id.oedRowLocation,
            R.id.oedDividerLocation,
            R.id.oedLocationValue,
            event.venue?.takeIf { it.isNotBlank() },
        )
        bindRow(
            R.id.oedRowEventTime,
            R.id.oedDividerEventTime,
            R.id.oedEventTimeValue,
            formatEventTime(this, event.eventStartAt, event.eventEndAt),
        )
        bindRow(
            R.id.oedRowRegistration,
            R.id.oedDividerRegistration,
            R.id.oedRegistrationValue,
            formatRegistrationWindow(this, event.registrationOpenAt, event.registrationCloseAt),
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

        // §6.4/§9.3: above fontScale 1.3 the label sits over a full-width value, the same path the
        // attendee event detail's REGISTRATION WINDOW row uses - one implementation, not a copy.
        organizerEventDetailStackedRows.forEach { (rowId, valueId) ->
            MetricRowStacking.apply(this, findViewById(rowId), findViewById(valueId))
        }

        val about = event.description?.takeIf { it.isNotBlank() }
        findViewById<View>(R.id.oedAboutBlock).visibility =
            if (about != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.oedDividerAbout).visibility =
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
     * Renders `adminRemarks` once, under the spec table. Tone follows the status; blank remarks or a
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

/**
 * The spec-table rows that stack above `fontScale` 1.3 (§6.4/§9.3), as (row, value) pairs: label on
 * top, value beneath through [MetricRowStacking] (a text value takes the row width; the STATUS badge
 * keeps hugging its label). `bind()` applies the stacking from this list, and `ComponentInflationTest`
 * pins the inflated table's row ids to it, so the layout and the stacked set stay in sync instead of
 * being asserted twice from separate copies.
 */
internal val organizerEventDetailStackedRows: List<Pair<Int, Int>> = listOf(
    R.id.oedRowStatus to R.id.oedStatusValue,
    R.id.oedRowLocation to R.id.oedLocationValue,
    R.id.oedRowEventTime to R.id.oedEventTimeValue,
    R.id.oedRowRegistration to R.id.oedRegistrationValue,
    R.id.oedRowOrganizer to R.id.oedOrganizerValue,
    R.id.oedRowCoOrganizers to R.id.oedCoOrganizersValue,
)
