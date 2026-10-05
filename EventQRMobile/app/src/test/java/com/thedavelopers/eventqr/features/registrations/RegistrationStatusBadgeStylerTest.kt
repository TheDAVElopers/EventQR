package com.thedavelopers.eventqr.features.registrations

import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RegistrationStatusBadgeStylerTest {

    @Test
    fun displayLabel_mapsAllStatusesCorrectly() {
        assertEquals("Registered", RegistrationStatusBadgeStyler.displayLabel(RegistrationStatus.REGISTERED))
        assertEquals("Checked In", RegistrationStatusBadgeStyler.displayLabel(RegistrationStatus.ENTERED))
        assertEquals("Exited", RegistrationStatusBadgeStyler.displayLabel(RegistrationStatus.EXITED))
        assertEquals("Cancelled", RegistrationStatusBadgeStyler.displayLabel(RegistrationStatus.CANCELLED))
        assertEquals("No Show", RegistrationStatusBadgeStyler.displayLabel(RegistrationStatus.NO_SHOW))
    }
}
