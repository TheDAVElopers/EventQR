package com.thedavelopers.eventqr.features.organizer.events

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.attendee.AttendeeBadges
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.Instant
import java.util.UUID

/**
 * The Manage event hub's quiet status line (§6.4, §5.4 on-dark exception): the band is the title
 * alone and the status is a 6dp dot + muted word under it, one TalkBack node.
 *
 * The activity is created but never resumed, so the screen's own load never runs and `bindEvent`
 * is the only writer; the repository seam only keeps the constructor off the network.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventManagementHubActivityTest {

    private var controller: ActivityController<EventManagementHubActivity>? = null

    @After
    fun tearDown() {
        controller?.destroy()
        controller = null
        ShadowLooper.idleMainLooper()
        RuntimeEnvironment.setFontScale(1.0f)
    }

    /**
     * A font-scale change recreates the activity, so the scale must be set before it is built.
     * [resume] drives the screen's own load and idles the main looper, so a test can assert the
     * error path; the default only creates the activity and leaves `bindEvent` the only writer.
     */
    private fun build(
        fontScale: Float = 1.0f,
        resume: Boolean = false,
        factory: (Context) -> OrganizerRepository = { LoadingRepository(it) },
    ): EventManagementHubActivity {
        RuntimeEnvironment.setFontScale(fontScale)
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            EventManagementHubActivity::class.java,
        ).putExtra(EXTRA_EVENT_ID, EVENT_ID)
        val controller = Robolectric.buildActivity(EventManagementHubActivity::class.java, intent)
        this.controller = controller
        val activity = controller.get()
        activity.installRepositoryFactory(factory)
        controller.create()
        if (resume) {
            controller.start().resume()
            ShadowLooper.idleMainLooper()
        }
        return activity
    }

    private fun event(
        status: String = "Draft",
        start: Instant? = null,
        end: Instant? = null,
    ) = OrganizerMvpEvent(
        id = EVENT_ID,
        title = "DevFest Manila 2026",
        organizerName = "TheDAVElopers",
        dateTime = "",
        shortDate = "",
        venue = "Iloilo Convention Center",
        status = status,
        submittedDate = "",
        adminRemarks = "",
        additionalOrganizers = emptyList(),
        registeredCount = 128,
        enteredCount = 0,
        attendedCount = 0,
        exitedCount = 0,
        noShowCount = 0,
        totalTransactions = 0,
        successfulScans = 0,
        rejectedScans = 0,
        benefitClaims = 0,
        boothSessionVisits = 0,
        rewardRedemptions = 0,
        totalPointsAwarded = 0,
        idTemplateStatus = "",
        rewardsStatus = "",
        staffCount = 0,
        scanPurposesCount = 0,
        capacity = 200,
        currentAttendeeCount = 128,
        eventStartAt = start,
        eventEndAt = end,
    )

    /** One fixture per status the screen can resolve; the date-derived ones drive the window. */
    private fun cases(): List<Pair<EventStatus, OrganizerMvpEvent>> {
        val now = Instant.now()
        return listOf(
            EventStatus.DRAFT to event(status = "Draft"),
            EventStatus.PENDING_REVIEW to event(status = "Pending Review"),
            EventStatus.REJECTED to event(status = "Rejected"),
            EventStatus.CANCELLED to event(status = "Cancelled"),
            EventStatus.UNKNOWN to event(status = "Awaiting clearance"),
            EventStatus.APPROVED to event(
                status = "Approved",
                start = now.plusSeconds(3_600),
                end = now.plusSeconds(7_200),
            ),
            EventStatus.ACTIVE to event(
                status = "Approved",
                start = now.minusSeconds(3_600),
                end = now.plusSeconds(3_600),
            ),
            EventStatus.ENDED to event(
                status = "Approved",
                start = now.minusSeconds(7_200),
                end = now.minusSeconds(3_600),
            ),
        )
    }

    @Test
    fun statusLine_bindsLabelDotAndOneA11yNodeForEveryStatus() {
        val activity = build()
        val row = activity.findViewById<View>(R.id.emh_status_row)
        val dot = activity.findViewById<View>(R.id.emh_status_dot)
        val label = activity.findViewById<TextView>(R.id.emh_status_text)

        assertEquals(EventStatus.values().size, cases().size)
        cases().forEach { (status, event) ->
            activity.bindEvent(event)

            val expectedLabel = AttendeeBadges.label(activity, status)
            assertEquals("$status label", expectedLabel, label.text.toString())
            assertEquals(
                "$status dot token",
                ContextCompat.getColor(activity, AttendeeBadges.onDarkDotColorRes(status)),
                ViewCompat.getBackgroundTintList(dot)?.defaultColor,
            )
            assertEquals(
                "$status a11y name",
                activity.getString(R.string.ui_organizer_hub_status_a11y, expectedLabel),
                row.contentDescription,
            )
            assertEquals("$status row visible", View.VISIBLE, row.visibility)
            assertTrue("$status is one TalkBack node", ViewCompat.isScreenReaderFocusable(row))
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, dot.importantForAccessibility)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, label.importantForAccessibility)
        }
    }

    @Test
    fun statusLine_neverPaintsTheWordLime() {
        val activity = build()

        activity.bindEvent(
            event(status = "Approved", start = Instant.now().minusSeconds(60), end = Instant.now().plusSeconds(60)),
        )

        val label = activity.findViewById<TextView>(R.id.emh_status_text)
        assertEquals(
            "the word stays muted on dark; lime is the dot fill only",
            ContextCompat.getColor(activity, R.color.ui_text_on_dark_muted),
            label.currentTextColor,
        )
    }

    @Test
    fun statusBadge_isGoneFromTheLayout() {
        val activity = build()

        // Positive control: a known hub id resolves, so the absence check cannot pass vacuously.
        assertNotEquals(
            0,
            activity.resources.getIdentifier("emh_header_identity", "id", activity.packageName),
        )
        assertEquals(
            "the trailing lime chip must stay deleted",
            0,
            activity.resources.getIdentifier("emh_status_badge", "id", activity.packageName),
        )
    }

    @Test
    fun title_ownsTheFullBand() {
        val activity = build()
        val identity = activity.findViewById<View>(R.id.emh_header_identity)
        val title = activity.findViewById<TextView>(R.id.emh_event_title)

        assertTrue("the title is a direct child of the band", title.parent === identity)
        assertEquals(
            "the title spans the band",
            LinearLayout.LayoutParams.MATCH_PARENT,
            (title.layoutParams as LinearLayout.LayoutParams).width,
        )
        assertEquals(2, title.maxLines)

        identity.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        identity.layout(0, 0, identity.measuredWidth, identity.measuredHeight)

        val gutter = activity.resources.getDimensionPixelSize(R.dimen.ui_gutter)
        assertEquals(identity.measuredWidth - 2 * gutter, title.measuredWidth)
    }

    @Test
    fun statusLine_atFontScaleTwo_wrapsWithoutClippingAndKeepsTheSixDpDot() {
        val activity = build(fontScale = 2.0f)
        activity.bindEvent(event(status = "Pending Review"))
        val row = activity.findViewById<View>(R.id.emh_status_row)
        val dot = activity.findViewById<View>(R.id.emh_status_dot)
        val label = activity.findViewById<TextView>(R.id.emh_status_text)

        // A real long label is the wrap case; the row has no maxLines and the dot is a fixed 6dp.
        label.text = "Pending review awaiting administrator approval"
        val width = (320 * activity.resources.displayMetrics.density).toInt()
        row.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)

        val dotSize = activity.resources.getDimensionPixelSize(R.dimen.ui_badge_dot)
        assertEquals(dotSize, dot.measuredWidth)
        assertEquals(dotSize, dot.measuredHeight)
        assertEquals(Int.MAX_VALUE, label.maxLines)
        assertTrue(
            "the label wraps instead of truncating " +
                "(lines=${label.lineCount}, label=${label.measuredWidth}x${label.measuredHeight}, " +
                "row=${row.measuredWidth}x${row.measuredHeight})",
            label.lineCount >= 2,
        )
        assertTrue("nothing clips: the row is as tall as its tallest child", row.measuredHeight >= label.measuredHeight)
        assertEquals(
            "the dot stays centred on the wrapped label",
            (row.measuredHeight - dot.measuredHeight) / 2,
            dot.top,
        )
        assertTrue("the dot ends inside the row", dot.bottom <= row.measuredHeight)
        assertTrue("the row keeps the 8dp gap", (row.layoutParams as LinearLayout.LayoutParams).topMargin > 0)
    }

    @Test
    fun statusRow_isInvisibleUntilBindAndVisibleAfter() {
        val activity = build()
        val row = activity.findViewById<View>(R.id.emh_status_row)

        assertEquals("the band keeps its height before the first bind", View.INVISIBLE, row.visibility)

        activity.bindEvent(event(status = "Draft"))

        assertEquals(View.VISIBLE, row.visibility)
    }

    @Test
    fun statusLine_errorPath_dropsTheRowWithTheBand() {
        val activity = build(resume = true, factory = { ErrorRepository(it) })

        // showStateCard() swaps the band's children for the loading + error states.
        val banner = activity.findViewById<LinearLayout>(R.id.emh_header_banner)
        assertEquals(2, banner.childCount)
        assertNull("the error state has no status line", activity.findViewById<View>(R.id.emh_status_row))
        assertNull("the error state has no identity band", activity.findViewById<View>(R.id.emh_header_identity))
    }

    @Test
    fun editHead_keepsItsOwnStatusBadge() {
        val activity = build()

        // Positive control: the hub's own band resolves, so the absence check cannot pass vacuously.
        assertNotNull(activity.findViewById<View>(R.id.emh_header_identity))
        assertNull(
            "the editor's shared identity include is untouched",
            activity.findViewById<View>(R.id.oeHeadStatus),
        )
    }

    /** The created-only path never runs this; the resumed error test returns an Error. */
    private class LoadingRepository(context: Context) : OrganizerRepository(context) {
        override suspend fun fetchOrganizerEvent(eventId: String): NetworkResult<OrganizerEventDto> =
            NetworkResult.Loading
    }

    private class ErrorRepository(context: Context) : OrganizerRepository(context) {
        override suspend fun fetchOrganizerEvent(eventId: String): NetworkResult<OrganizerEventDto> =
            NetworkResult.Error("offline")
    }

    private companion object {
        val EVENT_ID: String = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0").toString()
    }
}
