package com.thedavelopers.eventqr.features.notifications

import android.content.Context
import android.content.Intent
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_ID as ATTENDEE_EVENT_ID
import com.thedavelopers.eventqr.features.attendee.EventDetailActivity
import com.thedavelopers.eventqr.features.attendee.MyEventRequestsActivity
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID as ORGANIZER_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
import com.thedavelopers.eventqr.features.organizer.events.OrganizerEventDetailsActivity

/** Where a notification opens. Decided once here so the row chevron, the in-app tap and the shade tap agree. */
internal enum class NotificationDestination {
    ORGANIZER_HUB,
    ORGANIZER_EVENT_DETAILS,
    ATTENDEE_EVENT_DETAIL,
    /** The requester's own request list: the only place `adminRemarks` is readable without an event. */
    ATTENDEE_REQUESTS,
}

/**
 * The destination a notification type points at, or null when there is nothing to open.
 * Role independent on purpose: the chevron only promises that a tap goes somewhere. A rejection
 * always goes somewhere, so it always keeps its chevron, even without an event row.
 */
internal fun notificationDestinationFor(type: NotificationType?, eventId: String?): NotificationDestination? =
    when {
        type == NotificationType.EVENT_REJECTED ->
            if (eventId.isNullOrBlank()) NotificationDestination.ATTENDEE_REQUESTS
            else NotificationDestination.ORGANIZER_EVENT_DETAILS
        eventId.isNullOrBlank() -> null
        else -> NotificationDestination.ORGANIZER_HUB
    }

/**
 * [notificationDestinationFor] with the role applied, so a tap never opens a screen the reader's
 * role cannot see. A rejection always opens something: an organizer with an event row sees the
 * event, and everyone else - including a rejection of a request that never became an event -
 * reads the decision in their own request list.
 */
internal fun resolvedDestination(
    type: NotificationType?,
    eventId: String?,
    organizer: Boolean,
): NotificationDestination? =
    when {
        type == NotificationType.EVENT_REJECTED ->
            if (organizer && !eventId.isNullOrBlank()) NotificationDestination.ORGANIZER_EVENT_DETAILS
            else NotificationDestination.ATTENDEE_REQUESTS
        eventId.isNullOrBlank() -> null
        organizer -> NotificationDestination.ORGANIZER_HUB
        else -> NotificationDestination.ATTENDEE_EVENT_DETAIL
    }

internal fun resolvedDestination(context: Context, type: NotificationType?, eventId: String?): NotificationDestination? =
    resolvedDestination(type, eventId, RoleMapper.isAtLeast(SessionManager(context).getUserRole(), AccountRole.ORGANIZER))

/** The intent a notification-shade tap launches, matching the in-app tap destination. */
internal fun notificationIntent(context: Context, type: NotificationType?, eventId: String?): Intent? =
    when (resolvedDestination(context, type, eventId)) {
        NotificationDestination.ORGANIZER_HUB ->
            Intent(context, EventManagementHubActivity::class.java).putExtra(ORGANIZER_EVENT_ID, eventId)
        NotificationDestination.ORGANIZER_EVENT_DETAILS ->
            Intent(context, OrganizerEventDetailsActivity::class.java).putExtra(ORGANIZER_EVENT_ID, eventId)
        NotificationDestination.ATTENDEE_EVENT_DETAIL ->
            Intent(context, EventDetailActivity::class.java).putExtra(ATTENDEE_EVENT_ID, eventId)
        NotificationDestination.ATTENDEE_REQUESTS ->
            Intent(context, MyEventRequestsActivity::class.java)
        null -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
