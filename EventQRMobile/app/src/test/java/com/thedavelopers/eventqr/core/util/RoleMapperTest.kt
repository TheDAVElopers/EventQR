package com.thedavelopers.eventqr.core.util

import com.thedavelopers.eventqr.core.api.dto.AccountRole
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun getDisplayName_returnsExpectedLabels() {
        assertEquals("Attendee", RoleMapper.getDisplayName("USER"))
        assertEquals("Attendee", RoleMapper.getDisplayName("ATTENDEE"))
        assertEquals("Staff", RoleMapper.getDisplayName("STAFF"))
        assertEquals("Organizer", RoleMapper.getDisplayName("ORGANIZER"))
        assertEquals("Administrator", RoleMapper.getDisplayName("ADMIN"))
        assertEquals("Super Admin", RoleMapper.getDisplayName("SUPER_ADMIN"))
    }

    @Test
    fun getDisplayName_handlesBlankAndFallback() {
        assertEquals("", RoleMapper.getDisplayName(null))
        assertEquals("", RoleMapper.getDisplayName(""))
        assertEquals("Guest", RoleMapper.getDisplayName("guest"))
    }
}
