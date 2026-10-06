package com.thedavelopers.eventqr.features

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.features.admin.logs.AdminAuditLogsActivity
import com.thedavelopers.eventqr.features.admin.users.AdminAccountManagementActivity
import com.thedavelopers.eventqr.features.admin.users.CreateAdminAccountActivity
import com.thedavelopers.eventqr.features.attendee.AttendeeProfileActivity
import com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity
import com.thedavelopers.eventqr.features.staff.EventRegistrationsActivity
import com.thedavelopers.eventqr.features.staff.StaffAssignedEventsActivity
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffProfileActivity
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.StaffTransactionsActivity
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import com.thedavelopers.eventqr.features.staff.notifications.StaffNotificationsActivity
import com.thedavelopers.eventqr.features.staff.result.StaffScanResultActivity
import com.thedavelopers.eventqr.features.staff.result.StaffTransactionResultActivity
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeyBuilder::class, ShadowEncryptedSharedPreferences::class])
class RoleGuardMatrixTest {

    private lateinit var sessionManager: SessionManager

    private val staffFloorActivities: List<Class<out Activity>> = listOf(
        StaffDashboardActivity::class.java,
        StaffProfileActivity::class.java,
        StaffTransactionsActivity::class.java,
        StaffNotificationsActivity::class.java,
        ScannerActivity::class.java,
        EventRegistrationsActivity::class.java,
        StaffAssignedEventsActivity::class.java,
        StaffAttendeeDetailsActivity::class.java,
        StaffTransactionResultActivity::class.java,
        StaffScanResultActivity::class.java,
    )

    @Before
    fun setUp() {
        sessionManager = SessionManager(ApplicationProvider.getApplicationContext())
        sessionManager.clearSession()
    }

    private fun intentFor(activity: Class<out Activity>): Intent {
        val intent = Intent()
        if (activity == StaffAttendeeDetailsActivity::class.java) {
            intent.putExtra(StaffScreenExtras.EXTRA_EVENT_ID, "1")
            intent.putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, "1")
        }
        return intent
    }

    private fun launch(activity: Class<out Activity>, role: AccountRole?): Activity {
        if (role == null) {
            sessionManager.clearSession()
        } else {
            sessionManager.saveRole(role)
        }
        return Robolectric.buildActivity(activity, intentFor(activity))
            .create()
            .get()
    }

    private fun denies(activity: Class<out Activity>, role: AccountRole?): Boolean {
        return launch(activity, role).isFinishing
    }

    @Test
    fun attendeeProfile_admitsEveryKnownRole() {
        listOf(
            AccountRole.ATTENDEE,
            AccountRole.STAFF,
            AccountRole.ORGANIZER,
            AccountRole.ADMIN,
            AccountRole.SUPER_ADMIN,
        ).forEach { role ->
            assertFalse(role.name, denies(AttendeeProfileActivity::class.java, role))
        }
    }

    @Test
    fun attendeeProfile_failsClosedOnUnknownRole() {
        assertTrue(denies(AttendeeProfileActivity::class.java, null))
    }

    @Test
    fun staffFloorActivities_denyPlainAttendee() {
        staffFloorActivities.forEach { activity ->
            assertTrue(activity.simpleName, denies(activity, AccountRole.ATTENDEE))
        }
    }

    @Test
    fun staffFloorActivities_admitOrganizerAndAbove() {
        listOf(
            AccountRole.ORGANIZER,
            AccountRole.ADMIN,
            AccountRole.SUPER_ADMIN,
        ).forEach { role ->
            staffFloorActivities.forEach { activity ->
                assertFalse("${activity.simpleName} / ${role.name}", denies(activity, role))
            }
        }
    }

    @Test
    fun adminAuditLogs_denyPlainAttendee() {
        assertTrue(denies(AdminAuditLogsActivity::class.java, AccountRole.ATTENDEE))
    }

    @Test
    fun adminAuditLogs_allowAdminAndSuperAdmin() {
        assertFalse(denies(AdminAuditLogsActivity::class.java, AccountRole.ADMIN))
        assertFalse(denies(AdminAuditLogsActivity::class.java, AccountRole.SUPER_ADMIN))
    }

    @Test
    fun createAdminAccount_deniesAttendeeAndAdmin_superOnlyHeld() {
        assertTrue(denies(CreateAdminAccountActivity::class.java, AccountRole.ATTENDEE))
        assertTrue(denies(CreateAdminAccountActivity::class.java, AccountRole.ORGANIZER))
        assertTrue(denies(CreateAdminAccountActivity::class.java, AccountRole.ADMIN))
        assertFalse(denies(CreateAdminAccountActivity::class.java, AccountRole.SUPER_ADMIN))
    }

    @Test
    fun adminAccountManagement_superOnlyCeilingOnCreateAdminAction() {
        listOf(
            AccountRole.ATTENDEE to View.GONE,
            AccountRole.STAFF to View.GONE,
            AccountRole.ORGANIZER to View.GONE,
            AccountRole.ADMIN to View.GONE,
            AccountRole.SUPER_ADMIN to View.VISIBLE,
        ).forEach { (role, expectedVisibility) ->
            val activity = launch(AdminAccountManagementActivity::class.java, role)
            assertEquals(
                role.name,
                expectedVisibility,
                activity.findViewById<View>(R.id.buttonCreateAdminAccount).visibility
            )
        }
    }

    @Test
    fun organizerSwitchToAttendeePortal_leavesSessionRoleUnchanged() {
        sessionManager.saveRole(AccountRole.ORGANIZER)
        val activity = Robolectric.buildActivity(OrganizerDashboardActivity::class.java).create().get()
        val roleBefore = sessionManager.getUserRole()

        val switchToPortal = OrganizerDashboardActivity::class.java
            .getDeclaredMethod("switchToPortal", String::class.java)
        switchToPortal.isAccessible = true
        switchToPortal.invoke(activity, PortalSwitcher.PORTAL_ATTENDEE)

        assertEquals(roleBefore, sessionManager.getUserRole())
        assertEquals(AccountRole.ORGANIZER.name, sessionManager.getUserRole())
        assertTrue(activity.isFinishing)
    }
}
