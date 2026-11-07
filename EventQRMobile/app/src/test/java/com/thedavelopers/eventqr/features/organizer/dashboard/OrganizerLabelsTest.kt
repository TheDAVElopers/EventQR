package com.thedavelopers.eventqr.features.organizer.dashboard

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerAttendeeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class OrganizerLabelsTest {
    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()

    private fun texts(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) out.add(view.text.toString())
        if (view is ViewGroup) for (i in 0 until view.childCount) texts(view.getChildAt(i), out)
        return out
    }

    @Test
    fun dashboardLabels_areAccurate() {
        assertEquals("Registered", context.getString(R.string.ui_organizer_registrations))
        assertEquals("Attended", context.getString(R.string.ui_organizer_attended))
        assertEquals("Live now", context.getString(R.string.ui_organizer_live_now))
        assertEquals("Redeemed", context.getString(R.string.ui_organizer_redeemed))
    }

    @Test
    fun dashboardScreen_usesNewLabels_andNoApproveCopy() {
        val activity = Robolectric.buildActivity(OrganizerDashboardActivity::class.java).create().get()
        val all = texts(activity.window.decorView).toString()
        // §6.4: the stub is REGISTERED / ATTENDED / LIVE NOW; the pass head has no subtitle.
        // Before the first load every cell is the unavailable token, never a fake 0.
        assertTrue(all, all.contains("Registered"))
        assertTrue(all, all.contains("Attended"))
        assertTrue(all, all.contains("Live now"))
        assertTrue(all, all.contains("--"))
        assertTrue(all, all.contains("No events yet"))
        assertFalse(all, all.contains("Approved"))
        assertFalse(all, all.contains("Total Attendees"))
        assertFalse(all, all.contains("Rewards Given"))
        assertFalse(all, all.contains("Approve or create"))
    }

    @Test
    fun attendeeDto_readsCountedAsRegistered() {
        val json = """{"registrationId":"${UUID.randomUUID()}","eventId":"${UUID.randomUUID()}",
            "currentEventStatus":"Cancelled","countedAsRegistered":false}"""
        val dto = sharedGson().fromJson(json, OrganizerAttendeeDto::class.java)
        assertEquals(false, dto.countedAsRegistered)
        assertNull(sharedGson().fromJson("""{"registrationId":"${UUID.randomUUID()}","eventId":"${UUID.randomUUID()}"}""", OrganizerAttendeeDto::class.java).countedAsRegistered)
    }

    @Test
    fun transactionRuleLabels_areHonest() {
        assertEquals("Max scans per attendee (whole event)", context.getString(R.string.transaction_rules_max_scans_label))
        assertEquals("Applies only when Allow duplicate scans is on.", context.getString(R.string.transaction_rules_max_scans_helper))
        assertEquals("Require assigned staff", context.getString(R.string.transaction_rules_require_staff_title))
        assertEquals("Only staff assigned to this event can log scans", context.getString(R.string.transaction_rules_require_staff_desc))
    }
}

