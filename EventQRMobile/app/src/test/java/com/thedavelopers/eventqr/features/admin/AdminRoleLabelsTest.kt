package com.thedavelopers.eventqr.features.admin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression: signed in as a SUPER_ADMIN, the Requests / Accounts / Logs eyebrow rendered `ADMIN`
 * because all three screens hard-coded `R.string.ui_admin_tab`. The eyebrow is `overline` (all
 * caps), so the visible text is the upper-cased label.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AdminRoleLabelsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun eyebrow(role: String?): String =
        context.getString(AdminRoleLabels.eyebrowRes(role)).uppercase()

    @Test
    fun eyebrow_keepsSuperAdminDistinctFromAdmin() {
        assertEquals("SUPER ADMIN", eyebrow("SUPER_ADMIN"))
        assertEquals("ADMIN", eyebrow("ADMIN"))
    }

    @Test
    fun eyebrow_acceptsTheBackendSpellingsAndStrayWhitespace() {
        assertEquals("SUPER ADMIN", eyebrow("SUPERADMIN"))
        assertEquals("SUPER ADMIN", eyebrow("  super_admin  "))
    }

    /** Not an admin at all cannot happen behind the screen guards; it must still not crash. */
    @Test
    fun eyebrow_fallsBackToAdminForMissingRole() {
        assertEquals("ADMIN", eyebrow(null))
        assertEquals("ADMIN", eyebrow(""))
        assertEquals("ADMIN", eyebrow("ATTENDEE"))
    }
}
