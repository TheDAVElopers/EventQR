package com.thedavelopers.eventqr.features.registrations

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.ui.components.RegistrationBadges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * §5.4: one mapping, sentence case, `Entered` is the only valid (lime) tone.
 *
 * The old assertion set encoded the retired Title-Case labels ("Checked In", "No Show"), so the
 * labels are now asserted by resource id - the strings themselves live in strings_ui_attendee.xml.
 */
class RegistrationStatusBadgeStylerTest {

    @Test
    fun labelRes_mapsAllStatuses() {
        assertEquals(R.string.ui_attendee_registered, RegistrationBadges.labelRes(RegistrationStatus.REGISTERED))
        assertEquals(R.string.ui_attendee_entered, RegistrationBadges.labelRes(RegistrationStatus.ENTERED))
        assertEquals(R.string.ui_attendee_exited, RegistrationBadges.labelRes(RegistrationStatus.EXITED))
        assertEquals(R.string.ui_attendee_cancelled, RegistrationBadges.labelRes(RegistrationStatus.CANCELLED))
        assertEquals(R.string.ui_attendee_no_show, RegistrationBadges.labelRes(RegistrationStatus.NO_SHOW))
    }

    @Test
    fun badgeStyle_enteredIsTheOnlyValidTone() {
        assertEquals(
            R.style.Widget_EventQR_Ui_StatusBadge_Valid,
            RegistrationBadges.badgeStyleFor(RegistrationStatus.ENTERED),
        )
        listOf(
            RegistrationStatus.REGISTERED,
            RegistrationStatus.EXITED,
            RegistrationStatus.CANCELLED,
            RegistrationStatus.NO_SHOW,
        ).forEach { status ->
            assertEquals(
                status.name,
                R.style.Widget_EventQR_Ui_StatusBadge_Neutral,
                RegistrationBadges.badgeStyleFor(status),
            )
        }
    }

    @Test
    fun labelRes_isDistinctPerStatus() {
        val ids = RegistrationStatus.entries.map { RegistrationBadges.labelRes(it) }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun nullStatusFallsBackToRegistered() {
        assertEquals(R.string.ui_attendee_registered, RegistrationBadges.labelRes(null))
        assertNotEquals(
            R.style.Widget_EventQR_Ui_StatusBadge_Valid,
            RegistrationBadges.badgeStyleFor(null),
        )
    }
}
