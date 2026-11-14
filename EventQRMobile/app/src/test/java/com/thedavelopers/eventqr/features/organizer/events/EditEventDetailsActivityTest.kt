package com.thedavelopers.eventqr.features.organizer.events

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.format.formatEventTime
import com.thedavelopers.eventqr.core.format.formatRegistrationWindow
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import java.time.Instant
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The organizer event editor (§6.4 v1.7), bound for real: the header band is the title alone, the
 * badge is the read-only STATUS row that opens the form column, and STATUS / EVENT DATES /
 * REGISTRATION WINDOW stack above `fontScale` 1.3 through the production list, never a copy of it.
 *
 * The repository factory is installed before `create()`, so the screen's own load resolves to
 * [NetworkResult.Loading] and never issues a request; `populate()` is the only writer.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EditEventDetailsActivityTest {

    private var controller: ActivityController<EditEventDetailsActivity>? = null

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        controller = null
        RuntimeEnvironment.setFontScale(1.0f)
    }

    /** A font-scale change recreates the activity, so the scale must be set before it is built. */
    private fun build(fontScale: Float = 1.0f): EditEventDetailsActivity {
        RuntimeEnvironment.setFontScale(fontScale)
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            EditEventDetailsActivity::class.java,
        ).putExtra(EXTRA_EVENT_ID, EVENT_ID.toString())
        val controller = Robolectric.buildActivity(EditEventDetailsActivity::class.java, intent)
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

    /** Far enough ahead that the date-derived status stays Approved -> "Upcoming" on any run day. */
    private fun event() = OrganizerEventDto(
        eventId = EVENT_ID,
        title = "DevFest Manila 2026",
        organizerName = "TheDAVElopers",
        venue = "Iloilo Convention Center",
        status = "Approved",
        eventStartAt = Instant.parse("2030-06-12T01:00:00Z"),
        eventEndAt = Instant.parse("2030-06-12T09:00:00Z"),
        capacity = 300,
    )

    /**
     * The head is identity only here too: no badge in the band, and STATUS is the form column's first
     * block, above the EVENT section that carries Title and Venue.
     */
    @Test
    fun statusRow_opensTheFormAndTheHeadCarriesNoBadge() {
        val activity = build()

        activity.populate(event())

        val form = activity.findViewById<ViewGroup>(R.id.eeForm)
        assertEquals("STATUS must be the first block of the form column", R.id.eeStatusRow, form.getChildAt(0).id)
        assertEquals(R.id.eeDividerStatus, form.getChildAt(1).id)

        assertEquals(View.GONE, activity.findViewById<View>(R.id.oeHeadStatus).visibility)
        assertEquals(
            "the header reads as the event name alone",
            "DevFest Manila 2026",
            activity.findViewById<View>(R.id.oeHeadTitleRow).contentDescription.toString(),
        )
        val titleParams =
            activity.findViewById<View>(R.id.oeHeadTitle).layoutParams as LinearLayout.LayoutParams
        assertEquals("the title takes the full header row", 0, titleParams.width)
        assertEquals(1f, titleParams.weight, 0f)
    }

    /** An approved future event reads Upcoming, the same label the head binder would have shown. */
    @Test
    fun statusRow_readsUpcomingForAnApprovedFutureEvent() {
        val activity = build()

        activity.populate(event())

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.eeStatusRow).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.eeDividerStatus).visibility)
        assertEquals(
            "the badge is invisible until the first bind, then shown",
            View.VISIBLE,
            activity.findViewById<View>(R.id.eeStatusValue).visibility,
        )
        assertEquals(
            activity.getString(R.string.ui_attendee_upcoming),
            activity.findViewById<TextView>(R.id.eeStatusValue).text.toString(),
        )
    }

    /** Active is date-derived, and its tone is the lime confirmation - not a text colour change. */
    @Test
    fun statusRow_readsActiveForARunningEvent() {
        val activity = build()
        val now = Instant.now()

        activity.populate(
            event().copy(eventStartAt = now.minusSeconds(3_600), eventEndAt = now.plusSeconds(3_600)),
        )

        val badge = activity.findViewById<TextView>(R.id.eeStatusValue)
        assertEquals(activity.getString(R.string.ui_attendee_active), badge.text.toString())
        assertEquals(
            "the Active tone is the lime confirmation",
            ContextCompat.getColor(activity, R.color.ui_on_accent),
            badge.currentTextColor,
        )
    }

    /**
     * The editor's two read-only date rows are side-by-side form fields, so they print the shared
     * range on one flowing line: the details pages' deliberate break after the start date would put
     * half the value on a line of its own inside a half-width column.
     */
    @Test
    fun readOnlyDateRows_printTheRangeOnOneLine() {
        val activity = build()

        activity.populate(
            event().copy(
                eventStartAt = Instant.parse("2030-06-12T01:00:00Z"),
                eventEndAt = Instant.parse("2030-06-14T09:00:00Z"),
                registrationOpenAt = Instant.parse("2030-05-01T01:00:00Z"),
                registrationCloseAt = Instant.parse("2030-06-01T15:59:00Z"),
            ),
        )

        listOf(R.id.eeEventDatesValue, R.id.eeWindowValue).forEach { valueId ->
            val text = activity.findViewById<TextView>(valueId).text.toString()
            val name = activity.resources.getResourceEntryName(valueId)
            assertTrue("$name must stay one flowing line", "\n" !in text)
            assertTrue("$name must print both ends", "\u2013" in text)
        }
    }

    /**
     * The read-only rows are the details pages' values, not a second formatting path: both come from
     * the shared formatters, so the editor cannot drift from the pages it edits.
     */
    @Test
    fun readOnlyDateRows_printTheSharedFormatterOutput() {
        val activity = build()
        val fest = event().copy(
            eventStartAt = Instant.parse("2030-06-12T01:00:00Z"),
            eventEndAt = Instant.parse("2030-06-14T09:00:00Z"),
            registrationOpenAt = Instant.parse("2030-05-01T01:00:00Z"),
            registrationCloseAt = Instant.parse("2030-06-01T15:59:00Z"),
        )

        activity.populate(fest)

        assertEquals(
            formatEventTime(activity, fest.eventStartAt, fest.eventEndAt)?.replace('\n', ' '),
            activity.findViewById<TextView>(R.id.eeEventDatesValue).text.toString(),
        )
        assertEquals(
            formatRegistrationWindow(activity, fest.registrationOpenAt, fest.registrationCloseAt)
                ?.replace('\n', ' '),
            activity.findViewById<TextView>(R.id.eeWindowValue).text.toString(),
        )
    }

    /** The STATUS row is a value, not a control: no input, no tap target. */
    @Test
    fun statusRow_isReadOnly() {
        val activity = build()

        activity.populate(event())

        val row = activity.findViewById<View>(R.id.eeStatusRow)
        val value = activity.findViewById<View>(R.id.eeStatusValue)
        assertFalse("the badge must not be an input", value is EditText)
        assertFalse("the row must not be tappable", row.isClickable)
        assertFalse("tapping the badge must do nothing", value.performClick())
    }

    /**
     * The editor stacks the same three rows the details page's spec table does, through the one
     * [com.thedavelopers.eventqr.ui.components.MetricRowStacking] implementation. The badge stacks
     * under its label but keeps hugging; the two date values fill the row.
     */
    @Test
    fun readOnlyRows_stackAboveTheFontScaleThreshold() {
        val activity = build(fontScale = 2.0f)

        activity.populate(event())

        listOf(
            R.id.eeStatusRow to R.id.eeStatusValue,
            R.id.eeEventDatesRow to R.id.eeEventDatesValue,
            R.id.eeWindowRow to R.id.eeWindowValue,
        ).forEach { pair ->
            assertTrue(
                "the production stacking list must carry " +
                    activity.resources.getResourceEntryName(pair.first),
                organizerEventEditStackedRows.contains(pair),
            )
        }

        organizerEventEditStackedRows.forEach { (rowId, valueId) ->
            val row = activity.findViewById<LinearLayout>(rowId)
            val value = activity.findViewById<TextView>(valueId)
            val params = value.layoutParams as LinearLayout.LayoutParams
            val name = activity.resources.getResourceEntryName(rowId)

            assertEquals("$name must stack", LinearLayout.VERTICAL, row.orientation)
            assertEquals(
                "$name value width",
                if (rowId == R.id.eeStatusRow) {
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
            if (rowId == R.id.eeStatusRow) {
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
        }
    }

    @Test
    fun readOnlyRows_atTheDefaultFontScale_keepLabelLeftValueRight() {
        val activity = build()

        activity.populate(event())

        organizerEventEditStackedRows.forEach { (rowId, _) ->
            val row = activity.findViewById<LinearLayout>(rowId)
            val value = row.getChildAt(1) as TextView
            val params = value.layoutParams as LinearLayout.LayoutParams
            val name = activity.resources.getResourceEntryName(rowId)

            assertEquals("$name stays a horizontal row", LinearLayout.HORIZONTAL, row.orientation)
            // The date values fill the trailing half through weight; the badge hugs its label.
            if (rowId == R.id.eeStatusRow) {
                assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, params.width)
            } else {
                assertEquals(0, params.width)
                assertEquals(1f, params.weight, 0f)
            }
        }
    }

    /**
     * Save enablement is untouched by the STATUS row: clean on load, enabled on the first edit, and
     * still hidden with every field disabled once the event is locked.
     */
    @Test
    fun saveStaysDisabledUntilTheFormIsDirty() {
        val activity = build()
        val save = activity.findViewById<Button>(R.id.eeSaveButton)

        activity.populate(event())
        assertFalse("clean form: save must be disabled", save.isEnabled)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.eeUnsaved).visibility)

        activity.findViewById<EditText>(R.id.eeTitleInput).setText("DevFest Manila 2027")
        assertTrue("a dirty field enables save", save.isEnabled)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.eeUnsaved).visibility)
    }

    /**
     * Regression: the save bar's button had no click listener, so an edit could enable it, look
     * ready, and never leave the device. The listener is the only path from the bar to the PATCH,
     * so the assertion is the request the repository was handed.
     */
    @Test
    fun saveBarIssuesThePatchForTheEditedTitle() {
        val activity = build()
        val repository = RecordingRepository(activity)
        activity.repository = repository

        activity.populate(event())
        activity.findViewById<EditText>(R.id.eeTitleInput).setText("DevFest Manila 2027")
        val save = activity.findViewById<Button>(R.id.eeSaveButton)
        assertTrue("a dirty field must enable the bar's button", save.isEnabled)

        save.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "the PATCH must carry the event being edited",
            EVENT_ID.toString(),
            repository.savedEventId,
        )
        assertEquals(
            "the PATCH must carry the typed title",
            "DevFest Manila 2027",
            repository.savedRequest?.title,
        )
        assertTrue("a successful save closes the screen", activity.isFinishing)
    }

    /**
     * Back reaches the callback from more than one dispatch (the system gesture, the top bar's own
     * arrow), and a repeat while the confirm is already up must not stack a second copy.
     */
    @Test
    fun aSecondBackDispatchKeepsOneDiscardDialog() {
        val activity = rotatable().get()
        activity.populate(event())
        activity.findViewById<EditText>(R.id.eeTitleInput).setText("DevFest Manila 2027")

        activity.onBackPressedDispatcher.onBackPressed()
        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(
            "one dialog at a time, whatever the dispatch source",
            1,
            ShadowDialog.getShownDialogs().count { it.isShowing },
        )
        assertFalse("the confirm keeps the screen up", activity.isFinishing)
    }

    @Test
    fun runningEvent_locksTheFormAndHidesTheSaveBar() {
        val activity = build()
        val now = Instant.now()

        activity.populate(
            event().copy(eventStartAt = now.minusSeconds(3_600), eventEndAt = now.plusSeconds(3_600)),
        )

        assertEquals(View.GONE, activity.findViewById<View>(R.id.eeSaveBar).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.eeLockedNote).visibility)
        assertFalse(activity.findViewById<View>(R.id.eeTitleInput).isEnabled)
        assertFalse(activity.findViewById<View>(R.id.eeCapacityInput).isEnabled)
        // The status still reads on a locked event: the row is not part of the editable form.
        assertEquals(
            activity.getString(R.string.ui_attendee_active),
            activity.findViewById<TextView>(R.id.eeStatusValue).text.toString(),
        )
    }

    // ------------------------------------------------------------ rotation / process death

    /** The same screen, on a controller the test can recreate. */
    private fun rotatable(): ActivityController<EditEventDetailsActivity> {
        RuntimeEnvironment.setFontScale(1.0f)
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            EditEventDetailsActivity::class.java,
        ).putExtra(EXTRA_EVENT_ID, EVENT_ID.toString())
        val controller = Robolectric.buildActivity(EditEventDetailsActivity::class.java, intent)
        this.controller = controller
        controller.get().installRepositoryFactory { LoadingRepository(it) }
        controller.create().start().resume().visible()
        return controller
    }

    /** A recreated instance is built without the factory seam, so it is handed the same repository. */
    private fun recreatedScreen(controller: ActivityController<EditEventDetailsActivity>): EditEventDetailsActivity {
        controller.recreate()
        val activity = controller.get()
        activity.repository = LoadingRepository(activity)
        return activity
    }

    @Test
    fun anEditSurvivesRecreateAndStillReadsDirty() {
        val controller = rotatable()
        controller.get().populate(event())
        controller.get().findViewById<EditText>(R.id.eeTitleInput).setText("DevFest Manila 2027")

        val recreated = recreatedScreen(controller)
        recreated.populate(event())

        assertEquals(
            "the edit must survive the recreation",
            "DevFest Manila 2027",
            recreated.findViewById<EditText>(R.id.eeTitleInput).text.toString(),
        )
        assertEquals(
            "the head follows the restored title",
            "DevFest Manila 2027",
            recreated.findViewById<TextView>(R.id.oeHeadTitle).text.toString(),
        )
        assertEquals(
            "and its one TalkBack node",
            "DevFest Manila 2027",
            recreated.findViewById<View>(R.id.oeHeadTitleRow).contentDescription.toString(),
        )
        assertTrue(
            "a restored draft that differs from the loaded values is dirty",
            recreated.findViewById<Button>(R.id.eeSaveButton).isEnabled,
        )

        recreated.onBackPressedDispatcher.onBackPressed()
        assertNotNull("a dirty back must ask to discard", ShadowDialog.getLatestDialog())
        assertFalse("the confirm keeps the screen up", recreated.isFinishing)
    }

    @Test
    fun aCleanFormRecreatesClean() {
        val controller = rotatable()
        controller.get().populate(event())

        val recreated = recreatedScreen(controller)
        recreated.populate(event())

        assertFalse(
            "a clean form keeps save disabled",
            recreated.findViewById<Button>(R.id.eeSaveButton).isEnabled,
        )
        recreated.onBackPressedDispatcher.onBackPressed()
        assertNull("a clean form owes no confirm", ShadowDialog.getLatestDialog())
        assertTrue(recreated.isFinishing)
    }

    /** The screen's own load completes as Loading, so `populate()` is the only writer in these tests. */
    private class LoadingRepository(context: Context) : OrganizerRepository(context) {
        override suspend fun fetchOrganizerEvent(eventId: String): NetworkResult<OrganizerEventDto> =
            NetworkResult.Loading
    }

    /**
     * Same seam, but the PATCH is recorded instead of sent: the save bar's whole contract is what
     * it hands [updateOrganizerEvent]. The category read is a success because a failed one blocks
     * the save by design.
     */
    private class RecordingRepository(context: Context) : OrganizerRepository(context) {
        var savedEventId: String? = null
        var savedRequest: EventRequest? = null

        override suspend fun fetchOrganizerEvent(eventId: String): NetworkResult<OrganizerEventDto> =
            NetworkResult.Loading

        override suspend fun fetchEventDetail(eventId: String): NetworkResult<AttendeeEventResponse> =
            NetworkResult.Success(
                AttendeeEventResponse(
                    eventId = UUID.fromString(eventId),
                    title = "DevFest Manila 2026",
                    category = "Technology",
                ),
            )

        override suspend fun updateOrganizerEvent(
            eventId: String,
            request: EventRequest,
        ): NetworkResult<EventResponse> {
            savedEventId = eventId
            savedRequest = request
            return NetworkResult.Success(
                EventResponse(
                    eventId = UUID.fromString(eventId),
                    title = request.title,
                    category = request.category,
                    capacity = request.capacity,
                    currentAttendeeCount = 0,
                    status = EventStatus.APPROVED,
                    rewardsEnabled = request.rewardsEnabled,
                ),
            )
        }
    }

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    }
}
