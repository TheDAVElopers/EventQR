package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import java.util.UUID

interface EventsContract {
    interface View : AttendeeView {
        fun showEvents(items: List<AttendeeEventResponse>)

        /**
         * Event ids with a live registration (`DashboardStats.isLive`), or `null` when the
         * first-ever registration load failed. A later refresh failure keeps the last good set;
         * `null` must render an honest retry state for the `Registered` chip, never an empty
         * result (§6.2).
         */
        fun showRegisteredEventIds(ids: Set<UUID>?)
        fun showError(message: String)
    }
}
