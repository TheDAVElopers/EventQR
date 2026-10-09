package com.thedavelopers.eventqr.features.dashboard

import com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import com.thedavelopers.eventqr.features.landing.LandingActivity
import com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleReroutePolicyTest {

    @Test
    fun attendeeDashboardReroutesWhenRoleBecomesOrganizer() {
        assertTrue(RoleReroutePolicy.shouldReroute(DashboardActivity::class.java, "ORGANIZER"))
    }

    @Test
    fun matchingDashboardDoesNotReroute() {
        assertFalse(RoleReroutePolicy.shouldReroute(OrganizerDashboardActivity::class.java, "ORGANIZER"))
        assertFalse(RoleReroutePolicy.shouldReroute(DashboardActivity::class.java, "user"))
        assertFalse(RoleReroutePolicy.shouldReroute(AdminDashboardActivity::class.java, "SUPER_ADMIN"))
    }

    @Test
    fun demotionReroutesToAttendeeDashboard() {
        assertTrue(RoleReroutePolicy.shouldReroute(StaffDashboardActivity::class.java, "ATTENDEE"))
    }

    @Test
    fun nonDashboardScreensNeverReroute() {
        assertFalse(RoleReroutePolicy.shouldReroute(LoginActivity::class.java, "ORGANIZER"))
        assertFalse(RoleReroutePolicy.shouldReroute(RegistrationActivity::class.java, "ORGANIZER"))
        assertFalse(RoleReroutePolicy.shouldReroute(LandingActivity::class.java, "ORGANIZER"))
        assertFalse(RoleReroutePolicy.shouldReroute(null, "ORGANIZER"))
    }

    @Test
    fun destinationsMatchRoles() {
        assertEquals(StaffDashboardActivity::class.java, DashboardRouter.destinationFor("staff"))
        assertEquals(AdminDashboardActivity::class.java, DashboardRouter.destinationFor("ADMIN"))
        assertEquals(DashboardActivity::class.java, DashboardRouter.destinationFor(null))
    }
}
