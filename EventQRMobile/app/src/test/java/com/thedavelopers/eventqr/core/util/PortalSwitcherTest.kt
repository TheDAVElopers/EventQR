package com.thedavelopers.eventqr.core.util

import com.thedavelopers.eventqr.core.api.dto.AccountRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Regression tests for the portal switcher role -> portals mapping.
 *
 * Previously the portal list was built with cumulative "role fan-out" conditionals that
 * granted Staff/Organizer/Admin portals to ADMIN and SUPER_ADMIN accounts (and let any
 * unexpected role string fall through to a wrong list). Every user must only see portals
 * for the role they actually hold: Attendee (default) plus exactly their own elevated role.
 */
class PortalSwitcherTest {

    @Test
    fun attendee_seesOnlyAttendeePortal() {
        assertEquals(
            listOf(PortalSwitcher.PORTAL_ATTENDEE),
            PortalSwitcher.portalsForRole(AccountRole.ATTENDEE.name),
        )
    }

    @Test
    fun user_roleIsNormalizedToAttendeeBeforeMapping() {
        // RoleMapper maps "USER" -> "ATTENDEE"; the switcher must never receive the raw form.
        assertEquals(
            listOf(PortalSwitcher.PORTAL_ATTENDEE),
            PortalSwitcher.portalsForRole(AccountRole.USER.name),
        )
    }

    @Test
    fun staff_seesStaffPlusAttendeeOnly() {
        assertEquals(
            listOf(PortalSwitcher.PORTAL_ATTENDEE, PortalSwitcher.PORTAL_STAFF),
            PortalSwitcher.portalsForRole(AccountRole.STAFF.name),
        )
    }

    @Test
    fun organizer_seesOrganizerPlusAttendeeOnly() {
        assertEquals(
            listOf(PortalSwitcher.PORTAL_ATTENDEE, PortalSwitcher.PORTAL_ORGANIZER),
            PortalSwitcher.portalsForRole(AccountRole.ORGANIZER.name),
        )
    }

    @Test
    fun admin_seesAdminPlusAttendeeOnly_neverStaffOrOrganizer() {
        assertEquals(
            listOf(PortalSwitcher.PORTAL_ATTENDEE, PortalSwitcher.PORTAL_ADMIN),
            PortalSwitcher.portalsForRole(AccountRole.ADMIN.name),
        )
    }

    @Test
    fun superAdmin_seesSuperAdminPlusAttendeeOnly_neverPlainAdmin() {
        assertEquals(
            listOf(PortalSwitcher.PORTAL_ATTENDEE, PortalSwitcher.PORTAL_SUPER_ADMIN),
            PortalSwitcher.portalsForRole(AccountRole.SUPER_ADMIN.name),
        )
    }

    @Test
    fun attendee_neverOfferedElevatedPortals() {
        val attendeePortals =
            PortalSwitcher.portalsForRole(AccountRole.ATTENDEE.name) +
                PortalSwitcher.portalsForRole(AccountRole.USER.name)
        listOf(
            PortalSwitcher.PORTAL_STAFF,
            PortalSwitcher.PORTAL_ORGANIZER,
            PortalSwitcher.PORTAL_ADMIN,
            PortalSwitcher.PORTAL_SUPER_ADMIN,
        ).forEach { elevated ->
            assertFalse(elevated, attendeePortals.contains(elevated))
        }
    }

    @Test
    fun unknownOrBlankRole_cannotLeakElevatedPortals() {
        assertEquals(
            listOf(PortalSwitcher.PORTAL_ATTENDEE),
            PortalSwitcher.portalsForRole("CUSTOMER"),
        )
        assertEquals(
            listOf(PortalSwitcher.PORTAL_ATTENDEE),
            PortalSwitcher.portalsForRole(""),
        )
        assertEquals(
            listOf(PortalSwitcher.PORTAL_ATTENDEE),
            PortalSwitcher.portalsForRole(null),
        )
    }
}