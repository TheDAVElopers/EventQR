package com.thedavelopers.eventqr.features.attendee

import android.os.Looper
import android.content.DialogInterface
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import java.time.LocalDateTime
import java.time.ZoneId
import org.robolectric.Shadows.shadowOf
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.auth.AuthStripTestKit
import com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RequestEventActivityTest {

    private fun build(): RequestEventActivity =
        Robolectric.buildActivity(RequestEventActivity::class.java).setup().get()

    private fun submit(activity: RequestEventActivity) =
        activity.findViewById<Button>(R.id.submitRequestButton).performClick()

    private fun contact(activity: RequestEventActivity): EditText = activity.findViewById(R.id.contactNumberInput)

    /** Robolectric windows never report window focus, so Activity.currentFocus is null; ask the tree. */
    private fun focusedId(activity: RequestEventActivity): Int? = activity.window.decorView.findFocus()?.id

    private fun helperText(activity: RequestEventActivity, id: Int): String? {
        val helper = activity.findViewById<TextView>(id)
        return if (helper.visibility == View.VISIBLE) helper.text.toString() else null
    }

    /** Every field filled and valid, so a submit passes validation and reaches the repository. */
    private fun fillValid(activity: RequestEventActivity) {
        fun field(id: Int, value: String) = activity.findViewById<EditText>(id).setText(value)
        field(R.id.eventNameInput, "Tech Talk")
        field(R.id.eventDescriptionInput, "A talk")
        field(R.id.targetAudienceInput, "Students")
        field(R.id.capacityInput, "50")
        field(R.id.venueInput, "Hall A")
        field(R.id.requesterNameInput, "Ana Cruz")
        field(R.id.contactEmailInput, "ana@example.com")
        field(R.id.contactNumberInput, "+63 912 345 6789")
        field(R.id.reasonForRequestInput, "Club event")
        activity.selectedEventCategory = "Seminar"
        activity.findViewById<TextView>(R.id.txtSelectedEventCategory).text = "Seminar"
        val base = LocalDateTime.now(ZoneId.of("Asia/Manila")).plusDays(10).withNano(0)
        activity.registrationStartDateTimeValue = base
        activity.registrationEndDateTimeValue = base.plusDays(2)
        activity.startDateTimeValue = base.plusDays(3)
        activity.endDateTimeValue = base.plusDays(3).plusHours(2)
    }

    private val allHelpers = listOf(
        R.id.txtEventNameError, R.id.txtEventCategoryError, R.id.txtEventDescriptionError,
        R.id.txtTargetAudienceError, R.id.txtCapacityError, R.id.txtVenueError,
        R.id.txtStartDateTimeError, R.id.txtEndDateTimeError, R.id.txtRegistrationStartDateTimeError,
        R.id.txtRegistrationEndDateTimeError, R.id.txtRequesterNameError, R.id.txtContactEmailError,
        R.id.txtContactNumberError, R.id.txtReasonForRequestError,
    )

    /** Dialogs outlive their test otherwise; the next test would inherit a stale surface. */
    @After
    fun dismissOpenSurfaces() {
        ShadowDialog.getShownDialogs().toList().forEach { dialog ->
            if (dialog.isShowing) dialog.dismiss()
        }
    }

    // ---- RQ-3: contact number uses the shared phone rule

    @Test
    fun tooShortContactNumber_isRejectedWithInlineError() {
        val activity = build()
        contact(activity).setText("12")

        submit(activity)

        assertEquals(activity.getString(R.string.request_event_invalid_contact_number), helperText(activity, R.id.txtContactNumberError))
        assertTrue("the box takes the error stroke", contact(activity).isActivated)
        assertNull("the EditText.error balloon is banned", contact(activity).error)
    }

    @Test
    fun hintFormatContactNumber_withSpaces_isAccepted() {
        val activity = build()
        contact(activity).setText("+63 912 345 6789")

        submit(activity)

        assertEquals(View.GONE, activity.findViewById<View>(R.id.txtContactNumberError).visibility)
        assertFalse(contact(activity).isActivated)
    }

    @Test
    fun blankContactNumber_keepsTheRequiredMessage() {
        val activity = build()
        contact(activity).setText("")

        submit(activity)

        assertEquals("Contact number is required", helperText(activity, R.id.txtContactNumberError))
    }

    @Test
    fun hintFormatContactNumber_isSentAsE164InTheRequestPayload() {
        val activity = build()
        val repo = CapturingRepo()
        activity.repository = repo
        fun field(id: Int, value: String) = activity.findViewById<EditText>(id).setText(value)
        field(R.id.eventNameInput, "Tech Talk")
        field(R.id.eventDescriptionInput, "A talk")
        field(R.id.targetAudienceInput, "Students")
        field(R.id.capacityInput, "50")
        field(R.id.venueInput, "Hall A")
        field(R.id.requesterNameInput, "Ana Cruz")
        field(R.id.contactEmailInput, "ana@example.com")
        field(R.id.reasonForRequestInput, "Club event")
        contact(activity).setText("+63 912-345 6789")
        activity.selectedEventCategory = "Seminar"
        val base = LocalDateTime.now(ZoneId.of("Asia/Manila")).plusDays(10).withNano(0)
        activity.registrationStartDateTimeValue = base
        activity.registrationEndDateTimeValue = base.plusDays(2)
        activity.startDateTimeValue = base.plusDays(3)
        activity.endDateTimeValue = base.plusDays(3).plusHours(2)

        submit(activity)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("+639123456789"), repo.requests.map { it.contactNumber })
    }

    // ---- Audit X5: the poster block is ORGANIZER/ADMIN/SUPER_ADMIN only (upload endpoint 403s below)

    @Test
    fun posterBlock_isHiddenForAttendeeAndStaff() {
        listOf(AccountRole.ATTENDEE, AccountRole.STAFF).forEach { role ->
            SessionManager(ApplicationProvider.getApplicationContext()).saveRole(role)
            val activity = build()

            assertEquals(role.name, View.GONE, activity.findViewById<View>(R.id.layoutPosterSection).visibility)
        }
    }

    @Test
    fun posterBlock_isShownFromOrganizerUp() {
        listOf(AccountRole.ORGANIZER, AccountRole.ADMIN, AccountRole.SUPER_ADMIN).forEach { role ->
            SessionManager(ApplicationProvider.getApplicationContext()).saveRole(role)
            val activity = build()

            assertEquals(role.name, View.VISIBLE, activity.findViewById<View>(R.id.layoutPosterSection).visibility)
        }
    }

    private class CapturingRepo : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        val requests = mutableListOf<EventCreationRequestDto>()
        override suspend fun createEventRequest(request: EventCreationRequestDto): NetworkResult<EventRequestResponse> {
            requests += request
            return NetworkResult.Error("stop here")
        }
    }

    // ---- RQ-2: scroll-to-first-error targets the field's position in the whole form

    @Test
    fun scrollTarget_isTheFieldsOffsetInTheFormNotInItsCard() {
        val activity = build()
        val contactField = contact(activity)
        val nameField = activity.findViewById<EditText>(R.id.eventNameInput)

        val contactTarget = activity.scrollTargetFor(contactField)

        // The form is one column of fields (no per-section card), so the target is the field's own
        // top in the scroll content minus the fixed "room for its label" offset.
        val labelRoom = activity.resources.getDimensionPixelSize(R.dimen.space_xxl)
        assertEquals((contactField.top - labelRoom).coerceAtLeast(0), contactTarget)
        assertTrue(
            "name target should sit above the contact target",
            activity.scrollTargetFor(nameField) < contactTarget,
        )
    }

    // ---- Section 6.8 row 6: field errors inline, first invalid field focused, no toast

    @Test
    fun emptySubmit_marksEveryInvalidFieldAndFocusesTheFirst_withoutAToast() {
        val activity = build()
        // Requester name and email are prefilled from the session; blank them so every field fails.
        activity.findViewById<EditText>(R.id.requesterNameInput).setText("")
        activity.findViewById<EditText>(R.id.contactEmailInput).setText("")

        submit(activity)

        val expected = mapOf(
            R.id.txtEventNameError to "Event name is required",
            R.id.txtEventCategoryError to "Event category is required",
            R.id.txtEventDescriptionError to "Event description is required",
            R.id.txtTargetAudienceError to "Target audience is required",
            R.id.txtCapacityError to "Capacity must be a positive number",
            R.id.txtVenueError to "Location is required",
            R.id.txtStartDateTimeError to "Start date/time is required",
            R.id.txtEndDateTimeError to "End date/time is required",
            R.id.txtRegistrationStartDateTimeError to "Registration start date/time is required",
            R.id.txtRegistrationEndDateTimeError to "Registration end date/time is required",
            R.id.txtRequesterNameError to "Requester name is required",
            R.id.txtContactEmailError to "Contact email is required",
            R.id.txtContactNumberError to "Contact number is required",
            R.id.txtReasonForRequestError to "Reason for request is required",
        )
        expected.forEach { (id, message) -> assertEquals(message, helperText(activity, id)) }
        assertEquals("one focus target: the topmost invalid field", R.id.eventNameInput, focusedId(activity))
        assertNull("no toast", ShadowToast.getLatestToast())
        assertEquals(
            "field validation never reaches the page strip",
            View.GONE,
            activity.findViewById<View>(R.id.errorRequestEvent).visibility,
        )
        listOf(R.id.eventNameInput, R.id.cardEventCategory, R.id.reasonForRequestInput).forEach {
            assertTrue(activity.findViewById<View>(it).isActivated)
        }
    }

    @Test
    fun onlyPickerFieldsInvalid_focusMovesToTheFirstInvalidControl() {
        val activity = build()
        fillValid(activity)
        activity.startDateTimeValue = null
        activity.endDateTimeValue = null
        activity.selectedEventCategory = ""

        submit(activity)

        assertEquals(
            listOf(R.id.txtEventCategoryError, R.id.txtStartDateTimeError, R.id.txtEndDateTimeError),
            allHelpers.filter { helperText(activity, it) != null },
        )
        assertEquals("the category selector is the first invalid control", R.id.cardEventCategory, focusedId(activity))
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun pickerFieldIsFocusable_butNotInTouchMode_soTheFirstTapStillOpensThePicker() {
        val activity = build()
        val start = activity.findViewById<EditText>(R.id.startDateTimeInput)

        assertTrue(start.isFocusable)
        assertFalse(start.isFocusableInTouchMode)
    }

    @Test
    fun editingAField_clearsItsErrorAndLeavesTheOthers() {
        val activity = build()
        submit(activity)
        assertEquals("Event name is required", helperText(activity, R.id.txtEventNameError))
        assertEquals("Location is required", helperText(activity, R.id.txtVenueError))

        activity.findViewById<EditText>(R.id.eventNameInput).setText("T")

        assertNull(helperText(activity, R.id.txtEventNameError))
        assertFalse(activity.findViewById<View>(R.id.eventNameInput).isActivated)
        assertEquals("Location is required", helperText(activity, R.id.txtVenueError))
    }

    @Test
    fun validationOrder_isUnchanged_endBeforeStartFlagsTheEndField() {
        val activity = build()
        fillValid(activity)
        activity.endDateTimeValue = activity.startDateTimeValue!!.minusHours(1)

        submit(activity)

        assertEquals("End date/time must be after the start", helperText(activity, R.id.txtEndDateTimeError))
        assertEquals(R.id.endDateTimeInput, focusedId(activity))
    }

    // ---- Section 5.16: a server failure is one strip above the submit area, values kept, Retry re-runs

    @Test
    fun serverFailure_showsOneStripWithRetry_keepsValues_andRetryResubmits() {
        val activity = build()
        val repo = CapturingRepo()
        activity.repository = repo
        fillValid(activity)

        submit(activity)
        shadowOf(Looper.getMainLooper()).idle()

        val strip = activity.findViewById<View>(R.id.errorRequestEvent)
        assertEquals(View.VISIBLE, strip.visibility)
        val sentence = activity.findViewById<TextView>(R.id.txtErrorStrip).text.toString()
        assertEquals(activity.getString(R.string.ui_attendee_request_submit_failed), sentence)
        assertFalse("server text stays in the log", sentence.contains("stop here"))
        assertNull("no toast", ShadowToast.getLatestToast())
        assertTrue("no field is blamed for a page failure", allHelpers.all { helperText(activity, it) == null })
        assertEquals("Tech Talk", activity.findViewById<EditText>(R.id.eventNameInput).text.toString())
        assertTrue(activity.findViewById<Button>(R.id.submitRequestButton).isEnabled)
        assertEquals(1, repo.requests.size)
        assertEquals(
            "a create is not idempotent and the failure may have followed a stored write: no one-tap Retry",
            View.GONE,
            activity.findViewById<View>(R.id.btnErrorStripRetry).visibility,
        )

        // The deliberate act is the Submit press, which still works with the values kept.
        submit(activity)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Submit re-runs the request", 2, repo.requests.size)
        assertEquals("still exactly one strip", View.VISIBLE, strip.visibility)
    }

    @Test
    fun createFailure_withANetworkError_hidesRetryToo() {
        val activity = build()
        activity.repository = object : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
            override suspend fun createEventRequest(request: EventCreationRequestDto): NetworkResult<EventRequestResponse> =
                NetworkResult.Error("timeout", java.net.SocketTimeoutException())
        }
        fillValid(activity)

        submit(activity)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.errorRequestEvent).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnErrorStripRetry).visibility)
    }

    private class FailingUploadRepo : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        var uploads = 0
        var creates = 0
        override suspend fun uploadEventPoster(file: java.io.File): NetworkResult<StoredFileResponse> {
            uploads++
            return NetworkResult.Error("upload failed", java.io.IOException())
        }
        override suspend fun createEventRequest(request: EventCreationRequestDto): NetworkResult<EventRequestResponse> {
            creates++
            return NetworkResult.Error("stop here")
        }
    }

    @Test
    fun posterUploadFailure_showsTheStripWithRetry_announcesIt_andRetryReRunsTheUpload() {
        AuthStripTestKit.enableAccessibility()
        val activity = build()
        val repo = FailingUploadRepo()
        activity.repository = repo
        fillValid(activity)
        activity.selectedPosterFile = java.io.File.createTempFile("poster", ".jpg")

        submit(activity)
        shadowOf(Looper.getMainLooper()).idle()

        val message = activity.getString(R.string.ui_attendee_request_poster_upload_failed)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.errorRequestEvent).visibility)
        assertEquals(message, activity.findViewById<TextView>(R.id.txtErrorStrip).text.toString())
        assertEquals("nothing was created yet, so repeating is safe", View.VISIBLE, activity.findViewById<View>(R.id.btnErrorStripRetry).visibility)
        assertEquals(listOf(message), AuthStripTestKit.announcements())
        assertEquals(0, repo.creates)

        activity.findViewById<View>(R.id.btnErrorStripRetry).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Retry re-runs the upload", 2, repo.uploads)
    }

    @Test
    fun rotation_keepsWhetherTheStripOffersRetry() {
        val controller = Robolectric.buildActivity(RequestEventActivity::class.java).setup()
        val before = controller.get()
        before.repository = FailingUploadRepo()
        fillValid(before)
        before.selectedPosterFile = java.io.File.createTempFile("poster", ".jpg")
        submit(before)
        shadowOf(Looper.getMainLooper()).idle()

        controller.recreate()
        val afterUpload = controller.get()
        assertEquals(View.VISIBLE, afterUpload.findViewById<View>(R.id.btnErrorStripRetry).visibility)

        afterUpload.repository = CapturingRepo()
        fillValid(afterUpload)
        submit(afterUpload)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, afterUpload.findViewById<View>(R.id.btnErrorStripRetry).visibility)

        controller.recreate()

        assertEquals(View.VISIBLE, controller.get().findViewById<View>(R.id.errorRequestEvent).visibility)
        assertEquals(View.GONE, controller.get().findViewById<View>(R.id.btnErrorStripRetry).visibility)
    }

    // ---- One announcement per failed submit: the first message plus how many more fields need attention

    @Test
    fun failedValidation_announcesTheFirstMessageAndTheCountOfTheRest() {
        AuthStripTestKit.enableAccessibility()
        val activity = build()
        activity.findViewById<EditText>(R.id.requesterNameInput).setText("")
        activity.findViewById<EditText>(R.id.contactEmailInput).setText("")

        submit(activity)

        assertEquals(
            listOf("Event name is required. 13 more fields need attention."),
            AuthStripTestKit.announcements(),
        )
    }

    @Test
    fun failedValidation_withOneOtherField_usesTheSingularForm() {
        AuthStripTestKit.enableAccessibility()
        val activity = build()
        fillValid(activity)
        activity.findViewById<EditText>(R.id.eventNameInput).setText("")
        activity.findViewById<EditText>(R.id.venueInput).setText("")

        submit(activity)

        assertEquals(
            listOf("Event name is required. 1 more field needs attention."),
            AuthStripTestKit.announcements(),
        )
    }

    @Test
    fun failedValidation_withOneInvalidField_announcesJustItsMessage() {
        AuthStripTestKit.enableAccessibility()
        val activity = build()
        fillValid(activity)
        activity.findViewById<EditText>(R.id.eventNameInput).setText("")

        submit(activity)

        assertEquals(listOf("Event name is required"), AuthStripTestKit.announcements())
    }

    // ---- A rejected past time leaves no stale value behind a red stroke

    @Test
    fun aPastTimePick_clearsTheBoxAndTheStoredValue_andShowsTheError() {
        val activity = build()
        fillValid(activity)
        val start = activity.findViewById<EditText>(R.id.startDateTimeInput)
        // A valid value from earlier today (late evening), so the picker opens on today.
        activity.startDateTimeValue = LocalDateTime.now(ZoneId.of("Asia/Manila")).withHour(23).withMinute(59).withSecond(0).withNano(0)
        start.setText("earlier valid value")

        start.performClick()
        (ShadowDialog.getLatestDialog() as android.app.DatePickerDialog)
            .getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val timeDialog = ShadowDialog.getShownDialogs().filterIsInstance<android.app.TimePickerDialog>().last()
        // Today, at 00:00: earlier than now (Manila) whenever the test runs after midnight.
        timeDialog.updateTime(0, 0)
        timeDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull("the stored value is gone", activity.startDateTimeValue)
        assertEquals("the box matches it", "", start.text.toString())
        assertEquals(
            activity.getString(R.string.request_event_selected_date_time_cannot_be_in_the),
            helperText(activity, R.id.txtStartDateTimeError),
        )
        assertTrue(start.isActivated)
    }

    /**
     * The review promise belongs to the action it qualifies. It used to open the scroll content,
     * where it was read once as preamble and scrolled away from; it is now the footer's own node,
     * above Submit, never inside the ScrollView and never a hint on the button.
     */
    @Test
    fun reviewCaption_sitsInTheSubmitBarAboveSubmit_withNoLiveRegionOrLabelFor() {
        val activity = build()
        val caption = activity.findViewById<TextView>(R.id.txtSubmitReviewNote)
        val bar = activity.findViewById<android.view.ViewGroup>(R.id.layoutSubmitBar)

        assertEquals(bar, caption.parent)
        assertEquals(
            "the caption is the first thing in the bar, above the primary",
            0,
            bar.indexOfChild(caption),
        )
        assertTrue(
            "the caption comes before Submit",
            bar.indexOfChild(caption) < bar.indexOfChild(activity.findViewById(R.id.submitRequestButton)),
        )
        assertEquals(
            activity.getString(R.string.request_event_admin_review_note),
            caption.text.toString(),
        )
        assertEquals(
            "the run copy, not the old one-liner",
            "An admin reviews your request. You'll be notified when it's approved or rejected.",
            caption.text.toString(),
        )
        assertEquals(View.VISIBLE, caption.visibility)

        var node: View? = caption
        var insideScroll = false
        while (node != null) {
            if (node is android.widget.ScrollView) insideScroll = true
            node = node.parent as? View
        }
        assertFalse("the caption is not scroll content", insideScroll)

        // Its own ordinary node: no live region, no labelFor, no contentDescription, no maxLines cap.
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, caption.accessibilityLiveRegion)
        assertEquals("the caption names no other view", View.NO_ID, caption.labelFor)
        assertNull(caption.contentDescription)
        assertEquals("the line wraps instead of truncating at 2.0", Int.MAX_VALUE, caption.maxLines)
    }

    @Test
    fun theStrip_sitsBetweenTheFormAndTheSubmitArea() {
        val activity = build()
        val root = activity.findViewById<View>(R.id.errorRequestEvent).parent as android.view.ViewGroup
        val scroll = root.indexOfChild(activity.findViewById(R.id.formScrollView))
        val strip = root.indexOfChild(activity.findViewById(R.id.errorRequestEvent))
        val footer = root.indexOfChild(activity.findViewById(R.id.layoutSubmitBar))

        assertTrue("form, then strip, then footer", scroll < strip && strip < footer)
    }

    @Test
    fun editingFields_doesNotDismissTheStrip_butAnInvalidSubmitReplacesIt() {
        val activity = build()
        activity.repository = CapturingRepo()
        fillValid(activity)
        submit(activity)
        shadowOf(Looper.getMainLooper()).idle()
        val strip = activity.findViewById<View>(R.id.errorRequestEvent)
        assertEquals(View.VISIBLE, strip.visibility)

        // A field edit is not a retry: the page failure is still true.
        activity.findViewById<EditText>(R.id.venueInput).setText("Hall B")
        assertEquals(View.VISIBLE, strip.visibility)

        // Invalid input on the next submit shows field errors only, never both surfaces.
        activity.findViewById<EditText>(R.id.eventNameInput).setText("")
        submit(activity)
        assertEquals(View.GONE, strip.visibility)
        assertEquals("Event name is required", helperText(activity, R.id.txtEventNameError))
    }

    // ---- Rotation: errors, focus, strip and picker values survive recreation

    @Test
    fun rotation_keepsFieldErrorsAndFocus() {
        val controller = Robolectric.buildActivity(RequestEventActivity::class.java).setup()
        val before = controller.get()
        before.findViewById<EditText>(R.id.requesterNameInput).setText("")
        submit(before)
        assertEquals(R.id.eventNameInput, focusedId(before))
        before.findViewById<EditText>(R.id.eventNameInput).setText("Tech Talk")
        before.findViewById<EditText>(R.id.venueInput).requestFocus()

        controller.recreate()
        val after = controller.get()

        assertNull("an edited field stays clear after the typed text is restored", helperText(after, R.id.txtEventNameError))
        assertEquals("Tech Talk", after.findViewById<EditText>(R.id.eventNameInput).text.toString())
        assertEquals("Location is required", helperText(after, R.id.txtVenueError))
        assertEquals("Event category is required", helperText(after, R.id.txtEventCategoryError))
        assertEquals("Start date/time is required", helperText(after, R.id.txtStartDateTimeError))
        assertTrue(after.findViewById<View>(R.id.venueInput).isActivated)
        assertTrue(after.findViewById<View>(R.id.cardEventCategory).isActivated)
        assertEquals("focus returns to the field that had it", R.id.venueInput, focusedId(after))
    }

    @Test
    fun rotation_keepsPickerValuesAndCategory_soSubmitDoesNotReportThemMissing() {
        val controller = Robolectric.buildActivity(RequestEventActivity::class.java).setup()
        val before = controller.get()
        fillValid(before)
        val start = before.startDateTimeValue

        controller.recreate()
        val after = controller.get()

        assertEquals(start, after.startDateTimeValue)
        assertEquals("Seminar", after.selectedEventCategory)
        assertEquals("Seminar", after.findViewById<TextView>(R.id.txtSelectedEventCategory).text.toString())

        val repo = CapturingRepo()
        after.repository = repo
        submit(after)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("validation passes after the rotation", 1, repo.requests.size)
    }

    @Test
    fun rotation_keepsTheServerFailureStrip() {
        val controller = Robolectric.buildActivity(RequestEventActivity::class.java).setup()
        val before = controller.get()
        before.repository = CapturingRepo()
        fillValid(before)
        submit(before)
        shadowOf(Looper.getMainLooper()).idle()

        controller.recreate()
        val after = controller.get()

        assertEquals(View.VISIBLE, after.findViewById<View>(R.id.errorRequestEvent).visibility)
        assertEquals(
            after.getString(R.string.ui_attendee_request_submit_failed),
            after.findViewById<TextView>(R.id.txtErrorStrip).text.toString(),
        )
        assertEquals("the create failure still offers no Retry", View.GONE, after.findViewById<View>(R.id.btnErrorStripRetry).visibility)
    }

    // ---- POST_NOTIFICATIONS priming must not stack on the success dialog or exit underneath a dialog

    @Test
    fun decisionRationale_neverStacksOnTheSuccessDialog_andTheExitWaitsForIt() {
        val activity = build()
        assertEquals("no prompt on screen open", 0, ShadowDialog.getShownDialogs().count { it.isShowing })

        activity.showSuccessDialog()
        val success = ShadowDialog.getLatestDialog()
        assertTrue(success.isShowing)

        success.findViewById<Button>(R.id.dialogPositive).performClick()

        val rationale = ShadowDialog.getLatestDialog() as AlertDialog
        assertFalse("the success dialog must close before the rationale shows", success.isShowing)
        assertEquals("never two dialogs at once", 1, ShadowDialog.getShownDialogs().count { it.isShowing })
        assertTrue(rationale.isShowing)
        assertEquals(
            activity.getString(R.string.ui_attendee_decisions_permission_body),
            rationale.findViewById<TextView>(android.R.id.message)?.text?.toString(),
        )
        assertFalse("finish() must wait for the permission flow", activity.isFinishing)

        rationale.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("the exit runs once the rationale is resolved", activity.isFinishing)
        assertEquals(
            MyEventRequestsActivity::class.java.name,
            shadowOf(activity).nextStartedActivity?.component?.className,
        )
    }
}
