package com.thedavelopers.eventqr.features.attendee

import android.os.Looper
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import java.time.LocalDateTime
import java.time.ZoneId
import org.robolectric.Shadows.shadowOf
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RequestEventActivityTest {

    private fun build(): RequestEventActivity =
        Robolectric.buildActivity(RequestEventActivity::class.java).setup().get()

    private fun submit(activity: RequestEventActivity) =
        activity.findViewById<Button>(R.id.submitRequestButton).performClick()

    private fun contact(activity: RequestEventActivity): EditText = activity.findViewById(R.id.contactNumberInput)

    // ---- RQ-3: contact number uses the shared phone rule

    @Test
    fun tooShortContactNumber_isRejectedWithInlineError() {
        val activity = build()
        contact(activity).setText("12")

        submit(activity)

        assertEquals(activity.getString(R.string.request_event_invalid_contact_number), contact(activity).error?.toString())
    }

    @Test
    fun hintFormatContactNumber_withSpaces_isAccepted() {
        val activity = build()
        contact(activity).setText("+63 912 345 6789")

        submit(activity)

        assertNull(contact(activity).error)
    }

    @Test
    fun blankContactNumber_keepsTheRequiredMessage() {
        val activity = build()
        contact(activity).setText("")

        submit(activity)

        assertEquals("Contact number is required", contact(activity).error?.toString())
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

        // View.top is relative to the field's own card, which made the form jump to the top section.
        assertTrue(
            "contact target $contactTarget should be below the card-relative top ${contactField.top}",
            contactTarget > contactField.top,
        )
        assertTrue(contactTarget > activity.scrollTargetFor(nameField))
    }
}
