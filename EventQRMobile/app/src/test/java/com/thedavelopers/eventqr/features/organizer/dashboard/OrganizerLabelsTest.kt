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
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDashboardDto
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
        assertEquals("Total Registrations", context.getString(R.string.organizer_dashboard_total_registrations))
        assertEquals("Rewards Redeemed", context.getString(R.string.organizer_dashboard_rewards_redeemed))
        assertEquals("No events are happening right now.", context.getString(R.string.organizer_dashboard_no_events_happening_now))
    }

    @Test
    fun dashboardScreen_usesNewLabels_andNoApproveCopy() {
        val activity = Robolectric.buildActivity(OrganizerDashboardActivity::class.java).create().get()
        val all = texts(activity.window.decorView)
        assertTrue(all.toString(), all.contains("Total Registrations"))
        assertTrue(all.toString(), all.contains("Rewards Redeemed"))
        assertTrue(all.toString(), all.contains("No events are happening right now."))
        assertFalse(all.any { it == "Total Attendees" || it == "Rewards Given" || it.contains("Approve or create") })
    }

    @Test
    fun dashboardDto_readsTotalRegistrationsAndRedemptions() {
        val json = """{"totalAttendees":12,"totalRegistrations":12,"rewardRedemptions":4,"totalPointsAwarded":900}"""
        val dto = sharedGson().fromJson(json, OrganizerDashboardDto::class.java)
        assertEquals(12L, dto.totalRegistrations)
        assertEquals(4L, dto.rewardRedemptions)
        assertEquals(900L, dto.totalPointsAwarded)
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
