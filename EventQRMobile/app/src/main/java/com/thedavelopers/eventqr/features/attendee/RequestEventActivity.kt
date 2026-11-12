package com.thedavelopers.eventqr.features.attendee

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.PhoneInput
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.events.model.dto.EventCreationRequestDto
import com.thedavelopers.eventqr.features.notifications.EventDecisionNotifier
import com.thedavelopers.eventqr.ui.components.showInlineError
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
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
    @VisibleForTesting
    internal lateinit var repository: AttendeeRepository
    private lateinit var sessionManager: SessionManager

    private lateinit var eventNameInput: EditText
    private lateinit var eventDescriptionInput: EditText
    // EventQR - Event Category constrained to predetermined list via hidden Spinner + custom dropdown (matches Rewards picker style), not in SRS/SDD Event Request Form spec (scope addition)
    private lateinit var txtSelectedEventCategory: TextView
    private lateinit var spinnerEventCategory: Spinner
    private lateinit var cardEventCategory: View
    private lateinit var spinnerArrow: ImageView
    @VisibleForTesting
    internal var selectedEventCategory: String = ""
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
    // §5.16 page-failure strip (include_error_strip): server and upload failures only, never field validation.
    private lateinit var errorStrip: View
    private val errorStripRetry: View get() = errorStrip.findViewById(R.id.btnErrorStripRetry)
    private lateinit var submitProgress: ProgressBar
    private lateinit var submitButton: Button
    private lateinit var formScrollView: ScrollView
    private var successDialog: AlertDialog? = null
    private var isSubmitting = false
    // Once the request is stored a recreation must not offer the form again; a second tap would
    // post the same request twice.
    private var submitted = false
    @VisibleForTesting
    internal var selectedPosterFile: File? = null

    // §5.2 / §6.8 row 6: each field with the helper line that carries its error, in visual
    // top-to-bottom order so the first invalid field is the first entry that has a message.
    private val fieldHelpers = LinkedHashMap<View, TextView>()

    private companion object {
        val ALLOWED_POSTER_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png")
        val MAX_POSTER_BYTES = 5L * 1024L * 1024L
        const val STATE_SUBMITTED = "request_event_submitted"
        const val STATE_IN_FLIGHT = "request_event_in_flight"
        const val STATE_FIELD_ERRORS = "request_event_field_errors"
        const val STATE_FOCUSED_FIELD = "request_event_focused_field"
        const val STATE_ERROR_STRIP = "request_event_error_strip"
        const val STATE_ERROR_STRIP_RETRY = "request_event_error_strip_retry"
        const val STATE_CATEGORY = "request_event_category"
        const val STATE_START = "request_event_start"
        const val STATE_END = "request_event_end"
        const val STATE_REGISTRATION_START = "request_event_registration_start"
        const val STATE_REGISTRATION_END = "request_event_registration_end"
        const val TAG = "RequestEvent"
    }

    private val displayDateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    private val zoneId: ZoneId = ZoneId.of("Asia/Manila")

    // Set by the date/time pickers; internal so tests can fill the form without driving picker dialogs.
    @VisibleForTesting internal var startDateTimeValue: LocalDateTime? = null
    @VisibleForTesting internal var endDateTimeValue: LocalDateTime? = null
    @VisibleForTesting internal var registrationStartDateTimeValue: LocalDateTime? = null
    @VisibleForTesting internal var registrationEndDateTimeValue: LocalDateTime? = null

    private val posterPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { launchPosterCrop(it) }
    }

    // The rationale's Allow is the only path to the system prompt. The exit runs on the prompt's
    // result, so no navigation or finish happens while the prompt is visible.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            val next = afterNotificationFlow
            afterNotificationFlow = null
            // A recreation during the prompt loses the continuation; the request is already
            // submitted, so close rather than leave a stale filled form.
            if (next != null) next.invoke() else finish()
        }

    // Set while the system prompt is up, so the rationale's dismissal does not fire the exit first.
    private var afterNotificationFlow: (() -> Unit)? = null

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
                        renderPosterStatus(getString(R.string.request_event_unable_to_process_cropped_poster_ple))
                    }
                }
                result.resultCode == UCrop.RESULT_ERROR -> {
                    result.data?.let { UCrop.getError(it) }
                    renderPosterStatus(getString(R.string.request_event_unable_to_crop_selected_poster_pleas))
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
        applyPosterVisibility()
        findViewById<View>(R.id.layoutSubmitBar).applyEventQrBottomInsetPadding()

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.nav_header_title).setOnClickListener { finish() }
        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }
        findViewById<View>(R.id.eventPosterPicker).setOnClickListener { posterPicker.launch("image/*") }
        submitButton.setOnClickListener { submitRequest() }
        errorStripRetry.setOnClickListener { submitRequest() }
        savedInstanceState?.let { restoreFormValues(it) }

        configureDateTimeField(startDateTimeInput, { startDateTimeValue }) { value ->
            startDateTimeValue = value
            startDateTimeInput.setText(formatForDisplay(value))
            setFieldError(startDateTimeInput, null)
            if (endDateTimeValue != null && !endDateTimeValue!!.isAfter(value)) {
                endDateTimeValue = null
                endDateTimeInput.text?.clear()
                setFieldError(endDateTimeInput, getString(R.string.request_event_end_date_time_must_be_after_the_star))
            }
            if (registrationEndDateTimeValue != null && !registrationEndDateTimeValue!!.isBefore(value)) {
                registrationEndDateTimeValue = null
                registrationEndDateTimeInput.text?.clear()
                setFieldError(registrationEndDateTimeInput, getString(R.string.request_event_registration_end_must_be_before_even))
            }
        }
        configureDateTimeField(endDateTimeInput, { endDateTimeValue }) { value ->
            endDateTimeValue = value
            endDateTimeInput.setText(formatForDisplay(value))
            setFieldError(endDateTimeInput, null)
            if (startDateTimeValue != null && !value.isAfter(startDateTimeValue)) {
                endDateTimeValue = null
                endDateTimeInput.text?.clear()
                setFieldError(endDateTimeInput, getString(R.string.request_event_end_date_time_must_be_after_the_star))
            }
        }
        configureDateTimeField(registrationStartDateTimeInput, { registrationStartDateTimeValue }) { value ->
            registrationStartDateTimeValue = value
            registrationStartDateTimeInput.setText(formatForDisplay(value))
            setFieldError(registrationStartDateTimeInput, null)
            if (registrationEndDateTimeValue != null && !registrationEndDateTimeValue!!.isAfter(value)) {
                registrationEndDateTimeValue = null
                registrationEndDateTimeInput.text?.clear()
                setFieldError(registrationEndDateTimeInput, getString(R.string.request_event_registration_end_must_be_after_regis))
            }
        }
        configureDateTimeField(registrationEndDateTimeInput, { registrationEndDateTimeValue }) { value ->
            registrationEndDateTimeValue = value
            registrationEndDateTimeInput.setText(formatForDisplay(value))
            setFieldError(registrationEndDateTimeInput, null)
            if (registrationStartDateTimeValue != null && !value.isAfter(registrationStartDateTimeValue)) {
                registrationEndDateTimeValue = null
                registrationEndDateTimeInput.text?.clear()
                setFieldError(registrationEndDateTimeInput, getString(R.string.request_event_registration_end_must_be_after_regis))
                return@configureDateTimeField
            }
            if (startDateTimeValue != null && !value.isBefore(startDateTimeValue)) {
                registrationEndDateTimeValue = null
                registrationEndDateTimeInput.text?.clear()
                setFieldError(registrationEndDateTimeInput, getString(R.string.request_event_registration_end_must_be_before_even))
            }
        }

        submitted = savedInstanceState?.getBoolean(STATE_SUBMITTED) == true
        if (submitted) {
            restoreSubmittedState()
        } else if (savedInstanceState?.getBoolean(STATE_IN_FLIGHT) == true) {
            // The upload coroutine does not survive recreation, so the request may or may not have
            // landed: keep the form non-submittable instead of risking a duplicate POST.
            setLoading(true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SUBMITTED, submitted)
        outState.putBoolean(STATE_IN_FLIGHT, isSubmitting)
        val errors = Bundle()
        fieldHelpers.forEach { (field, helper) ->
            if (helper.visibility == View.VISIBLE) errors.putString(field.id.toString(), helper.text.toString())
        }
        outState.putBundle(STATE_FIELD_ERRORS, errors)
        outState.putInt(STATE_FOCUSED_FIELD, window.decorView.findFocus()?.takeIf { fieldHelpers.containsKey(it) }?.id ?: View.NO_ID)
        outState.putString(
            STATE_ERROR_STRIP,
            errorStrip.takeIf { it.visibility == View.VISIBLE }
                ?.findViewById<TextView>(R.id.txtErrorStrip)?.text?.toString(),
        )
        outState.putBoolean(STATE_ERROR_STRIP_RETRY, errorStripRetry.visibility == View.VISIBLE)
        outState.putString(STATE_CATEGORY, selectedEventCategory)
        outState.putString(STATE_START, startDateTimeValue?.toString())
        outState.putString(STATE_END, endDateTimeValue?.toString())
        outState.putString(STATE_REGISTRATION_START, registrationStartDateTimeValue?.toString())
        outState.putString(STATE_REGISTRATION_END, registrationEndDateTimeValue?.toString())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restoring the typed text runs the edit watchers that clear an error, so the errors, the
        // strip and the focus go back after it, never before.
        savedInstanceState.getBundle(STATE_FIELD_ERRORS)?.let { errors ->
            fieldHelpers.keys.forEach { field ->
                errors.getString(field.id.toString())?.let { setFieldError(field, it) }
            }
        }
        savedInstanceState.getString(STATE_ERROR_STRIP)?.let {
            errorStripRetry.visibility = if (savedInstanceState.getBoolean(STATE_ERROR_STRIP_RETRY, true)) View.VISIBLE else View.GONE
            errorStrip.showInlineError(it, announce = false)
        }
        val focusedId = savedInstanceState.getInt(STATE_FOCUSED_FIELD, View.NO_ID)
        if (focusedId != View.NO_ID) findViewById<View>(focusedId)?.let { focusField(it) }
    }

    /**
     * The pickers and the category selector keep their value in fields, not in the view tree, so a
     * rotation would otherwise leave a date shown in the box and `null` behind it: submit would then
     * report a date the user can see as missing.
     */
    private fun restoreFormValues(state: Bundle) {
        fun date(key: String) = state.getString(key)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        startDateTimeValue = date(STATE_START)
        endDateTimeValue = date(STATE_END)
        registrationStartDateTimeValue = date(STATE_REGISTRATION_START)
        registrationEndDateTimeValue = date(STATE_REGISTRATION_END)
        startDateTimeValue?.let { startDateTimeInput.setText(formatForDisplay(it)) }
        endDateTimeValue?.let { endDateTimeInput.setText(formatForDisplay(it)) }
        registrationStartDateTimeValue?.let { registrationStartDateTimeInput.setText(formatForDisplay(it)) }
        registrationEndDateTimeValue?.let { registrationEndDateTimeInput.setText(formatForDisplay(it)) }
        val category = state.getString(STATE_CATEGORY).orEmpty()
        val index = categoryOptions.indexOf(category)
        if (index >= 0) {
            selectedEventCategory = category
            txtSelectedEventCategory.text = category
            spinnerEventCategory.setSelection(index, false)
        }
    }

    /**
     * A rotation after a successful submit re-renders the submitted state, never a filled editable
     * form: the request is already stored and a second tap on Submit would create it again.
     */
    private fun restoreSubmittedState() {
        setLoading(false)
        submitButton.isEnabled = false
        showSuccessDialog()
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
        errorStrip = findViewById(R.id.errorRequestEvent)
        submitProgress = findViewById(R.id.submitProgress)
        submitButton = findViewById(R.id.submitRequestButton)
        formScrollView = findViewById(R.id.formScrollView)
        bindFieldErrors()
    }

    /** Order here is the visual order of the form; focus lands on the first entry with an error. */
    private fun bindFieldErrors() {
        registerField(eventNameInput, R.id.txtEventNameError)
        registerField(cardEventCategory, R.id.txtEventCategoryError)
        registerField(eventDescriptionInput, R.id.txtEventDescriptionError)
        registerField(targetAudienceInput, R.id.txtTargetAudienceError)
        registerField(capacityInput, R.id.txtCapacityError)
        registerField(venueInput, R.id.txtVenueError)
        registerField(startDateTimeInput, R.id.txtStartDateTimeError)
        registerField(endDateTimeInput, R.id.txtEndDateTimeError)
        registerField(registrationStartDateTimeInput, R.id.txtRegistrationStartDateTimeError)
        registerField(registrationEndDateTimeInput, R.id.txtRegistrationEndDateTimeError)
        registerField(requesterNameInput, R.id.txtRequesterNameError)
        registerField(contactEmailInput, R.id.txtContactEmailError)
        registerField(contactNumberInput, R.id.txtContactNumberError)
        registerField(reasonForRequestInput, R.id.txtReasonForRequestError)
        // Typed fields drop their error on the first edit; the pickers and the category selector
        // drop theirs when a value is chosen.
        listOf(
            eventNameInput, eventDescriptionInput, targetAudienceInput, capacityInput, venueInput,
            requesterNameInput, contactEmailInput, contactNumberInput, reasonForRequestInput,
        ).forEach { field -> field.doAfterTextChanged { setFieldError(field, null) } }
    }

    private fun registerField(field: View, helperId: Int) {
        val helper = findViewById<TextView>(helperId)
        fieldHelpers[field] = helper
        // TalkBack reads the message with the field: the helper line is a separate node, and focus
        // lands on the field, so the field itself reports itself as invalid.
        field.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (helper.visibility == View.VISIBLE) {
                    info.isContentInvalid = true
                    info.error = helper.text
                }
            }
        }
    }

    // The poster upload endpoint is ORGANIZER/ADMIN/SUPER_ADMIN only: an attendee who picks a
    // poster gets a 403 that aborts the whole request, so the block is hidden below that floor.
    private fun applyPosterVisibility() {
        val allowed = RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.ORGANIZER)
        findViewById<View>(R.id.layoutPosterSection).visibility = if (allowed) View.VISIBLE else View.GONE
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
        renderPosterStatus()
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
        renderPosterStatus()

        // Crop output is forced to JPEG by UCrop options; still guard the type explicitly.
        val croppedType = contentResolver.getType(croppedUri)?.lowercase()
        if (croppedType != null && croppedType !in ALLOWED_POSTER_MIME_TYPES) {
            renderPosterStatus(getString(R.string.request_event_only_jpg_and_png_posters_are_support))
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
                renderPosterStatus(getString(R.string.request_event_cropped_poster_must_not_exceed_5_mb))
                return
            }
            selectedPosterFile?.delete()
            selectedPosterFile = posterFile
            eventPosterPreview.setImageURI(croppedUri)
            eventPosterPreview.visibility = View.VISIBLE
            eventPosterPlaceholder.visibility = View.GONE
            renderPosterStatus()
        }.onFailure {
            renderPosterStatus(getString(R.string.request_event_unable_to_attach_cropped_poster_plea))
        }
    }

    private fun submitRequest() {
        if (isSubmitting || submitted) return
        errorStrip.visibility = View.GONE
        buildValidatedRequest(eventPosterFileId = null) ?: return
        setLoading(true)

        lifecycleScope.launch {
            val posterFileId = selectedPosterFile?.let { file ->
                when (val uploadResult = repository.uploadEventPoster(file)) {
                    is NetworkResult.Success -> uploadResult.data.fileId.toString()
                    is NetworkResult.Error -> {
                        Log.w(TAG, "poster upload failed: ${uploadResult.message}")
                        setLoading(false)
                        // Nothing was created yet, so repeating the submit is safe.
                        showPageFailure(getString(R.string.ui_attendee_request_poster_upload_failed), retry = true)
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
                    submitted = true
                    setLoading(false)
                    showSuccessDialog()
                }
                is NetworkResult.Error -> {
                    Log.w(TAG, "event request failed: ${result.message}")
                    setLoading(false)
                    // No Retry: the create is not idempotent and the client cannot tell a failure before the
                    // request was stored from one after it (a timeout, a 502/504 behind a stored write, a lost
                    // response). A one-tap repeat could post the same request twice; the next Submit press is
                    // a deliberate act.
                    showPageFailure(getString(R.string.ui_attendee_request_submit_failed), retry = false)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    // Collect-all validation: every failing field gets its helper line and error stroke at once.
    // Checks run in visual top-to-bottom order, the first invalid field takes focus and scrolls
    // into view, and nothing is toasted (§6.8 row 6).
    private fun buildValidatedRequest(eventPosterFileId: String?): EventCreationRequestDto? {
        clearFieldErrors()

        fun EditText.requiredValue(@StringRes message: Int): String? {
            val value = text.toString().trim()
            if (value.isEmpty()) {
                setFieldError(this, getString(message))
                return null
            }
            return value
        }

        fun EditText.positiveIntValue(@StringRes message: Int): Int? {
            val value = text.toString().trim().toIntOrNull()
            if (value == null || value <= 0) {
                setFieldError(this, getString(message))
                return null
            }
            return value
        }

        fun failIfNull(view: EditText, value: LocalDateTime?, @StringRes message: Int) {
            if (value == null) setFieldError(view, getString(message))
        }

        val eventName = eventNameInput.requiredValue(R.string.ui_attendee_request_error_event_name)
        val eventCategory = selectedEventCategory.trim().takeIf { it.isNotEmpty() }
        if (eventCategory == null) {
            setFieldError(cardEventCategory, getString(R.string.ui_attendee_request_error_event_category))
        }
        val eventDescription = eventDescriptionInput.requiredValue(R.string.ui_attendee_request_error_event_description)
        val targetAudience = targetAudienceInput.requiredValue(R.string.ui_attendee_request_error_target_audience)
        val capacity = capacityInput.positiveIntValue(R.string.ui_attendee_request_error_capacity)
        val venue = venueInput.requiredValue(R.string.ui_attendee_request_error_venue)

        val startDateTime = startDateTimeValue
        failIfNull(startDateTimeInput, startDateTime, R.string.ui_attendee_request_error_start_required)
        val endDateTime = endDateTimeValue
        failIfNull(endDateTimeInput, endDateTime, R.string.ui_attendee_request_error_end_required)

        if (startDateTime != null && startDateTime.isBefore(currentLocalDateTime())) {
            setFieldError(startDateTimeInput, getString(R.string.request_event_start_date_time_cannot_be_in_the_pas))
        }
        if (startDateTime != null && endDateTime != null && !endDateTime.isAfter(startDateTime)) {
            setFieldError(endDateTimeInput, getString(R.string.request_event_end_date_time_must_be_after_the_star))
        }

        val registrationStart = registrationStartDateTimeValue
        failIfNull(registrationStartDateTimeInput, registrationStart, R.string.ui_attendee_request_error_registration_start_required)
        val registrationEnd = registrationEndDateTimeValue
        failIfNull(registrationEndDateTimeInput, registrationEnd, R.string.ui_attendee_request_error_registration_end_required)

        if (registrationStart != null && registrationStart.isBefore(currentLocalDateTime())) {
            setFieldError(registrationStartDateTimeInput, getString(R.string.request_event_registration_start_cannot_be_in_the))
        }
        if (registrationStart != null && registrationEnd != null && !registrationEnd.isAfter(registrationStart)) {
            setFieldError(registrationEndDateTimeInput, getString(R.string.request_event_registration_end_must_be_after_regis))
        }
        if (registrationEnd != null && registrationEnd.isBefore(currentLocalDateTime())) {
            setFieldError(registrationEndDateTimeInput, getString(R.string.request_event_registration_end_cannot_be_in_the_pa))
        }
        if (registrationEnd != null && endDateTime != null && registrationEnd.isAfter(endDateTime)) {
            setFieldError(registrationEndDateTimeInput, getString(R.string.request_event_registration_end_must_not_be_after_e))
        }
        if (registrationEnd != null && startDateTime != null && !registrationEnd.isBefore(startDateTime)) {
            setFieldError(registrationEndDateTimeInput, getString(R.string.request_event_registration_end_must_be_before_even))
        }

        val requesterName = requesterNameInput.requiredValue(R.string.ui_attendee_request_error_requester_name)

        val contactEmail = contactEmailInput.requiredValue(R.string.ui_attendee_request_error_contact_email)
        if (contactEmail != null && !Validators.isValidEmail(contactEmail)) {
            setFieldError(contactEmailInput, getString(R.string.create_admin_account_enter_a_valid_email_address))
        }
        // Same rule as registration/profile (Validators.isValidPhoneNumber); the hint shows "+63 912 345 6789",
        // so spaces/dashes are dropped first and the normalized E.164 value is what gets sent.
        val contactNumber = contactNumberInput.requiredValue(R.string.ui_attendee_request_error_contact_number)?.let { typed ->
            PhoneInput.toE164OrNull(typed) ?: run {
                val message = getString(R.string.request_event_invalid_contact_number)
                setFieldError(contactNumberInput, message)
                null
            }
        }
        val reason = reasonForRequestInput.requiredValue(R.string.ui_attendee_request_error_reason)

        val invalid = fieldHelpers.values.filter { it.visibility == View.VISIBLE }
        if (invalid.isNotEmpty()) {
            focusFirstInvalidField()
            announceInvalidFields(invalid)
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

    /**
     * One announcement per failed submit: the first message, plus how many more fields need attention.
     * Focus lands on the first field, so TalkBack reads that one; the count is what it would not say.
     */
    private fun announceInvalidFields(invalid: List<TextView>) {
        val message = invalid.first().text.toString()
        val others = invalid.size - 1
        val spoken = if (others == 0) message else
            "$message. " + resources.getQuantityString(R.plurals.ui_attendee_request_more_fields_need_attention, others, others)
        formScrollView.announceForAccessibility(spoken)
    }

    /** The page-failure strip (§5.16): [retry] false hides the control for a failure that is not safe to repeat. */
    private fun showPageFailure(message: String, retry: Boolean) {
        errorStripRetry.visibility = if (retry) View.VISIBLE else View.GONE
        errorStrip.showInlineError(message)
    }

    private fun focusFirstInvalidField() {
        val first = fieldHelpers.entries.firstOrNull { it.value.visibility == View.VISIBLE }?.key ?: return
        focusField(first)
        // Posted so the offset is read after the layout pass that shows the helper lines.
        formScrollView.post { formScrollView.smoothScrollTo(0, scrollTargetFor(first)) }
    }

    /**
     * The date pickers and the category selector take focus from the keyboard and from TalkBack,
     * not from a touch, so a plain request fails for them while the screen is in touch mode.
     */
    private fun focusField(view: View) {
        if (!view.requestFocus()) view.requestFocusFromTouch()
    }

    /**
     * Scroll offset that brings [field] (plus a little room for its label) to the top of the form.
     * View.top is relative to the field's own card, so it is mapped into the ScrollView's content first.
     */
    @VisibleForTesting
    internal fun scrollTargetFor(field: View): Int {
        val bounds = Rect()
        field.getDrawingRect(bounds)
        formScrollView.offsetDescendantRectToMyCoords(field, bounds)
        return (bounds.top - dimenOffset(R.dimen.space_xxl)).coerceAtLeast(0)
    }

    private fun setLoading(loading: Boolean) {
        isSubmitting = loading
        submitProgress.visibility = if (loading) View.VISIBLE else View.GONE
        submitButton.isEnabled = !loading
        submitButton.text = getString(if (loading) R.string.ui_attendee_submitting else R.string.ui_attendee_submit_request)
    }

    @VisibleForTesting
    internal fun showSuccessDialog() {
        if (isFinishing || isDestroyed) return
        successDialog?.dismiss()

        val dialogView = layoutInflater.inflate(R.layout.dialog_base, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(color(R.color.transparent)))

        // dialog_base (§5.12): title / body / one text action. The status row and the second
        // full-width button are gone: the request's status lives on My Event Requests, which is
        // where the dialog's action goes.
        dialogView.findViewById<TextView>(R.id.dialogTitle)
            .setText(R.string.dialog_event_request_submitted_event_request_submitted)
        dialogView.findViewById<TextView>(R.id.dialogBody)
            .setText(R.string.dialog_event_request_submitted_your_event_request_has_been_submitte)
        dialogView.findViewById<Button>(R.id.dialogNegative).visibility = View.GONE
        dialogView.findViewById<Button>(R.id.dialogPositive).apply {
            setText(R.string.common_done)
            // Each action dismisses this dialog first, then runs the permission flow, then leaves.
            // The success dialog is never on screen beside the rationale, and nothing navigates
            // under one.
            setOnClickListener {
                dialog.dismiss()
                askForDecisionNotifications {
                    startActivity(Intent(this@RequestEventActivity, MyEventRequestsActivity::class.java))
                    finish()
                }
            }
        }

        successDialog = dialog
        dialog.show()
    }

    /**
     * Runs the decision-permission flow and then [onResolved], which performs the screen's exit.
     * A dialog that is already showing is finished before this is called, and the exit waits for
     * every dialog in the flow to be dismissed.
     */
    private fun askForDecisionNotifications(onResolved: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            onResolved()
            return
        }
        val canAskAgain = !EventDecisionNotifier.hasAskedForPermission(this) ||
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if (canAskAgain) showDecisionRationaleDialog(onResolved) else showDecisionSettingsDialog(onResolved)
    }

    private fun showDecisionRationaleDialog(onResolved: () -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.ui_attendee_decisions_permission_title)
            .setMessage(R.string.ui_attendee_decisions_permission_body)
            .setPositiveButton(R.string.ui_attendee_decisions_permission_allow) { _, _ ->
                EventDecisionNotifier.markAskedForPermission(this)
                afterNotificationFlow = onResolved
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.ui_attendee_decisions_permission_not_now, null)
            .create()
        // Allow hands the exit to the system prompt's result; every other dismissal resumes it here.
        // A destroyed activity never resumes it, so an exit can never run from a dead window.
        dialog.setOnDismissListener {
            if (afterNotificationFlow == null && !isFinishing && !isDestroyed) onResolved()
        }
        dialog.show()
    }

    private fun showDecisionSettingsDialog(onResolved: () -> Unit) {
        var openSettings = false
        val dialog = AlertDialog.Builder(this)
            .setMessage(R.string.ui_attendee_decisions_permission_off_body)
            .setPositiveButton(R.string.ui_attendee_decisions_permission_open_settings) { _, _ ->
                openSettings = true
            }
            .setNegativeButton(R.string.ui_attendee_decisions_permission_not_now, null)
            .create()
        // Settings opens and the screen closes only after this dialog is gone; the list destination
        // is skipped because the user chose settings instead.
        dialog.setOnDismissListener {
            if (isFinishing || isDestroyed) return@setOnDismissListener
            if (openSettings) {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                )
                finish()
            } else {
                onResolved()
            }
        }
        dialog.show()
    }

    override fun onDestroy() {
        eventCategoryPopup?.dismiss()
        eventCategoryPopup = null
        successDialog?.dismiss()
        successDialog = null
        super.onDestroy()
    }

    /**
     * Poster picking has its own status line under the picker (`micro`): a failed crop or an
     * oversized image is a problem with that one control, not a page failure, so it never reaches
     * the strip and its Retry (which re-runs the submit). [error] null restores the neutral line.
     */
    private fun renderPosterStatus(error: String? = null) {
        val hasPoster = selectedPosterFile != null
        eventPosterStatusText.text = error ?: getString(
            if (hasPoster) R.string.request_event_poster_ready_16_9_tap_it_to_replace
            else R.string.request_event_no_poster_selected_the_default_gradi,
        )
        eventPosterStatusText.setTextColor(
            color(
                when {
                    error != null -> R.color.ui_error
                    hasPoster -> R.color.primary
                    else -> R.color.ui_text_secondary
                },
            ),
        )
        if (error != null) eventPosterStatusText.announceForAccessibility(error)
    }

    /**
     * Inline error under [field] (§5.2): the helper turns into a `micro` `ui_error` line and the
     * box takes the 2dp error stroke through `isActivated`, which `bg_ui_field` already styles.
     * A null [message] clears both. Never uses `EditText.error` (the balloon is banned, §6.8 row 6).
     */
    private fun setFieldError(field: View, message: String?) {
        val helper = fieldHelpers[field] ?: return
        if (message == null) {
            if (helper.visibility != View.VISIBLE) return
            helper.text = null
            helper.visibility = View.GONE
            field.isActivated = false
        } else {
            helper.text = message
            helper.visibility = View.VISIBLE
            field.isActivated = true
        }
    }

    private fun clearFieldErrors() {
        fieldHelpers.keys.forEach { setFieldError(it, null) }
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
            elevation = dimenOffset(R.dimen.elevation_overlay).toFloat()
            setBackgroundDrawable(ColorDrawable(color(R.color.transparent)))
            setOnDismissListener { isCategoryDropdownOpen = false; normalizeChevron(spinnerArrow, false) }
        }
    }

    private fun buildCategoryDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_ui_card)
        categoryOptions.forEachIndexed { index, option ->
            addView(LinearLayout(this@RequestEventActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dimenOffset(R.dimen.space_lg), dimenOffset(R.dimen.space_14),
                    dimenOffset(R.dimen.space_lg), dimenOffset(R.dimen.space_14),
                )
                setBackgroundColor(if (index == spinnerEventCategory.selectedItemPosition) color(R.color.ui_primary_wash) else color(R.color.ui_surface))
                setOnClickListener {
                    spinnerEventCategory.setSelection(index, false)
                    txtSelectedEventCategory.text = option
                    selectedEventCategory = option
                    setFieldError(cardEventCategory, null)
                    renderCategoryDropdown()
                    setCategoryDropdownOpen(false)
                }
                addView(TextView(this@RequestEventActivity).apply {
                    text = option
                    setTextColor(color(R.color.ui_text_primary))
                    setTextSizeRes(R.dimen.type_body)
                    strong()
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


    private fun configureDateTimeField(
        field: EditText,
        getCurrentValue: () -> LocalDateTime?,
        onSelected: (LocalDateTime) -> Unit,
    ) {
        field.isCursorVisible = false
        field.isLongClickable = false
        field.setTextIsSelectable(false)
        // Keyboard and TalkBack focusable so a failed submit can focus the field, but not focusable
        // in touch mode: a first tap must open the picker, not just take focus. setTextIsSelectable
        // resets both flags, so they are set after it.
        field.isFocusable = true
        field.isFocusableInTouchMode = false
        field.setOnClickListener {
            showDateTimePicker(
                field = field,
                initialValue = getCurrentValue() ?: currentLocalDateTime(),
                onSelected = onSelected,
            )
        }
    }

    private fun showDateTimePicker(field: EditText, initialValue: LocalDateTime, onSelected: (LocalDateTime) -> Unit) {
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
                            val message = getString(R.string.request_event_selected_date_time_cannot_be_in_the)
                            // The rejected pick must not leave the earlier value in the box under an error:
                            // value, text and helper line all say "no value", like the other rejections.
                            clearDateTimeValue(field)
                            field.text?.clear()
                            setFieldError(field, message)
                            field.announceForAccessibility(message)
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

    private fun clearDateTimeValue(field: EditText) {
        when (field) {
            startDateTimeInput -> startDateTimeValue = null
            endDateTimeInput -> endDateTimeValue = null
            registrationStartDateTimeInput -> registrationStartDateTimeValue = null
            registrationEndDateTimeInput -> registrationEndDateTimeValue = null
        }
    }

    private fun formatForDisplay(value: LocalDateTime): String = value.format(displayDateTimeFormatter)
    private fun currentLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.now(), zoneId)
    private fun EditText.textString(): String = text?.toString()?.trim().orEmpty()
}
