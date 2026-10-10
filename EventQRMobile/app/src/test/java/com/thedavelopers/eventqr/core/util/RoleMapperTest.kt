package com.thedavelopers.eventqr.core.util

import com.thedavelopers.eventqr.core.api.dto.AccountRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.thedavelopers.eventqr.R

class RoleMapperTest {

    @Test
    fun normalizeRole_mapsUserAndAttendeeVariants() {
        assertEquals(AccountRole.ATTENDEE.name, RoleMapper.normalizeRole("USER"))
        assertEquals(AccountRole.ATTENDEE.name, RoleMapper.normalizeRole("user"))
        assertEquals(AccountRole.ATTENDEE.name, RoleMapper.normalizeRole("ATTENDEE"))
        assertEquals(AccountRole.ATTENDEE.name, RoleMapper.normalizeRole("attendee"))
    }

    @Test
    fun normalizeRole_mapsElevatedRoles() {
        assertEquals(AccountRole.STAFF.name, RoleMapper.normalizeRole("STAFF"))
        assertEquals(AccountRole.STAFF.name, RoleMapper.normalizeRole("staff"))
        assertEquals(AccountRole.ORGANIZER.name, RoleMapper.normalizeRole("ORGANIZER"))
        assertEquals(AccountRole.ORGANIZER.name, RoleMapper.normalizeRole("organizer"))
        assertEquals(AccountRole.ADMIN.name, RoleMapper.normalizeRole("ADMIN"))
        assertEquals(AccountRole.ADMIN.name, RoleMapper.normalizeRole("admin"))
        assertEquals(AccountRole.SUPER_ADMIN.name, RoleMapper.normalizeRole("SUPER_ADMIN"))
        assertEquals(AccountRole.SUPER_ADMIN.name, RoleMapper.normalizeRole("superadmin"))
    }

    @Test
    fun normalizeRole_handlesNullEmptyAndUnknown() {
        assertEquals("", RoleMapper.normalizeRole(null))
        assertEquals("", RoleMapper.normalizeRole(""))
        assertEquals("", RoleMapper.normalizeRole("   "))
        assertEquals("GUEST", RoleMapper.normalizeRole("guest"))
    }

    @Test
    fun getDisplayNameRes_mapsKnownRolesAndAliases() {
        assertEquals(R.string.common_attendee, RoleMapper.getDisplayNameRes("USER"))
        assertEquals(R.string.common_attendee, RoleMapper.getDisplayNameRes("ATTENDEE"))
        assertEquals(null, RoleMapper.getDisplayNameRes("guest"))
        assertEquals(null, RoleMapper.getDisplayNameRes(null))
    }

    @Test
    fun rankOf_assignsExplicitRanks() {
        assertEquals(0, RoleMapper.rankOf(AccountRole.ATTENDEE.name))
        assertEquals(1, RoleMapper.rankOf(AccountRole.STAFF.name))
        assertEquals(2, RoleMapper.rankOf(AccountRole.ORGANIZER.name))
        assertEquals(3, RoleMapper.rankOf(AccountRole.ADMIN.name))
        assertEquals(4, RoleMapper.rankOf(AccountRole.SUPER_ADMIN.name))
    }

    @Test
    fun rankOf_userVariantSharesAttendeeRank() {
        assertEquals(RoleMapper.rankOf(AccountRole.ATTENDEE.name), RoleMapper.rankOf("USER"))
        assertEquals(0, RoleMapper.rankOf("user"))
    }

    @Test
    fun rankOf_organizerOutranksStaff_despiteEnumDeclarationOrder() {
        assertTrue(RoleMapper.rankOf(AccountRole.STAFF.name) < RoleMapper.rankOf(AccountRole.ORGANIZER.name))
        assertTrue(RoleMapper.rankOf(AccountRole.ORGANIZER.name) < RoleMapper.rankOf(AccountRole.ADMIN.name))
        assertTrue(RoleMapper.rankOf(AccountRole.ADMIN.name) < RoleMapper.rankOf(AccountRole.SUPER_ADMIN.name))
    }

    @Test
    fun rankOf_unknownOrNullIsEmptyMinusOne() {
        assertEquals(-1, RoleMapper.rankOf(null))
        assertEquals(-1, RoleMapper.rankOf(""))
        assertEquals(-1, RoleMapper.rankOf("   "))
        assertEquals(-1, RoleMapper.rankOf("MANAGER"))
    }

    @Test
    fun isAtLeast_admitsEveryKnownRoleAtAttendeeFloor() {
        listOf(
            AccountRole.ATTENDEE.name,
            "USER",
            AccountRole.STAFF.name,
            AccountRole.ORGANIZER.name,
            AccountRole.ADMIN.name,
            AccountRole.SUPER_ADMIN.name,
        ).forEach { role ->
            assertTrue(role, RoleMapper.isAtLeast(role, AccountRole.ATTENDEE))
        }
    }

    @Test
    fun isAtLeast_unknownRolesFailClosedEvenAtAttendeeFloor() {
        assertFalse(RoleMapper.isAtLeast(null, AccountRole.ATTENDEE))
        assertFalse(RoleMapper.isAtLeast("", AccountRole.ATTENDEE))
        assertFalse(RoleMapper.isAtLeast("MANAGER", AccountRole.ATTENDEE))
    }

    @Test
    fun isAtLeast_staffFloorRejectsAttendeeAndPassesHigherRoles() {
        assertFalse(RoleMapper.isAtLeast(AccountRole.ATTENDEE.name, AccountRole.STAFF))
        assertFalse(RoleMapper.isAtLeast("USER", AccountRole.STAFF))
        assertTrue(RoleMapper.isAtLeast(AccountRole.STAFF.name, AccountRole.STAFF))
        assertTrue(RoleMapper.isAtLeast(AccountRole.ORGANIZER.name, AccountRole.STAFF))
        assertTrue(RoleMapper.isAtLeast(AccountRole.ADMIN.name, AccountRole.STAFF))
        assertTrue(RoleMapper.isAtLeast(AccountRole.SUPER_ADMIN.name, AccountRole.STAFF))
    }

    @Test
    fun isAtLeast_staffDoesNotMeetOrganizerFloor() {
        assertFalse(RoleMapper.isAtLeast(AccountRole.STAFF.name, AccountRole.ORGANIZER))
        assertTrue(RoleMapper.isAtLeast(AccountRole.ORGANIZER.name, AccountRole.ORGANIZER))
        assertTrue(RoleMapper.isAtLeast(AccountRole.SUPER_ADMIN.name, AccountRole.ORGANIZER))
    }

    @Test
    fun isAtLeast_everyAccountRoleFloorResolvesToKnownRank() {
        AccountRole.values().forEach { floor ->
            assertTrue(floor.name, RoleMapper.rankOf(floor.name) >= 0)
        }
    }

    @Test
    fun isAtLeast_unknownRolesFailClosedAtEveryFloor() {
        AccountRole.values().forEach { floor ->
            assertFalse(floor.name, RoleMapper.isAtLeast(null, floor))
            assertFalse(floor.name, RoleMapper.isAtLeast("", floor))
            assertFalse(floor.name, RoleMapper.isAtLeast("   ", floor))
            assertFalse(floor.name, RoleMapper.isAtLeast("MANAGER", floor))
        }
    }
}
