package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * §6.2: the range joins through an en dash plus a non-breaking space, so the only break
 * opportunity sits before the dash, and the single-sided labels keep their argument slot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RegistrationWindowResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the range is joined by an en dash and a non-breaking space`() {
        assertEquals("%1\$s \u2013\u00A0%2\$s", context.getString(R.string.event_detail_date_range))
    }

    @Test
    fun `the single-sided labels keep their argument slot`() {
        assertTrue(context.getString(R.string.ui_attendee_registration_opens).contains("%1\$s"))
        assertTrue(context.getString(R.string.ui_attendee_registration_closes).contains("%1\$s"))
    }
}
