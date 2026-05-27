package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class RequestEventActivity : AppCompatActivity() {
    private lateinit var repository: AttendeeRepository
    private lateinit var sessionManager: SessionManager

    private lateinit var eventNameInput: EditText
    private lateinit var eventDescriptionInput: EditText
    private lateinit var eventCategoryInput: EditText
    private lateinit var targetAudienceInput: EditText
    private lateinit var capacityInput: EditText
    private lateinit var venueInput: EditText
    private lateinit var startDateTimeInput: EditText
    private lateinit var endDateTimeInput: EditText
    private lateinit var registrationStartDateTimeInput: EditText
    private lateinit var registrationEndDateTimeInput: EditText
    private lateinit var requesterNameInput: EditText
    private lateinit var contactEmailInput: EditText
    private lateinit var contactNumberInput: EditText
    private lateinit var eventLogoInput: EditText
    private lateinit var additionalNotesInput: EditText
    private lateinit var reasonForRequestInput: EditText
    private lateinit var formMessageText: TextView
    private lateinit var submitProgress: ProgressBar
    private lateinit var submitButton: Button
    private lateinit var viewMyRequestsButton: Button

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_event)

        repository = AttendeeRepository(this)
        sessionManager = SessionManager(this)
        bindViews()
        prefillRequester()

        findViewById<TextView>(R.id.backText).setOnClickListener { finish() }
        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }
        submitButton.setOnClickListener { submitRequest() }
        viewMyRequestsButton.setOnClickListener {
            startActivity(Intent(this, MyEventRequestsActivity::class.java))
        }
    }

    private fun bindViews() {
        eventNameInput = findViewById(R.id.eventNameInput)
        eventDescriptionInput = findViewById(R.id.eventDescriptionInput)
        eventCategoryInput = findViewById(R.id.eventCategoryInput)
        targetAudienceInput = findViewById(R.id.targetAudienceInput)
        capacityInput = findViewById(R.id.capacityInput)
        venueInput = findViewById(R.id.venueInput)
        startDateTimeInput = findViewById(R.id.startDateTimeInput)
        endDateTimeInput = findViewById(R.id.endDateTimeInput)
        registrationStartDateTimeInput = findViewById(R.id.registrationStartDateTimeInput)
        registrationEndDateTimeInput = findViewById(R.id.registrationEndDateTimeInput)
        requesterNameInput = findViewById(R.id.requesterNameInput)
        contactEmailInput = findViewById(R.id.contactEmailInput)
        contactNumberInput = findViewById(R.id.contactNumberInput)
        eventLogoInput = findViewById(R.id.eventLogoInput)
        additionalNotesInput = findViewById(R.id.additionalNotesInput)
        reasonForRequestInput = findViewById(R.id.reasonForRequestInput)
        formMessageText = findViewById(R.id.formMessageText)
        submitProgress = findViewById(R.id.submitProgress)
        submitButton = findViewById(R.id.submitRequestButton)
        viewMyRequestsButton = findViewById(R.id.viewMyRequestsButton)
    }

    private fun prefillRequester() {
        requesterNameInput.setText(sessionManager.getFullName().orEmpty())
        contactEmailInput.setText(sessionManager.getEmail().orEmpty())

        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    if (requesterNameInput.textString().isBlank()) {
                        requesterNameInput.setText(result.data.fullName)
                    }
                    if (contactEmailInput.textString().isBlank()) {
                        contactEmailInput.setText(result.data.email)
                    }
                    if (contactNumberInput.textString().isBlank()) {
                        contactNumberInput.setText(result.data.phoneNumber.orEmpty())
                    }
                }
                is NetworkResult.Error -> Unit
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun submitRequest() {
        hideMessage()
        val request = buildValidatedRequest() ?: return
        setLoading(true)

        lifecycleScope.launch {
            when (val result = repository.createEventRequest(request)) {
                is NetworkResult.Success -> {
                    setLoading(false)
                    showMessage("Request submitted successfully. Status: ${result.data.status.name.lowercase().replaceFirstChar { it.uppercase() }}.")
                    viewMyRequestsButton.visibility = View.VISIBLE
                    Toast.makeText(this@RequestEventActivity, "Event request submitted", Toast.LENGTH_SHORT).show()
                }
                is NetworkResult.Error -> {
                    setLoading(false)
                    showMessage(result.message.ifBlank { "Could not submit request. Please try again." })
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun buildValidatedRequest(): EventCreationRequestDto? {
        clearFieldErrors()

        val eventName = eventNameInput.required("Event name is required") ?: return null
        val eventDescription = eventDescriptionInput.required("Event description is required") ?: return null
        val eventCategory = eventCategoryInput.required("Event category is required") ?: return null
        val venue = venueInput.required("Venue/location is required") ?: return null
        val startDateTime = startDateTimeInput.requiredInstant("Start date/time is required") ?: return null
        val endDateTime = endDateTimeInput.requiredInstant("End date/time is required") ?: return null
        val capacity = capacityInput.positiveInt("Capacity must be a positive number") ?: return null
        val requesterName = requesterNameInput.required("Requester name is required") ?: return null
        val contactEmail = contactEmailInput.required("Contact email is required") ?: return null
        val contactNumber = contactNumberInput.required("Contact number is required") ?: return null
        val reason = reasonForRequestInput.required("Reason for request is required") ?: return null

        if (!Validators.isValidEmail(contactEmail)) {
            contactEmailInput.error = "Enter a valid email address"
            contactEmailInput.requestFocus()
            return null
        }
        if (!endDateTime.isAfter(startDateTime)) {
            endDateTimeInput.error = "End date/time must be after the start"
            endDateTimeInput.requestFocus()
            return null
        }

        val registrationStart = if (registrationStartDateTimeInput.textString().isBlank()) {
            null
        } else {
            registrationStartDateTimeInput.parseInstantOrMarkError() ?: return null
        }
        val registrationEnd = if (registrationEndDateTimeInput.textString().isBlank()) {
            null
        } else {
            registrationEndDateTimeInput.parseInstantOrMarkError() ?: return null
        }
        if (registrationStart != null && registrationEnd != null && !registrationEnd.isAfter(registrationStart)) {
            registrationEndDateTimeInput.error = "Registration end must be after registration start"
            registrationEndDateTimeInput.requestFocus()
            return null
        }
        if (registrationEnd != null && registrationEnd.isAfter(endDateTime)) {
            registrationEndDateTimeInput.error = "Registration end must not be after event end"
            registrationEndDateTimeInput.requestFocus()
            return null
        }

        return EventCreationRequestDto(
            eventName = eventName,
            eventDescription = eventDescription,
            eventCategory = eventCategory,
            targetAudience = targetAudienceInput.optionalText(),
            capacity = capacity,
            venue = venue,
            startDateTime = startDateTime.toString(),
            endDateTime = endDateTime.toString(),
            registrationStartDateTime = registrationStart?.toString(),
            registrationEndDateTime = registrationEnd?.toString(),
            requesterName = requesterName,
            contactEmail = contactEmail,
            contactNumber = contactNumber,
            requestedFeatures = selectedFeatures().ifEmpty { null },
            eventLogoUrl = eventLogoInput.optionalText(),
            additionalNotes = additionalNotesInput.optionalText(),
            reasonForRequest = reason,
        )
    }

    private fun selectedFeatures(): List<String> {
        return listOf(
            R.id.featureQrRegistration to "QR registration",
            R.id.featureQrCheckIn to "QR check-in / entry logging",
            R.id.featureAttendanceTracking to "Attendance tracking",
            R.id.featureBenefitClaiming to "Benefit claiming",
            R.id.featureBoothTracking to "Booth/session tracking",
            R.id.featureRewardsPoints to "Rewards and points",
            R.id.featureIdPrinting to "ID printing",
        ).mapNotNull { (id, label) ->
            label.takeIf { findViewById<CheckBox>(id).isChecked }
        }
    }

    private fun setLoading(loading: Boolean) {
        submitProgress.visibility = if (loading) View.VISIBLE else View.GONE
        submitButton.isEnabled = !loading
        submitButton.text = if (loading) "Submitting..." else "Submit Request"
    }

    private fun showMessage(message: String) {
        formMessageText.text = message
        formMessageText.visibility = View.VISIBLE
    }

    private fun hideMessage() {
        formMessageText.text = ""
        formMessageText.visibility = View.GONE
    }

    private fun clearFieldErrors() {
        listOf(
            eventNameInput, eventDescriptionInput, eventCategoryInput, targetAudienceInput,
            capacityInput, venueInput, startDateTimeInput, endDateTimeInput,
            registrationStartDateTimeInput, registrationEndDateTimeInput,
            requesterNameInput, contactEmailInput, contactNumberInput,
            eventLogoInput, additionalNotesInput, reasonForRequestInput,
        ).forEach { it.error = null }
    }

    private fun EditText.required(errorMessage: String): String? {
        val value = textString()
        if (value.isBlank()) {
            error = errorMessage
            requestFocus()
            return null
        }
        return value
    }

    private fun EditText.positiveInt(errorMessage: String): Int? {
        val value = textString().toIntOrNull()
        if (value == null || value <= 0) {
            error = errorMessage
            requestFocus()
            return null
        }
        return value
    }

    private fun EditText.requiredInstant(requiredMessage: String): Instant? {
        if (textString().isBlank()) {
            error = requiredMessage
            requestFocus()
            return null
        }
        return parseInstantOrMarkError()
    }

    private fun EditText.parseInstantOrMarkError(): Instant? {
        return try {
            LocalDateTime.parse(textString(), dateTimeFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        } catch (_: DateTimeParseException) {
            error = "Use format yyyy-MM-dd HH:mm"
            requestFocus()
            null
        }
    }

    private fun EditText.optionalText(): String? = textString().takeIf { it.isNotBlank() }

    private fun EditText.textString(): String = text?.toString()?.trim().orEmpty()
}
