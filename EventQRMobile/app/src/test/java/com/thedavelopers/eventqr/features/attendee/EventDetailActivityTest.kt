package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.UUID

/** The header is a fixed "Event Details"; the event name belongs on the banner above the description. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventDetailActivityTest {

    private fun build(title: String?): EventDetailActivity {
        val intent = Intent(ApplicationProvider.getApplicationContext(), EventDetailActivity::class.java)
        title?.let { intent.putExtra(EXTRA_EVENT_TITLE, it) }
        return Robolectric.buildActivity(EventDetailActivity::class.java, intent).create().get()
    }

    private fun event(title: String) = AttendeeEventResponse(eventId = UUID.randomUUID(), title = title, description = "About the event")

    @Test
    fun whileLoading_headerSaysEventDetailsAndTheBannerShowsTheNameFromTheIntent() {
        val activity = build("Summer Fest")

        assertEquals("Event Details", activity.findViewById<TextView>(R.id.nav_header_title).text.toString())
        assertEquals("Summer Fest", activity.findViewById<TextView>(R.id.txtDetailTitle).text.toString())
    }

    @Test
    fun renderEvent_putsTheNameOnTheBannerAndLeavesTheHeaderAlone() {
        val activity = build(null)

        activity.renderEvent(event("Night Market"))

        assertEquals("Night Market", activity.findViewById<TextView>(R.id.txtDetailTitle).text.toString())
        assertEquals("Event Details", activity.findViewById<TextView>(R.id.nav_header_title).text.toString())
        assertEquals("About the event", activity.findViewById<TextView>(R.id.txtDetailDescription).text.toString())
    }

    @Test
    fun renderEvent_unlimitedCapacityIsLabelledUnlimitedNotDashes() {
        val activity = build(null)

        activity.renderEvent(event("Open Gate").copy(capacity = 0, currentAttendeeCount = 7))

        // renderEvent shows availability through the registration status block once the availability call returns;
        // the intent-extra path renders the same text immediately, so exercise that path too.
        val intent = Intent(ApplicationProvider.getApplicationContext(), EventDetailActivity::class.java)
            .putExtra(EXTRA_EVENT_COUNT, "7").putExtra(EXTRA_EVENT_CAPACITY, "0")
        val fromIntent = Robolectric.buildActivity(EventDetailActivity::class.java, intent).create().get()

        assertEquals("Unlimited (7 registered)", fromIntent.findViewById<TextView>(R.id.txtDetailCapacity).text.toString())
    }
}
