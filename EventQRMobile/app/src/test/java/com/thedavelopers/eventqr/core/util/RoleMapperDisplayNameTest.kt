package com.thedavelopers.eventqr.core.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Role labels now come from string resources; these assertions pin the visible English text. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RoleMapperDisplayNameTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun getDisplayName_returnsExpectedLabels() {
        assertEquals("Attendee", RoleMapper.getDisplayName(context, "USER"))
        assertEquals("Attendee", RoleMapper.getDisplayName(context, "ATTENDEE"))
        assertEquals("Staff", RoleMapper.getDisplayName(context, "STAFF"))
        assertEquals("Organizer", RoleMapper.getDisplayName(context, "ORGANIZER"))
        assertEquals("Administrator", RoleMapper.getDisplayName(context, "ADMIN"))
        assertEquals("Super Admin", RoleMapper.getDisplayName(context, "SUPER_ADMIN"))
    }

    @Test
    fun getDisplayName_handlesBlankAndFallback() {
        assertEquals("", RoleMapper.getDisplayName(context, null))
        assertEquals("", RoleMapper.getDisplayName(context, ""))
        assertEquals("Guest", RoleMapper.getDisplayName(context, "guest"))
    }
}
