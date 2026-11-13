package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The hub's on-dark status line dot (plan §5.4 on-dark exception). One role per tone: lime only for
 * the Active confirmation, the three on-dark status roles for the tones that need them, and the
 * muted word colour for every neutral state. A status without a role fails here.
 */
class AttendeeBadgesTest {

    @Test
    fun onDarkDotColorRes_mapsEveryEventStatus() {
        val expected = mapOf(
            EventStatus.ACTIVE to R.color.ui_accent,
            EventStatus.APPROVED to R.color.ui_status_dot_on_dark_info,
            EventStatus.PENDING_REVIEW to R.color.ui_status_dot_on_dark_warning,
            EventStatus.REJECTED to R.color.ui_status_dot_on_dark_error,
            EventStatus.DRAFT to R.color.ui_text_on_dark_muted,
            EventStatus.ENDED to R.color.ui_text_on_dark_muted,
            EventStatus.CANCELLED to R.color.ui_text_on_dark_muted,
            EventStatus.UNKNOWN to R.color.ui_text_on_dark_muted,
        )

        // The map covers the enum, so a new status cannot ship without a dot role.
        assertEquals(EventStatus.values().toSet(), expected.keys)

        expected.forEach { (status, colorRes) ->
            assertEquals("$status dot role", colorRes, AttendeeBadges.onDarkDotColorRes(status))
        }
    }
}
