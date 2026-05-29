package com.thedavelopers.eventqr.features.attendee

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    private lateinit var reasonForRequestInput: EditText
    private lateinit var formMessageText: TextView
    private lateinit var submitProgress: ProgressBar
    private lateinit var submitButton: Button
    private var successDialog: AlertDialog? = null
    private var isSubmitting = false

    private val displayDateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    private val zoneId: ZoneId = ZoneId.of("Asia/Manila")

    private var startDateTimeValue: LocalDateTime? = null
    private var endDateTimeValue: LocalDateTime? = null
    private var registrationStartDateTimeValue: LocalDateTime? = null
    private var registrationEndDateTimeValue: LocalDateTime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_event)

        repository = AttendeeRepository(this)
        sessionManager = SessionManager(this)
        bindViews()
        prefillRequester()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.backText).setOnClickListener { finish() }
        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }
        submitButton.setOnClickListener { submitRequest() }

        configureDateTimeField(startDateTimeInput, { startDateTimeValue }) { value ->
            startDateTimeValue = value
            startDateTimeInput.setText(formatForDisplay(value))
            startDateTimeInput.error = null
            if (endDateTimeValue != null && !endDateTimeValue!!.isAfter(value)) {
                endDateTimeValue = null
                endDateTimeInput.text?.clear()
                endDateTimeInput.error = "End date/time must be after the start"
            }
            if (registrationEndDateTimeValue != null && !registrationEndDateTimeValue!!.isBefore(value)) {
                registrationEndDateTimeValue = null
                registrationEndDateTimeInput.text?.clear()
                registrationEndDateTimeInput.error = "Registration end must be before event start"
            }
        }
        configureDateTimeField(endDateTimeInput, { endDateTimeValue }) { value ->
            endDateTimeValue = value
            endDateTimeInput.setText(formatForDisplay(value))
            endDateTimeInput.error = null
            if (startDateTimeValue != null && !value.isAfter(startDateTimeValue)) {
                endDateTimeValue = null
                endDateTimeInput.text?.clear()
                endDateTimeInput.error = "End date/time must be after the start"
            }
        }
        configureDateTimeField(registrationStartDateTimeInput, { registrationStartDateTimeValue }) { value ->
            registrationStartDateTimeValue = value
            registrationStartDateTimeInput.setText(formatForDisplay(value))
            registrationStartDateTimeInput.error = null
            if (registrationEndDateTimeValue != null && !registrationEndDateTimeValue!!.isAfter(value)) {
                registrationEndDateTimeValue = null
                registrationEndDateTimeInput.text?.clear()
                registrationEndDateTimeInput.error = "Registration end must be after registration start"
            }
        }
        configureDateTimeField(registrationEndDateTimeInput, { registrationEndDateTimeValue }) { value ->
            registrationEndDateTimeValue = value
            registrationEndDateTimeInput.setText(formatForDisplay(value))
            registrationEndDateTimeInput.error = null
            if (registrationStartDateTimeValue != null && !value.isAfter(registrationStartDateTimeValue)) {
                registrationEndDateTimeValue = null
                registrationEndDateTimeInput.text?.clear()
                registrationEndDateTimeInput.error = "Registration end must be after registration start"
                return@configureDateTimeField
            }
            if (startDateTimeValue != null && !value.isBefore(startDateTimeValue)) {
                registrationEndDateTimeValue = null
                registrationEndDateTimeInput.text?.clear()
                registrationEndDateTimeInput.error = "Registration end must be before event start"
            }
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
        reasonForRequestInput = findViewById(R.id.reasonForRequestInput)
        formMessageText = findViewById(R.id.formMessageText)
        submitProgress = findViewById(R.id.submitProgress)
        submitButton = findViewById(R.id.submitRequestButton)
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
        if (isSubmitting) return
        hideMessage()
        val request = buildValidatedRequest() ?: return
        setLoading(true)

        lifecycleScope.launch {
            when (val result = repository.createEventRequest(request)) {
                is NetworkResult.Success -> {
                    setLoading(false)
                    showSuccessDialog()
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
        val targetAudience = targetAudienceInput.required("Target audience is required") ?: return null
        val venue = venueInput.required("Venue/location is required") ?: return null
        val startDateTime = startDateTimeValue ?: run {
            startDateTimeInput.error = "Start date/time is required"
            return null
        }
        val endDateTime = endDateTimeValue ?: run {
            endDateTimeInput.error = "End date/time is required"
            return null
        }
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
        if (startDateTime.isBefore(currentLocalDateTime())) {
            startDateTimeInput.error = "Start date/time cannot be in the past"
            return null
        }
        if (!endDateTime.isAfter(startDateTime)) {
            endDateTimeInput.error = "End date/time must be after the start"
            return null
        }

        val registrationStart = registrationStartDateTimeValue ?: run {
            registrationStartDateTimeInput.error = "Registration start date/time is required"
            return null
        }
        val registrationEnd = registrationEndDateTimeValue ?: run {
            registrationEndDateTimeInput.error = "Registration end date/time is required"
            return null
        }

        if (registrationStart.isBefore(currentLocalDateTime())) {
            registrationStartDateTimeInput.error = "Registration start cannot be in the past"
            return null
        }
        if (!registrationEnd.isAfter(registrationStart)) {
            registrationEndDateTimeInput.error = "Registration end must be after registration start"
            return null
        }
        if (registrationEnd.isBefore(currentLocalDateTime())) {
            registrationEndDateTimeInput.error = "Registration end cannot be in the past"
            return null
        }
        if (registrationEnd.isAfter(endDateTime)) {
            registrationEndDateTimeInput.error = "Registration end must not be after event end"
            return null
        }
        if (!registrationEnd.isBefore(startDateTime)) {
            registrationEndDateTimeInput.error = "Registration end must be before event start"
            return null
        }

        return EventCreationRequestDto(
            eventName = eventName,
            eventDescription = eventDescription,
            eventCategory = eventCategory,
            targetAudience = targetAudience,
            capacity = capacity,
            venue = venue,
            startDateTime = startDateTime.atZone(zoneId).toInstant().toString(),
            endDateTime = endDateTime.atZone(zoneId).toInstant().toString(),
            registrationStartDateTime = registrationStart.atZone(zoneId).toInstant().toString(),
            registrationEndDateTime = registrationEnd.atZone(zoneId).toInstant().toString(),
            requesterName = requesterName,
            contactEmail = contactEmail,
            contactNumber = contactNumber,
            requestedFeatures = null,
            eventLogoUrl = eventLogoInput.optionalText(),
            additionalNotes = null,
            reasonForRequest = reason,
        )
    }

    private fun setLoading(loading: Boolean) {
        isSubmitting = loading
        submitProgress.visibility = if (loading) View.VISIBLE else View.GONE
        submitButton.isEnabled = !loading
        submitButton.text = if (loading) "Submitting..." else "Submit Request"
    }

    private fun showSuccessDialog() {
        if (isFinishing || isDestroyed) return
        successDialog?.dismiss()

        val dialogView = layoutInflater.inflate(R.layout.dialog_event_request_submitted, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.successViewRequestsButton).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, MyEventRequestsActivity::class.java))
            finish()
        }
        dialogView.findViewById<Button>(R.id.successDashboardButton).setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }

        successDialog = dialog
        dialog.show()
    }

    override fun onDestroy() {
        successDialog?.dismiss()
        successDialog = null
        super.onDestroy()
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
            eventLogoInput, reasonForRequestInput,
        ).forEach { it.error = null }
    }

    private fun configureDateTimeField(
        field: EditText,
        getCurrentValue: () -> LocalDateTime?,
        onSelected: (LocalDateTime) -> Unit,
    ) {
        field.isFocusable = false
        field.isFocusableInTouchMode = false
        field.isCursorVisible = false
        field.isLongClickable = false
        field.setTextIsSelectable(false)
        field.setOnClickListener {
            showDateTimePicker(
                initialValue = getCurrentValue() ?: currentLocalDateTime(),
                onSelected = onSelected,
            )
        }
    }

    private fun showDateTimePicker(
        initialValue: LocalDateTime,
        onSelected: (LocalDateTime) -> Unit,
    ) {
        val now = currentLocalDateTime()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                val initialTime = if (selectedDate == initialValue.toLocalDate()) {
                    initialValue.toLocalTime().withSecond(0).withNano(0)
                } else if (selectedDate == now.toLocalDate()) {
                    now.toLocalTime().withSecond(0).withNano(0)
                } else {
                    LocalTime.of(9, 0)
                }

                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        val selectedDateTime = LocalDateTime.of(selectedDate, LocalTime.of(hourOfDay, minute))
                        if (selectedDateTime.isBefore(currentLocalDateTime())) {
                            Toast.makeText(this, "Selected date/time cannot be in the past", Toast.LENGTH_SHORT).show()
                            return@TimePickerDialog
                        }
                        onSelected(selectedDateTime)
                    },
                    initialTime.hour,
                    initialTime.minute,
                    false,
                ).show()
            },
            initialValue.year,
            initialValue.monthValue - 1,
            initialValue.dayOfMonth,
        ).apply {
            datePicker.minDate = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }.show()
    }

    private fun formatForDisplay(value: LocalDateTime): String = value.format(displayDateTimeFormatter)

    private fun currentLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.now(), zoneId)

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

    private fun EditText.optionalText(): String? = textString().takeIf { it.isNotBlank() }

    private fun EditText.textString(): String = text?.toString()?.trim().orEmpty()
}
