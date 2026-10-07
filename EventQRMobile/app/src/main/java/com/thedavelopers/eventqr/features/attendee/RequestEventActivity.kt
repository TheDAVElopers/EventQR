package com.thedavelopers.eventqr.features.attendee

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
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
    // EventQR - Event Category constrained to predetermined list via hidden Spinner + custom dropdown (matches Rewards picker style), not in SRS/SDD Event Request Form spec (scope addition)
    private lateinit var txtSelectedEventCategory: TextView
    private lateinit var spinnerEventCategory: Spinner
    private lateinit var cardEventCategory: View
    private lateinit var spinnerArrow: ImageView
    private var selectedEventCategory: String = ""
    private var isCategoryDropdownOpen = false
    private var eventCategoryPopup: PopupWindow? = null
    private val categoryOptions = listOf("Academic", "Seminar", "Workshop", "Sports", "Cultural", "Organizational", "Others")
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
    private lateinit var eventPosterPreview: ImageView
    private lateinit var eventPosterPlaceholder: View
    private lateinit var eventPosterStatusText: TextView
    private lateinit var reasonForRequestInput: EditText
    private lateinit var formMessageText: TextView
    private lateinit var submitProgress: ProgressBar
    private lateinit var submitButton: Button
    private lateinit var formScrollView: ScrollView
    private var successDialog: AlertDialog? = null
    private var isSubmitting = false
    private var selectedPosterFile: File? = null

    private companion object {
        val ALLOWED_POSTER_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png")
        val MAX_POSTER_BYTES = 5L * 1024L * 1024L
    }

    private val displayDateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    private val zoneId: ZoneId = ZoneId.of("Asia/Manila")

    private var startDateTimeValue: LocalDateTime? = null
    private var endDateTimeValue: LocalDateTime? = null
    private var registrationStartDateTimeValue: LocalDateTime? = null
    private var registrationEndDateTimeValue: LocalDateTime? = null

    private val posterPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { launchPosterCrop(it) }
    }

    // SCOPE ADDITION (beyond SRS/SDD Module 3 - event management): FB-style crop-before-upload
    // flow for the event poster. The picked image is opened in uCrop with a locked 16:9 frame so
    // every submitted poster is guaranteed to be landscape before it reaches the server.
    private val posterCropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when {
                result.resultCode == RESULT_OK -> {
                    val croppedUri = result.data?.let { UCrop.getOutput(it) }
                    if (croppedUri != null) {
                        handleCroppedPoster(croppedUri)
                    } else {
                        showMessage("Unable to process cropped poster. Please try again.")
                    }
                }
                result.resultCode == UCrop.RESULT_ERROR -> {
                    result.data?.let { UCrop.getError(it) }
                    showMessage("Unable to crop selected poster. Please choose another image.")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_event)

        repository = AttendeeRepository(this)
        sessionManager = SessionManager(this)
        bindViews()
        prefillRequester()

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.nav_header_title).setOnClickListener { finish() }
        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }
        findViewById<View>(R.id.eventPosterPicker).setOnClickListener { posterPicker.launch("image/*") }
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
        txtSelectedEventCategory = findViewById(R.id.txtSelectedEventCategory)
        cardEventCategory = findViewById(R.id.cardEventCategory)
        spinnerArrow = findViewById(R.id.spinnerArrow)
        spinnerEventCategory = findViewById(R.id.spinnerEventCategory)
        val categoryAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerEventCategory.adapter = categoryAdapter
        cardEventCategory.setOnClickListener { setCategoryDropdownOpen(!isCategoryDropdownOpen) }
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
        eventPosterPreview = findViewById(R.id.eventPosterPreview)
        eventPosterPlaceholder = findViewById(R.id.eventPosterPlaceholder)
        eventPosterStatusText = findViewById(R.id.eventPosterStatusText)
        reasonForRequestInput = findViewById(R.id.reasonForRequestInput)
        formMessageText = findViewById(R.id.formMessageText)
        submitProgress = findViewById(R.id.submitProgress)
        submitButton = findViewById(R.id.submitRequestButton)
        formScrollView = findViewById(R.id.formScrollView)
    }

    private fun prefillRequester() {
        requesterNameInput.setText(sessionManager.getFullName().orEmpty())
        contactEmailInput.setText(sessionManager.getEmail().orEmpty())

        lifecycleScope.launch {
            when (val result = repository.getMyProfile()) {
                is NetworkResult.Success -> {
                    if (requesterNameInput.textString().isBlank()) requesterNameInput.setText(result.data.fullName)
                    if (contactEmailInput.textString().isBlank()) contactEmailInput.setText(result.data.email)
                    if (contactNumberInput.textString().isBlank()) contactNumberInput.setText(result.data.phoneNumber.orEmpty())
                }
                is NetworkResult.Error -> Unit
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun launchPosterCrop(sourceUri: Uri) {
        hideMessage()
        val destinationUri = Uri.fromFile(File(cacheDir, "event_poster_cropped_${System.currentTimeMillis()}.jpg"))
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(85)
            // Aspect lock is the point of this flow: no free-style cropping.
            setFreeStyleCropEnabled(false)
        }
        val cropIntent = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(16f, 9f)
            .withMaxResultSize(1920, 1080)
            .withOptions(options)
            .getIntent(this)
        posterCropLauncher.launch(cropIntent)
    }

    private fun handleCroppedPoster(croppedUri: Uri) {
        hideMessage()

        // Crop output is forced to JPEG by UCrop options; still guard the type explicitly.
        val croppedType = contentResolver.getType(croppedUri)?.lowercase()
        if (croppedType != null && croppedType !in ALLOWED_POSTER_MIME_TYPES) {
            showMessage("Only JPG and PNG posters are supported. Please choose another image.")
            return
        }

        val posterFile = File(cacheDir, "event_poster_${System.currentTimeMillis()}.jpg")
        runCatching {
            contentResolver.openInputStream(croppedUri)?.use { input ->
                posterFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open cropped image")
        }.onSuccess {
            if (posterFile.length() > MAX_POSTER_BYTES) {
                posterFile.delete()
                showMessage("Cropped poster must not exceed 5 MB. Please choose a smaller image.")
                return
            }
            selectedPosterFile?.delete()
            selectedPosterFile = posterFile
            eventPosterPreview.setImageURI(croppedUri)
            eventPosterPreview.visibility = View.VISIBLE
            eventPosterPlaceholder.visibility = View.GONE
            eventPosterStatusText.text = "Poster ready (16:9). Tap it to replace or re-crop."
            eventPosterStatusText.setTextColor(0xFF4F46E5.toInt())
        }.onFailure {
            showMessage("Unable to attach cropped poster. Please choose another image.")
        }
    }

    private fun submitRequest() {
        if (isSubmitting) return
        hideMessage()
        buildValidatedRequest(eventPosterFileId = null) ?: return
        setLoading(true)

        lifecycleScope.launch {
            val posterFileId = selectedPosterFile?.let { file ->
                when (val uploadResult = repository.uploadEventPoster(file)) {
                    is NetworkResult.Success -> uploadResult.data.fileId.toString()
                    is NetworkResult.Error -> {
                        setLoading(false)
                        showMessage(uploadResult.message.ifBlank { "Could not upload event poster. Please try another image." })
                        return@launch
                    }
                    NetworkResult.Loading -> null
                }
            }

            val request = buildValidatedRequest(eventPosterFileId = posterFileId) ?: run {
                setLoading(false)
                return@launch
            }

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

    // EventQR - scroll-to-error + specific toast, not explicit SRS/SDD spec (UX safeguard)
    // Collect-all validation: every failing field gets its .error set simultaneously,
    // checks run in visual/XML top-to-bottom order so the first failure is the topmost
    // one, the form scrolls to it, and the Toast lists all issues (capped at 4).
    private fun buildValidatedRequest(eventPosterFileId: String?): EventCreationRequestDto? {
        clearFieldErrors()
        val failures = mutableListOf<Pair<View, String>>()

        fun EditText.requiredValue(message: String): String? {
            val value = text.toString().trim()
            if (value.isEmpty()) {
                error = message
                failures += this to message
                return null
            }
            return value
        }

        fun EditText.positiveIntValue(message: String): Int? {
            val value = text.toString().trim().toIntOrNull()
            if (value == null || value <= 0) {
                error = message
                failures += this to message
                return null
            }
            return value
        }

        fun failIfNull(view: EditText, value: LocalDateTime?, message: String) {
            if (value == null) {
                view.error = message
                failures += view to message
            }
        }

        val eventName = eventNameInput.requiredValue("Event name is required")
        val eventCategory = selectedEventCategory.trim().takeIf { it.isNotEmpty() }
        if (eventCategory == null) {
            failures += txtSelectedEventCategory to "Event category is required"
        }
        val eventDescription = eventDescriptionInput.requiredValue("Event description is required")
        val targetAudience = targetAudienceInput.requiredValue("Target audience is required")
        val capacity = capacityInput.positiveIntValue("Capacity must be a positive number")
        val venue = venueInput.requiredValue("Venue/location is required")

        val startDateTime = startDateTimeValue
        failIfNull(startDateTimeInput, startDateTime, "Start date/time is required")
        val endDateTime = endDateTimeValue
        failIfNull(endDateTimeInput, endDateTime, "End date/time is required")

        if (startDateTime != null && startDateTime.isBefore(currentLocalDateTime())) {
            startDateTimeInput.error = "Start date/time cannot be in the past"
            failures += startDateTimeInput to "Start date/time cannot be in the past"
        }
        if (startDateTime != null && endDateTime != null && !endDateTime.isAfter(startDateTime)) {
            endDateTimeInput.error = "End date/time must be after the start"
            failures += endDateTimeInput to "End date/time must be after the start"
        }

        val registrationStart = registrationStartDateTimeValue
        failIfNull(registrationStartDateTimeInput, registrationStart, "Registration start date/time is required")
        val registrationEnd = registrationEndDateTimeValue
        failIfNull(registrationEndDateTimeInput, registrationEnd, "Registration end date/time is required")

        if (registrationStart != null && registrationStart.isBefore(currentLocalDateTime())) {
            registrationStartDateTimeInput.error = "Registration start cannot be in the past"
            failures += registrationStartDateTimeInput to "Registration start cannot be in the past"
        }
        if (registrationStart != null && registrationEnd != null && !registrationEnd.isAfter(registrationStart)) {
            registrationEndDateTimeInput.error = "Registration end must be after registration start"
            failures += registrationEndDateTimeInput to "Registration end must be after registration start"
        }
        if (registrationEnd != null && registrationEnd.isBefore(currentLocalDateTime())) {
            registrationEndDateTimeInput.error = "Registration end cannot be in the past"
            failures += registrationEndDateTimeInput to "Registration end cannot be in the past"
        }
        if (registrationEnd != null && endDateTime != null && registrationEnd.isAfter(endDateTime)) {
            registrationEndDateTimeInput.error = "Registration end must not be after event end"
            failures += registrationEndDateTimeInput to "Registration end must not be after event end"
        }
        if (registrationEnd != null && startDateTime != null && !registrationEnd.isBefore(startDateTime)) {
            registrationEndDateTimeInput.error = "Registration end must be before event start"
            failures += registrationEndDateTimeInput to "Registration end must be before event start"
        }

        val requesterName = requesterNameInput.requiredValue("Requester name is required")

        val contactEmail = contactEmailInput.requiredValue("Contact email is required")
        if (contactEmail != null && !Validators.isValidEmail(contactEmail)) {
            contactEmailInput.error = "Enter a valid email address"
            failures += contactEmailInput to "Enter a valid email address"
        }
        val contactNumber = contactNumberInput.requiredValue("Contact number is required")
        val reason = reasonForRequestInput.requiredValue("Reason for request is required")

        if (failures.isNotEmpty()) {
            reportValidationFailures(failures)
            return null
        }

        return EventCreationRequestDto(
            eventName = eventName ?: "",
            eventDescription = eventDescription ?: "",
            eventCategory = eventCategory ?: "",
            targetAudience = targetAudience ?: "",
            capacity = capacity ?: 0,
            venue = venue ?: "",
            startDateTime = requireNotNull(startDateTime).atZone(zoneId).toInstant().toString(),
            endDateTime = requireNotNull(endDateTime).atZone(zoneId).toInstant().toString(),
            registrationStartDateTime = requireNotNull(registrationStart).atZone(zoneId).toInstant().toString(),
            registrationEndDateTime = requireNotNull(registrationEnd).atZone(zoneId).toInstant().toString(),
            requesterName = requesterName ?: "",
            contactEmail = contactEmail ?: "",
            contactNumber = contactNumber ?: "",
            requestedFeatures = null,
            eventLogoUrl = eventPosterFileId,
            additionalNotes = null,
            reasonForRequest = reason ?: "",
        )
    }

    private fun reportValidationFailures(failures: List<Pair<View, String>>) {
        val firstFailingView = failures.first().first
        formScrollView.smoothScrollTo(0, firstFailingView.top.coerceAtLeast(0))
        firstFailingView.requestFocus()

        val maxShownMessages = 4
        val shown = failures.take(maxShownMessages).joinToString("\n") { "• ${it.second}" }
        val hiddenCount = failures.size - maxShownMessages
        val message = if (hiddenCount > 0) {
            "$shown\nand $hiddenCount more issue${if (hiddenCount == 1) "" else "s"}"
        } else {
            shown
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        eventCategoryPopup?.dismiss()
        eventCategoryPopup = null
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
            eventNameInput, eventDescriptionInput, targetAudienceInput,
            capacityInput, venueInput, startDateTimeInput, endDateTimeInput,
            registrationStartDateTimeInput, registrationEndDateTimeInput,
            requesterNameInput, contactEmailInput, contactNumberInput,
            reasonForRequestInput,
        ).forEach { it.error = null }
    }

    private fun renderCategoryDropdown() {
        eventCategoryPopup?.dismiss()
        eventCategoryPopup = PopupWindow(
            buildCategoryDropdownView(),
            cardEventCategory.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { isCategoryDropdownOpen = false; normalizeChevron(spinnerArrow, false) }
        }
    }

    private fun buildCategoryDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        categoryOptions.forEachIndexed { index, option ->
            addView(LinearLayout(this@RequestEventActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundColor(if (index == spinnerEventCategory.selectedItemPosition) Color.parseColor("#EEF2FF") else Color.WHITE)
                setOnClickListener {
                    spinnerEventCategory.setSelection(index, false)
                    txtSelectedEventCategory.text = option
                    selectedEventCategory = option
                    renderCategoryDropdown()
                    setCategoryDropdownOpen(false)
                }
                addView(TextView(this@RequestEventActivity).apply {
                    text = option
                    setTextColor(if (index == spinnerEventCategory.selectedItemPosition) 0xFF4F46E5.toInt() else 0xFF111827.toInt())
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            })
        }
    }

    private fun setCategoryDropdownOpen(open: Boolean) {
        if (open && categoryOptions.isEmpty()) return
        if (open) {
            if (eventCategoryPopup == null || cardEventCategory.width > 0 && eventCategoryPopup?.width != cardEventCategory.width) {
                renderCategoryDropdown()
            }
            isCategoryDropdownOpen = true
            normalizeChevron(spinnerArrow, true)
            eventCategoryPopup?.showAsDropDown(cardEventCategory, 0, 0)
        } else {
            eventCategoryPopup?.dismiss()
            isCategoryDropdownOpen = false
            normalizeChevron(spinnerArrow, false)
        }
    }

    private fun normalizeChevron(view: View, open: Boolean) {
        if (view is ImageView) {
            view.rotation = if (open) 180f else 0f
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    private fun showDateTimePicker(initialValue: LocalDateTime, onSelected: (LocalDateTime) -> Unit) {
        val now = currentLocalDateTime()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                val initialTime = if (selectedDate == now.toLocalDate()) {
                    // EventQR - default time suggestion rounds to next valid minute (removes reject-then-repick friction)
                    now.plusMinutes(1).toLocalTime().withSecond(0).withNano(0)
                } else if (selectedDate == initialValue.toLocalDate()) {
                    initialValue.toLocalTime().withSecond(0).withNano(0)
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
            // EventQR - fixed to Philippine Time (Asia/Manila), not device-local, since system is PH-based
            // Get today's date fields as seen in Manila
            val manilaCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Manila"))
            val manilaYear = manilaCal.get(java.util.Calendar.YEAR)
            val manilaMonth = manilaCal.get(java.util.Calendar.MONTH)
            val manilaDay = manilaCal.get(java.util.Calendar.DAY_OF_MONTH)

            // Build minDate using DEVICE'S default timezone calendar, but with Manila's date fields —
            // this ensures DatePicker's internal (device-timezone) day-boundary comparison greys correctly
            val todayCal = java.util.Calendar.getInstance()
            todayCal.set(manilaYear, manilaMonth, manilaDay, 0, 0, 0)
            todayCal.set(java.util.Calendar.MILLISECOND, 0)

            datePicker.minDate = todayCal.timeInMillis
        }.show()
    }

    private fun formatForDisplay(value: LocalDateTime): String = value.format(displayDateTimeFormatter)
    private fun currentLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.now(), zoneId)
    private fun EditText.textString(): String = text?.toString()?.trim().orEmpty()
}
