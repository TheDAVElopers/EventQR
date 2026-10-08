package com.thedavelopers.eventqr.features.organizer.staff

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff
import com.thedavelopers.eventqr.features.organizer.buildStaffAssignmentRequest
import com.thedavelopers.eventqr.features.organizer.buildStaffUpdateRequest
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerStaffDto
import com.thedavelopers.eventqr.features.organizer.transactions.buildTransactionRuleRequest
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionRuleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StaffPermissionsTest {
    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()

    private fun user(role: String = "STAFF") = OrganizerMvpStaff(
        id = UUID.randomUUID().toString(),
        name = "Maria",
        email = "maria@x.com",
        assignedEventId = UUID.randomUUID().toString(),
        assignedEvent = "Expo",
        roleLabel = "Scanner",
        accessStatus = "Active",
        addedDate = "-",
        permissions = listOf("Scan QR"),
        accountRole = role,
    )

    @Test
    fun switches_defaultToScanOnAndOthersOff() {
        val form = StaffPermissionsForm(context)
        assertTrue(form.scanSwitch.isChecked)
        assertFalse("Scan QR is always on and locked", form.scanSwitch.isEnabled)
        assertFalse(form.printIdSwitch.isChecked)
        assertFalse(form.viewLogsSwitch.isChecked)
        assertFalse(form.manageRewardsSwitch.isChecked)
    }

    @Test
    fun addPayload_carriesTheChosenFlags() {
        val form = StaffPermissionsForm(context)
        form.printIdSwitch.isChecked = true
        form.manageRewardsSwitch.isChecked = true
        val request = buildStaffAssignmentRequest(form.applyTo(user()))

        assertEquals(true, request.canScan)
        assertEquals(true, request.canPrintId)
        assertEquals(false, request.canViewLogs)
        assertEquals(true, request.canManageRewards)
        val json = sharedGson().toJsonTree(request).asJsonObject
        assertEquals(true, json.get("canPrintId").asBoolean)
        assertEquals(false, json.get("canViewLogs").asBoolean)
    }

    @Test
    fun defaultsSendOffFlags() {
        val request = buildStaffAssignmentRequest(StaffPermissionsForm(context).applyTo(user()))
        assertEquals(true, request.canScan)
        assertEquals(false, request.canPrintId)
        assertEquals(false, request.canViewLogs)
        assertEquals(false, request.canManageRewards)
    }

    @Test
    fun editPrefillsFromTheRow_andUpdatePayloadUsesTheAssignmentFlags() {
        val row = user().copy(canPrintId = true, canViewLogs = false, canManageRewards = false)
        val form = StaffPermissionsForm(context, row)
        assertTrue(form.printIdSwitch.isChecked)
        form.viewLogsSwitch.isChecked = true

        val update = buildStaffUpdateRequest(form.applyTo(row))
        assertEquals(true, update.canPrintId)
        assertEquals(true, update.canViewLogs)
        assertEquals(false, update.canManageRewards)
        assertEquals(true, update.active)
    }

    @Test
    fun rowSummary_showsRealPermissions_notHardCodedText() {
        assertEquals("Scan, Print IDs", staffPermissionSummary(context, user().copy(canPrintId = true)))
        assertEquals("Scan", staffPermissionSummary(context, user()))
        assertEquals(
            "Scan, Print IDs, View logs, Manage rewards",
            staffPermissionSummary(context, user().copy(canPrintId = true, canViewLogs = true, canManageRewards = true)),
        )
    }

    @Test
    fun staffDto_exposesAssignmentIdAndPromotedFlag() {
        val id = UUID.randomUUID()
        val json = """{"assignmentId":"$id","eventId":"${UUID.randomUUID()}","staffUserId":"${UUID.randomUUID()}",
            "canScan":true,"canPrintId":true,"promotedToStaff":true}"""
        val dto = sharedGson().fromJson(json, OrganizerStaffDto::class.java)
        assertEquals(id, dto.assignmentId)
        assertTrue(dto.promotedToStaff)
        assertTrue(dto.canPrintId)
    }

    @Test
    fun attendeeAccount_getsPromotionConfirmation_withTheNameInTheMessage() {
        assertTrue(user(role = "ATTENDEE").willBePromotedToStaff)
        assertFalse(user(role = "STAFF").willBePromotedToStaff)
        assertEquals(
            "Maria will become a Staff member. They keep their attendee access.",
            context.getString(R.string.staff_promote_message, "Maria"),
        )
        assertEquals(
            "Maria is now a Staff member. They keep their attendee access.",
            context.getString(R.string.staff_promoted_toast, "Maria"),
        )
    }

    @Test
    fun transactionRuleSave_keepsExistingActiveAndPoints() {
        val purpose = UUID.randomUUID()
        val existing = OrganizerTransactionRuleDto(
            eventId = UUID.randomUUID(),
            scanPurposeId = purpose,
            active = false,
            pointsAwarded = 7,
        )
        val request = buildTransactionRuleRequest(purpose, existing, allowDuplicate = true, requiresStaff = false, cooldown = 5, maxScans = 3)
        assertFalse("active must not be forced to true", request.active)
        assertEquals(7, request.pointsAwarded)
        assertEquals(3, request.maxUsesPerRegistration)

        assertTrue(buildTransactionRuleRequest(purpose, null, false, true, 0, 1).active)
    }
}
