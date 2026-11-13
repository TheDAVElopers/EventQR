package com.thedavelopers.eventqr.features.organizer.events

import android.content.Context
import android.content.Intent
import android.view.Gravity
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
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * `TextView.JUSTIFICATION_MODE_NONE` / `_INTER_WORD` are the platform's own values but are not in the
 * public SDK stubs, so the assertions name them here.
 */
private const val JUSTIFICATION_MODE_NONE = 0
private const val JUSTIFICATION_MODE_INTER_WORD = 1

/**
 * The read-only organizer event details screen, bound for real (§6.4): the spec table hides a blank
 * row with its hairline, EVENT TIME collapses a same-day range, the STATUS row carries the badge the
 * header no longer shows, the stats band is gone, and the stacking rows come from the production
 * list, never a copy of it.
 *
 * The repository factory is installed before `create()`, so the screen's own load resolves to
 * [NetworkResult.Loading] and never issues a request; the seam is at repository level, and `bind()`
 * is the only writer.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class OrganizerEventDetailsActivityTest {

    private var controller: ActivityController<OrganizerEventDetailsActivity>? = null

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        controller = null
        RuntimeEnvironment.setFontScale(1.0f)
    }

    /** A font-scale change recreates the activity, so the scale must be set before it is built. */
    private fun build(fontScale: Float = 1.0f): OrganizerEventDetailsActivity {
        RuntimeEnvironment.setFontScale(fontScale)
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            OrganizerEventDetailsActivity::class.java,
        ).putExtra(EXTRA_EVENT_ID, EVENT_ID.toString())
        val controller = Robolectric.buildActivity(OrganizerEventDetailsActivity::class.java, intent)
        this.controller = controller
        val activity = controller.get()
        activity.installRepositoryFactory { LoadingRepository(it) }
        controller.create()
        assertTrue(
            "factory seam must be installed before create()",
            activity.repository is LoadingRepository,
        )
        return activity
    }

    private fun event() = OrganizerEventDto(
        eventId = EVENT_ID,
        title = "DevFest Manila 2026",
        organizerName = "TheDAVElopers",
        additionalOrganizers = listOf("UP Cebu", "DevCon Manila"),
        venue = "Iloilo Convention Center",
        status = "Approved",
        eventStartAt = Instant.parse("2026-11-13T01:00:00Z"),
        eventEndAt = Instant.parse("2026-11-13T04:20:00Z"),
    )

    @Test
    fun locationRow_showsTheEventLocation() {
        val activity = build()

        activity.bind(event().copy(venue = "Iloilo Convention Center"))

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.oedRowLocation).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.oedDividerLocation).visibility)
        assertEquals(
            "Iloilo Convention Center",
            activity.findViewById<TextView>(R.id.oedLocationValue).text.toString(),
        )
    }

    @Test
    fun blankLocation_hidesTheRowAndItsHairline() {
        val activity = build()

        activity.bind(event().copy(venue = "   "))

        assertEquals(View.GONE, activity.findViewById<View>(R.id.oedRowLocation).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.oedDividerLocation).visibility)
    }

    @Test
    fun eventTime_sameDayRangePrintsTheDateOnce() {
        val activity = build()

        activity.bind(
            event().copy(
                eventStartAt = Instant.parse("2026-11-13T01:00:00Z"), // 13\u00A0Nov\u00A02026, 9:00\u00A0AM
                eventEndAt = Instant.parse("2026-11-13T04:20:00Z"), // 13\u00A0Nov\u00A02026, 12:20\u00A0PM
            ),
        )

        assertEquals(
            activity.getString(
                R.string.ui_organizer_event_time_same_day,
                "13\u00A0Nov\u00A02026",
                "9:00\u00A0AM",
                "12:20\u00A0PM",
            ),
            activity.findViewById<TextView>(R.id.oedEventTimeValue).text.toString(),
        )
    }

    @Test
    fun eventTime_multiDayRangeKeepsBothDates() {
        val activity = build()

        activity.bind(
            event().copy(
                eventStartAt = Instant.parse("2026-11-13T01:00:00Z"), // 13\u00A0Nov\u00A02026, 9:00\u00A0AM
                eventEndAt = Instant.parse("2026-11-14T09:00:00Z"), // 14\u00A0Nov\u00A02026, 5:00\u00A0PM
            ),
        )

        assertEquals(
            "13\u00A0Nov\u00A02026, 9:00\u00A0AM\n\u2013\u00A014\u00A0Nov\u00A02026, 5:00\u00A0PM",
            activity.findViewById<TextView>(R.id.oedEventTimeValue).text.toString(),
        )
    }

    /**
     * The header band is title only on this screen: the badge lives in the STATUS row, bound through
     * the same `bindEventBadge` mapping the head binder uses, and the title row reads as the name
     * alone. Active is date-derived, so the fixture drives it with a live range.
     */
    @Test
    fun statusRow_showsTheActiveStatusAndTheHeaderCarriesNoBadge() {
        val activity = build()
        val now = Instant.now()

        activity.bind(
            event().copy(
                status = "Approved",
                eventStartAt = now.minusSeconds(3_600),
                eventEndAt = now.plusSeconds(3_600),
            ),
        )

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.oedRowStatus).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.oedDividerStatus).visibility)
        val badge = activity.findViewById<TextView>(R.id.oedStatusValue)
        assertEquals(activity.getString(R.string.ui_attendee_active), badge.text.toString())
        assertEquals(
            "the Active tone is the lime confirmation",
            ContextCompat.getColor(activity, R.color.ui_on_accent),
            badge.currentTextColor,
        )

        assertEquals(View.GONE, activity.findViewById<View>(R.id.oeHeadStatus).visibility)
        assertEquals(
            "the head reads as the event name alone",
            "DevFest Manila 2026",
            activity.findViewById<View>(R.id.oeHeadTitleRow).contentDescription.toString(),
        )
    }

    /** A non-time-derived status keeps the backend value and its tone: Rejected is error, not lime. */
    @Test
    fun statusRow_mapsARejectedEventThroughTheSameBinder() {
        val activity = build()

        activity.bind(event().copy(status = "Rejected"))

        val badge = activity.findViewById<TextView>(R.id.oedStatusValue)
        assertEquals(activity.getString(R.string.ui_attendee_rejected), badge.text.toString())
        assertEquals(
            ContextCompat.getColor(activity, R.color.ui_on_error_container),
            badge.currentTextColor,
        )
    }

    // ── STATUS row: all eight lifecycle values through the one binding path ──────────────────────

    /** Dates that make the resolver return [status]: time-derived values come from the range. */
    private fun statusFixture(raw: String, status: EventStatus): OrganizerEventDto {
        val now = Instant.now()
        val (start, end) = when (status) {
            EventStatus.APPROVED -> now.plusSeconds(86_400) to now.plusSeconds(172_800)
            EventStatus.ACTIVE -> now.minusSeconds(3_600) to now.plusSeconds(3_600)
            EventStatus.ENDED -> now.minusSeconds(172_800) to now.minusSeconds(86_400)
            else -> null to null
        }
        return event().copy(status = raw, eventStartAt = start, eventEndAt = end)
    }

    @Test
    fun statusRow_bindsAllEightStatusesAndKeepsTheScreensPrimaryAction() {
        val statuses = linkedMapOf(
            "Draft" to EventStatus.DRAFT,
            "Pending review" to EventStatus.PENDING_REVIEW,
            "Approved" to EventStatus.APPROVED,
            "Rejected" to EventStatus.REJECTED,
            "Active" to EventStatus.ACTIVE,
            "Ended" to EventStatus.ENDED,
            "Cancelled" to EventStatus.CANCELLED,
            "Awaiting" to EventStatus.UNKNOWN,
        )
        assertEquals(
            "one fixture per lifecycle value",
            EventStatus.values().toSet(),
            statuses.values.toSet(),
        )

        statuses.forEach { (raw, expected) ->
            val fixture = statusFixture(raw, expected)
            val resolved = EventStatusBadgeStyler.resolve(
                EventStatusBadgeStyler.fromLabel(raw),
                fixture.eventStartAt,
                fixture.eventEndAt,
            )
            assertEquals("$raw: resolves to $expected", expected, resolved)

            val activity = build()
            activity.bind(fixture)

            val badge = activity.findViewById<TextView>(R.id.oedStatusValue)
            assertEquals(
                "$raw: STATUS row is shown",
                View.VISIBLE,
                activity.findViewById<View>(R.id.oedRowStatus).visibility,
            )
            assertEquals(
                "$raw: badge text",
                activity.getString(AttendeeBadges.labelRes(resolved)),
                badge.text.toString(),
            )
            val tone = AttendeeBadges.tone(resolved)
            assertEquals(
                "$raw: badge content tone",
                ContextCompat.getColor(activity, tone.content),
                badge.currentTextColor,
            )
            assertEquals(
                "$raw: badge container tone",
                ContextCompat.getColor(activity, tone.container),
                ViewCompat.getBackgroundTintList(badge)?.defaultColor,
            )

            // This page's primary action does not depend on the status: it is the hub's entry.
            val primary = activity.findViewById<android.widget.Button>(R.id.oedActionAttendees)
            assertEquals(
                "$raw: primary action",
                activity.getString(R.string.ui_organizer_view_attendees),
                primary.text.toString(),
            )
            assertEquals("$raw: primary action is shown", View.VISIBLE, primary.visibility)
        }
    }

    @Test
    fun statsBandViews_areAbsentFromTheScreen() {
        val activity = build()

        activity.bind(event())

        // Positive control: a known details id resolves, so the absence checks cannot pass vacuously.
        assertNotEquals(
            0,
            activity.resources.getIdentifier("oedRowLocation", "id", activity.packageName),
        )
        // Registered / Capacity / Available are the Manage event hub's stub (§6.4 v1.18).
        listOf("oedStub", "oedStatRegistered", "oedStatCapacity", "oedStatAvailable").forEach { gone ->
            assertEquals(
                "$gone must stay deleted: the stats band lives on Manage event",
                0,
                activity.resources.getIdentifier(gone, "id", activity.packageName),
            )
        }
    }

    /**
     * STATUS joins ORGANIZER and CO-ORGANIZERS on the same stacking path as LOCATION / EVENT TIME /
     * REGISTRATION WINDOW. The pairs come from the production list, so the screen and this test
     * cannot drift apart; the ids are asserted explicitly so dropping one fails here. The STATUS
     * value is the badge: it stacks under its label but keeps hugging, never a full-width pill.
     */
    @Test
    fun organizerAndCoOrganizerRows_stackAboveTheFontScaleThreshold() {
        val activity = build(fontScale = 2.0f)

        activity.bind(event())

        assertTrue(
            "the production list must carry STATUS",
            organizerEventDetailStackedRows.contains(R.id.oedRowStatus to R.id.oedStatusValue),
        )
        assertTrue(
            "the production list must carry ORGANIZER",
            organizerEventDetailStackedRows.contains(R.id.oedRowOrganizer to R.id.oedOrganizerValue),
        )
        assertTrue(
            "the production list must carry CO-ORGANIZERS",
            organizerEventDetailStackedRows.contains(
                R.id.oedRowCoOrganizers to R.id.oedCoOrganizersValue,
            ),
        )

        organizerEventDetailStackedRows.forEach { (rowId, valueId) ->
            val row = activity.findViewById<LinearLayout>(rowId)
            val value = activity.findViewById<TextView>(valueId)
            val params = value.layoutParams as LinearLayout.LayoutParams
            val name = activity.resources.getResourceEntryName(rowId)

            assertEquals("$name must stack", LinearLayout.VERTICAL, row.orientation)
            if (rowId == R.id.oedRowStatus) {
                // The badge is wrap_content and keeps the style's centred text gravity; only a
                // full-width text value is re-aligned to START.
                assertEquals(
                    "$name badge keeps its centred text gravity",
                    Gravity.CENTER_VERTICAL,
                    value.gravity and Gravity.CENTER_VERTICAL,
                )
            } else {
                assertEquals(
                    "$name value starts at the leading edge",
                    Gravity.START,
                    value.gravity and Gravity.START,
                )
            }
            assertEquals(
                "$name value width",
                if (rowId == R.id.oedRowStatus) {
                    LinearLayout.LayoutParams.WRAP_CONTENT
                } else {
                    LinearLayout.LayoutParams.MATCH_PARENT
                },
                params.width,
            )
            assertEquals("$name value carries no width weight", 0f, params.weight, 0f)
            assertEquals(
                "space_4 between the $name label and its value",
                activity.resources.getDimensionPixelSize(R.dimen.space_4),
                params.topMargin,
            )
        }
    }

    @Test
    fun organizerRows_atTheDefaultFontScale_keepLabelLeftValueRight() {
        val activity = build()

        activity.bind(event())

        listOf(R.id.oedRowOrganizer, R.id.oedRowCoOrganizers).forEach { rowId ->
            val row = activity.findViewById<LinearLayout>(rowId)
            val value = row.getChildAt(1) as TextView
            val params = value.layoutParams as LinearLayout.LayoutParams

            assertEquals(LinearLayout.HORIZONTAL, row.orientation)
            assertEquals(0, params.width)
            assertEquals(1f, params.weight, 0f)
        }
    }

    // A blank description hides About and its hairline only: the row above (REGISTRATION WINDOW) and
    // the row below (ORGANIZER) keep theirs, so the rhythm never doubles or drops a rule.
    @Test
    fun blankDescription_hidesAboutAndItsHairlineOnly() {
        val activity = build()

        activity.bind(
            event().copy(
                description = "  ",
                registrationOpenAt = Instant.parse("2026-05-01T01:00:00Z"),
                registrationCloseAt = Instant.parse("2026-06-01T15:59:00Z"),
            ),
        )

        assertEquals(View.GONE, activity.findViewById<View>(R.id.oedAboutBlock).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.oedDividerAbout).visibility)
        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.oedDividerRegistration).visibility,
        )
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.oedRowOrganizer).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.oedDividerOrganizer).visibility)
    }

    // No co-organizers hides their row and their own hairline; About sits above them and stays.
    @Test
    fun absentCoOrganizers_hidesOnlyTheirRowAndHairline() {
        val activity = build()

        activity.bind(
            event().copy(
                additionalOrganizers = emptyList(),
                description = "A one-day developer conference.",
            ),
        )

        assertEquals(View.GONE, activity.findViewById<View>(R.id.oedRowCoOrganizers).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.oedDividerCoOrganizers).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.oedAboutBlock).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.oedDividerAbout).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.oedRowOrganizer).visibility)
    }

    // The About body is the one justified surface in the app; the ABOUT label above it stays
    // ragged-right, like every other label and value on the table.
    @Test
    fun aboutBody_isInterWordJustifiedAndItsLabelIsNot() {
        val activity = build()

        activity.bind(event().copy(description = "A one-day developer conference for students and professionals."))

        assertEquals(
            JUSTIFICATION_MODE_INTER_WORD,
            activity.findViewById<TextView>(R.id.oedAboutBody).justificationMode,
        )
        assertEquals(
            "the ABOUT label stays ragged-right",
            JUSTIFICATION_MODE_NONE,
            (activity.findViewById<View>(R.id.oedAboutBlock) as LinearLayout)
                .getChildAt(0)
                .let { (it as TextView).justificationMode },
        )
    }

    /** The screen's own load completes as Loading, so `bind()` is the only writer in these tests. */    private class LoadingRepository(context: Context) : OrganizerRepository(context) {
        override suspend fun fetchOrganizerEvent(eventId: String): NetworkResult<OrganizerEventDto> =
            NetworkResult.Loading
    }

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    }
}
