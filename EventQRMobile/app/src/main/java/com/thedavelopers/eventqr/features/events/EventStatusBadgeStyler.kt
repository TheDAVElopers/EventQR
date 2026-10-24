package com.thedavelopers.eventqr.features.events

import android.content.Context
import android.util.Log
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import java.time.Instant

/**
 * Single source of truth for mapping an event's lifecycle [EventStatus] to its
 * display label and color/badge styling across the whole app (Organizer,
 * Attendee, Staff, Admin screens).
 *
 * Mirrors [com.thedavelopers.eventqr.features.registrations.RegistrationStatusBadgeStyler];
 * that class handles registration statuses and is intentionally out of scope here.
 */
object EventStatusBadgeStyler {

    private const val TAG = "EventStatusBadgeStyler"

    @ColorRes
    fun textColorRes(status: EventStatus): Int = when (status) {
        EventStatus.APPROVED -> R.color.event_status_upcoming
        EventStatus.ACTIVE -> R.color.event_status_active
        EventStatus.ENDED -> R.color.event_status_completed
        EventStatus.DRAFT -> R.color.event_status_draft
        EventStatus.PENDING_REVIEW -> R.color.event_status_pending_review
        EventStatus.REJECTED -> R.color.event_status_rejected
        EventStatus.CANCELLED -> R.color.event_status_cancelled
        EventStatus.UNKNOWN -> R.color.event_status_unknown
    }

    @DrawableRes
    fun backgroundRes(status: EventStatus): Int = when (status) {
        EventStatus.APPROVED -> R.drawable.bg_event_badge_upcoming
        EventStatus.ACTIVE -> R.drawable.bg_event_badge_active
        EventStatus.ENDED -> R.drawable.bg_event_badge_completed
        EventStatus.DRAFT -> R.drawable.bg_event_badge_draft
        EventStatus.PENDING_REVIEW -> R.drawable.bg_event_badge_pending_review
        EventStatus.REJECTED -> R.drawable.bg_event_badge_rejected
        EventStatus.CANCELLED -> R.drawable.bg_event_badge_cancelled
        EventStatus.UNKNOWN -> R.drawable.bg_event_badge_unknown
    }

    /** Rounded-square date badge background per status. */
    @DrawableRes
    fun dateBadgeRes(status: EventStatus): Int = when (status) {
        EventStatus.APPROVED -> R.drawable.bg_dashboard_event_date_upcoming
        EventStatus.ACTIVE -> R.drawable.bg_dashboard_event_date_active
        EventStatus.ENDED -> R.drawable.bg_dashboard_event_date_completed
        EventStatus.DRAFT -> R.drawable.bg_dashboard_event_date_draft
        EventStatus.PENDING_REVIEW -> R.drawable.bg_dashboard_event_date_pending_review
        EventStatus.REJECTED -> R.drawable.bg_dashboard_event_date_rejected
        EventStatus.CANCELLED -> R.drawable.bg_dashboard_event_date_cancelled
        EventStatus.UNKNOWN -> R.drawable.bg_dashboard_event_date_unknown
    }

    /** Saturated primary/accent color for a status (used for top strips, text, progress bars). */
    fun primaryColor(context: Context, status: EventStatus): Int =
        ContextCompat.getColor(context, textColorRes(status))

    /** Applies label + pill background + text color to an existing TextView. */
    fun bind(view: TextView, status: EventStatus, rawLabel: String? = null) {
        view.text = displayLabel(status, rawLabel)
        view.setBackgroundResource(backgroundRes(status))
        view.setTextColor(
            when (status) {
                EventStatus.APPROVED, EventStatus.ACTIVE, EventStatus.ENDED ->
                    ContextCompat.getColor(view.context, R.color.text_primary)
                else -> ContextCompat.getColor(view.context, textColorRes(status))
            },
        )
    }

    fun displayLabel(status: EventStatus, rawLabel: String? = null): String = when (status) {
        EventStatus.DRAFT -> "Draft"
        EventStatus.PENDING_REVIEW -> "Pending Review"
        EventStatus.APPROVED -> "Upcoming"
        EventStatus.REJECTED -> "Rejected"
        EventStatus.ACTIVE -> "Active"
        EventStatus.ENDED -> "Completed"
        EventStatus.CANCELLED -> "Cancelled"
        EventStatus.UNKNOWN -> if (!rawLabel.isNullOrBlank()) "Status: $rawLabel" else "Status not recognized"
    }

    /**
     * Parses a raw backend status string, display label, or raw enum name back into an
     * [EventStatus]. Used by screens that receive a status as a string (e.g. the organizer
     * MVP events) so they can still reuse the centralized styling. Unknown/empty input is
     * logged and mapped to [EventStatus.UNKNOWN], which renders as a neutral gray badge
     * showing the raw value.
     */
    fun fromLabel(label: String?): EventStatus {
        val l = label?.lowercase() ?: ""
        return when {
            l.contains("completed") || l.contains("ended") -> EventStatus.ENDED
            l.contains("active") || l.contains("ongoing") -> EventStatus.ACTIVE
            l.contains("upcoming") || l.contains("approved") -> EventStatus.APPROVED
            l.contains("draft") -> EventStatus.DRAFT
            l.contains("pending") || l.contains("review") -> EventStatus.PENDING_REVIEW
            l.contains("reject") -> EventStatus.REJECTED
            l.contains("cancel") -> EventStatus.CANCELLED
            else -> {
                Log.w(TAG, "Unknown event status label: $label")
                EventStatus.UNKNOWN
            }
        }
    }

    /**
     * Date-derived status used as the primary display rule, matching the pill filters
     * (AttendeeEventsActivity) that derive state from eventStartAt/eventEndAt vs. now.
     * Maps "before start" -> [EventStatus.APPROVED] (Upcoming),
     * "past end" -> [EventStatus.ENDED] (Completed), otherwise [EventStatus.ACTIVE].
     */
    fun fromDates(startAt: Instant?, endAt: Instant?, now: Instant = Instant.now()): EventStatus {
        val completed = endAt != null && endAt.isBefore(now)
        val upcoming = startAt != null && startAt.isAfter(now)
        return when {
            completed -> EventStatus.ENDED
            upcoming -> EventStatus.APPROVED
            else -> EventStatus.ACTIVE
        }
    }

    /**
     * Resolves the displayed status from dates as the PRIMARY rule, so badges agree with the
     * ACTIVE/UPCOMING/COMPLETED pill filters (which derive purely from eventStartAt/eventEndAt
     * vs. now). Only statuses that are NOT time-derived (DRAFT, PENDING_REVIEW, REJECTED,
     * CANCELLED) are kept from the backend `status` field. Time-derived backend values
     * (APPROVED, ACTIVE, ENDED) are overridden by the date computation, since the backend may
     * stamp ACTIVE before start time (e.g. admin-approved creation requests).
     */
    fun resolve(status: EventStatus?, startAt: Instant?, endAt: Instant?, now: Instant = Instant.now()): EventStatus {
        val effective = status ?: EventStatus.APPROVED
        return when (effective) {
            EventStatus.DRAFT,
            EventStatus.PENDING_REVIEW,
            EventStatus.REJECTED,
            EventStatus.CANCELLED,
            EventStatus.UNKNOWN,
            -> effective
            else -> fromDates(startAt, endAt, now)
        }
    }
}
