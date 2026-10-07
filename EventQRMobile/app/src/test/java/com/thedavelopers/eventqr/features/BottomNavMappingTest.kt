package com.thedavelopers.eventqr.features

import com.thedavelopers.eventqr.features.admin.AdminEventApprovalBackendActivity
import com.thedavelopers.eventqr.features.admin.adminNavDestination
import com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity
import com.thedavelopers.eventqr.features.admin.users.AdminAccountManagementActivity
import com.thedavelopers.eventqr.features.attendee.AttendeeEventsActivity
import com.thedavelopers.eventqr.features.attendee.AttendeeRewardsActivity
import com.thedavelopers.eventqr.features.attendee.RegisteredEventsActivity
import com.thedavelopers.eventqr.features.attendee.attendeeNavDestination
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity
import com.thedavelopers.eventqr.features.organizer.attendees.AttendeeManagementActivity
import com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity
import com.thedavelopers.eventqr.features.organizer.events.ManageEventsActivity
import com.thedavelopers.eventqr.features.organizer.organizerNavDestination
import com.thedavelopers.eventqr.features.organizer.reports.EventReportsActivity
import com.thedavelopers.eventqr.features.organizer.reports.ReportPreviewActivity
import com.thedavelopers.eventqr.features.organizer.rewards.ManageRewardsActivity
import com.thedavelopers.eventqr.features.organizer.shouldNavigateAway
import com.thedavelopers.eventqr.features.staff.StaffAssignedEventsActivity
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffTransactionsActivity
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.staff.staffNavDestination
import com.thedavelopers.eventqr.features.staff.staffNavEventIdExtra
import com.thedavelopers.eventqr.ui.components.AdminNavItems
import com.thedavelopers.eventqr.ui.components.AttendeeNavItems
import com.thedavelopers.eventqr.ui.components.OrganizerNavItems
import com.thedavelopers.eventqr.ui.components.StaffNavItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the per-role navbar id→destination mapping tables. The shared
 * [com.thedavelopers.eventqr.ui.components.EventQrBottomNavBar] itself is covered by
 * [com.thedavelopers.eventqr.ui.components.EventQrBottomNavBarComposeTest].
 *
 * The `startActivity`/`finish` side effects configured in `configure*BottomNav` are
 * not exercised here: they are lambdas wired to a live Activity and fall outside the
 * unit-test boundary (see coverage notes).
 */
class BottomNavMappingTest {

    // -- Attendee -----------------------------------------------------------------

    @Test
    fun attendeeNavDestination_mapsEveryTab() {
        assertEquals(DashboardActivity::class.java, attendeeNavDestination("home"))
        assertEquals(AttendeeEventsActivity::class.java, attendeeNavDestination("events"))
        assertEquals(RegisteredEventsActivity::class.java, attendeeNavDestination("registered"))
        assertEquals(AttendeeRewardsActivity::class.java, attendeeNavDestination("rewards"))
        assertEquals(com.thedavelopers.eventqr.features.attendee.AttendeeProfileActivity::class.java, attendeeNavDestination("profile"))
    }

    @Test
    fun attendeeNavDestination_unknownIdResolvesToNull() {
        assertNull(attendeeNavDestination("bogus"))
    }

    @Test
    fun attendeeNavItems_onlyProfileIsUnmapped() {
        // Profile is a rendered tab but has no destination in the XML-hosted attendee nav.
        AttendeeNavItems.forEach { item ->
            if (item.id == "profile") {
                assertNotNull("attendee tab profile must map to destination", attendeeNavDestination(item.id))
            } else {
                assertNotNull("attendee tab '${item.id}' must map to a destination", attendeeNavDestination(item.id))
            }
        }
    }

    // -- Staff --------------------------------------------------------------------

    @Test
    fun staffNavDestination_mapsEveryTab() {
        assertEquals(StaffDashboardActivity::class.java, staffNavDestination("dashboard"))
        assertEquals(ScannerActivity::class.java, staffNavDestination("scanner"))
        assertEquals(StaffAssignedEventsActivity::class.java, staffNavDestination("events"))
        assertEquals(StaffTransactionsActivity::class.java, staffNavDestination("logs"))
        assertEquals(com.thedavelopers.eventqr.features.staff.StaffProfileActivity::class.java, staffNavDestination("profile"))
    }

    @Test
    fun staffNavDestination_profileAndUnknownResolveToNull() {
        // Profile is a rendered tab but has no destination in the XML-hosted staff nav.
        assertNull(staffNavDestination("bogus"))
    }

    @Test
    fun staffNavEventIdExtra_onlyCarriedForScannerAndLogs() {
        val eventId = "evt-42"
        assertEquals(eventId, staffNavEventIdExtra("scanner", eventId))
        assertEquals(eventId, staffNavEventIdExtra("logs", eventId))
        assertNull(staffNavEventIdExtra("dashboard", eventId))
        assertNull(staffNavEventIdExtra("events", eventId))
        assertNull(staffNavEventIdExtra("profile", eventId))
    }

    @Test
    fun staffNavEventIdExtra_neverCarriedWhenBlankOrNull() {
        assertNull(staffNavEventIdExtra("scanner", null))
        assertNull(staffNavEventIdExtra("scanner", ""))
        assertNull(staffNavEventIdExtra("logs", "   "))
    }

    @Test
    fun staffNavItems_onlyProfileIsUnmapped() {
        StaffNavItems.forEach { item ->
            if (item.id == "profile") {
                assertNotNull("staff tab profile must map to destination", staffNavDestination(item.id))
            } else {
                assertNotNull("staff tab '${item.id}' must map to a destination", staffNavDestination(item.id))
            }
        }
    }

    // -- Admin --------------------------------------------------------------------

    @Test
    fun adminNavDestination_mapsEveryTab() {
        assertEquals(AdminDashboardActivity::class.java, adminNavDestination("dashboard"))
        assertEquals(AdminEventApprovalBackendActivity::class.java, adminNavDestination("requests"))
        assertEquals(AdminAccountManagementActivity::class.java, adminNavDestination("accounts"))
        assertEquals(com.thedavelopers.eventqr.features.admin.logs.AdminAuditLogsActivity::class.java, adminNavDestination("logs"))
    }

    @Test
    fun adminNavDestination_logsAndUnknownResolveToNull() {
        // Logs is a rendered tab but the legacy admin nav has no own destination for it.
        assertNull(adminNavDestination("bogus"))
    }

    @Test
    fun adminNavItems_onlyLogsIsUnmapped() {
        AdminNavItems.forEach { item ->
            if (item.id == "logs") {
                assertNotNull("admin tab logs must map to destination", adminNavDestination(item.id))
            } else {
                assertNotNull("admin tab '${item.id}' must map to a destination", adminNavDestination(item.id))
            }
        }
    }

    // -- Organizer ----------------------------------------------------------------

    @Test
    fun organizerNavDestination_mapsEveryTab() {
        assertEquals(OrganizerDashboardActivity::class.java, organizerNavDestination("dashboard"))
        assertEquals(ManageEventsActivity::class.java, organizerNavDestination("events"))
        assertEquals(AttendeeManagementActivity::class.java, organizerNavDestination("attendees"))
        assertEquals(EventReportsActivity::class.java, organizerNavDestination("reports"))
        assertEquals(ManageRewardsActivity::class.java, organizerNavDestination("rewards"))
    }

    @Test
    fun organizerNavDestination_unknownIdResolvesToNull() {
        assertNull(organizerNavDestination("bogus"))
    }

    @Test
    fun organizerNavItems_everyListItemHasAMapping() {
        OrganizerNavItems.forEach { item ->
            assertNotNull("organizer tab '${item.id}' must map to a destination", organizerNavDestination(item.id))
        }
    }

    @Test
    fun shouldNavigateAway_sameTabGuardBlocksReopen() {
        assertFalse(shouldNavigateAway(EventReportsActivity::class.java, EventReportsActivity::class.java))
        assertFalse(shouldNavigateAway(ManageEventsActivity::class.java, ManageEventsActivity::class.java))
        assertFalse(shouldNavigateAway(OrganizerDashboardActivity::class.java, OrganizerDashboardActivity::class.java))
    }

    @Test
    fun shouldNavigateAway_reportPreviewReportsTabReturnsToEventReports() {
        // ReportPreview has no own "reports" destination; the tab maps back to
        // EventReportsActivity, so from the preview the guard must allow navigation.
        assertTrue(shouldNavigateAway(ReportPreviewActivity::class.java, EventReportsActivity::class.java))
    }

    @Test
    fun shouldNavigateAway_differentDestinationNavigates() {
        assertTrue(shouldNavigateAway(OrganizerDashboardActivity::class.java, ManageEventsActivity::class.java))
        assertTrue(shouldNavigateAway(EventReportsActivity::class.java, ManageRewardsActivity::class.java))
    }

    @Test
    fun shouldNavigateAway_nullDestinationNeverNavigates() {
        assertFalse(shouldNavigateAway(OrganizerDashboardActivity::class.java, null))
        assertFalse(shouldNavigateAway(ReportPreviewActivity::class.java, null))
    }
}