package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse

interface EventRegistrationsContract {
    interface View {
        /** The full list loaded so far for the current search query (grows as pages are appended). */
        fun renderRegistrations(items: List<RegistrationResponse>, hasQuery: Boolean)

        /** Tile values; null means "unknown" and is shown as "--". */
        fun renderCounts(counts: RegistrationCounts)

        /** Select every item of [items] for ID printing (the complete result set, not just the loaded pages). */
        fun selectAllForPrint(items: List<RegistrationResponse>)

        /** A page failed to load: show [message] with a retry that calls the presenter's retry(). */
        fun showLoadMoreError(message: String)

        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}
