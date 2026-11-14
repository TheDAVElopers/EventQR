package com.thedavelopers.eventqr.features.organizer.events

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.format.formatEventTime
import com.thedavelopers.eventqr.core.format.formatRegistrationWindow
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.bindEventBadge
import com.thedavelopers.eventqr.features.organizer.formatCount
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import com.thedavelopers.eventqr.ui.components.MetricRowStacking
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import com.thedavelopers.eventqr.ui.theme.applyEventQrKeyboardAwareBottomInsetPadding
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Organizer event editor (§6.4 v1.7) - the Draft / Pending review / Approved screen.
 *
 * Five eyebrowed sections in the view page's block order (Event, Schedule, Registration, About,
 * Banner), the shared identity-only head, a read-only STATUS row that opens the form (the header is
 * the title alone), and one bottom-anchored save bar.
 * Everything here binds a real layout: no view is built at runtime, and no field name reaches the
 * user.
 *
 * Behaviour:
 *  * dirty = the four fields differ from the value the server sent, or a new banner was picked
 *  * `Save changes` is disabled while clean; the first edit adds the `Unsaved changes` line above
 *    the button, so the button never moves (§6.6)
 *  * validation is inline and committed once: errors clear on the next keystroke, the first invalid
 *    field scrolls into view and takes focus, and a server rejection keeps every entered value
 *  * back on clean work finishes; back on dirty work asks first
 *  * a failed save reports in the bar, never in a toast
 *  * the Active / Completed fallback disables the fields and hides the save bar behind one line
 */
class EditEventDetailsActivity : AppCompatActivity() {

    private var repositoryFactory: (Context) -> OrganizerRepository = { OrganizerRepository(it) }

    @VisibleForTesting
    internal lateinit var repository: OrganizerRepository

    @VisibleForTesting
    internal fun installRepositoryFactory(factory: (Context) -> OrganizerRepository) {
        repositoryFactory = factory
    }
    private lateinit var eventId: String

    private lateinit var scroll: ScrollView
    private lateinit var head: View
    private lateinit var statusValue: TextView
    private lateinit var titleInput: EditText
    private lateinit var venueInput: EditText
    private lateinit var capacityInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var titleError: TextView
    private lateinit var capacityNote: TextView
    private lateinit var eventDatesValue: TextView
    private lateinit var windowValue: TextView
    private lateinit var bannerPreview: ImageView
    private lateinit var bannerNote: TextView
    private lateinit var bannerPickButton: Button
    private lateinit var saveBar: View
    private lateinit var saveButton: Button
    private lateinit var saveProgress: View
    private lateinit var saveError: TextView
    private lateinit var unsavedLine: TextView
    private lateinit var lockedNote: TextView

    private var loadedEvent: OrganizerEventDto? = null
    private var selectedBannerFile: File? = null

    /** Echoed back on save so the PATCH never clears the category; see [loadCategory]. */
    private var eventCategory: String? = null
    private var categoryLoaded = false

    /** Values as the server sent them; dirty is a plain comparison against this. */
    private var original = Draft("", "", "", "")
    private var bannerDirty = false
    private var capacityErrorShown = false
    private var saving = false
    private var locked = false

    /** Suppresses the dirty recalc while the loaded values are written into the fields. */
    private var populating = false

    /** The form as the process left it; consumed by the next [populate], over the loaded baseline. */
    private var restoredDraft: Draft? = null
    private var restoredBannerDirty = false
    private var restoredBannerPath: String? = null

    private val bannerPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { launchBannerCrop(it) }
    }

    private val bannerCropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when {
                result.resultCode == RESULT_OK -> {
                    val croppedUri = result.data?.let { UCrop.getOutput(it) }
                    if (croppedUri != null) {
                        handleCroppedBanner(croppedUri)
                    } else {
                        showBannerNote(R.string.ui_organizer_edit_banner_failed, error = true)
                    }
                }
                result.resultCode == UCrop.RESULT_ERROR -> {
                    result.data?.let { UCrop.getError(it) }
                    showBannerNote(R.string.ui_organizer_edit_banner_failed, error = true)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_event_edit)
        applyEventQrSystemBarAppearance(lightStatusBars = true)
        repository = repositoryFactory(this)
        bindViews()
        saveBar.applyEventQrKeyboardAwareBottomInsetPadding()
        restoreDraft(savedInstanceState)

        eventId = intentEventId()
            ?: return showMissingEventScreen(getString(R.string.ui_organizer_edit_title))

        findViewById<TextView>(R.id.topBarTitle).setText(R.string.ui_organizer_edit_title)
        findViewById<View>(R.id.topBarBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        bannerPickButton.setOnClickListener { bannerPicker.launch("image/*") }
        saveButton.setOnClickListener { saveChanges() }
        wireInputs()
        onBackPressedDispatcher.addCallback(this, backCallback)
        loadEvent()
    }

    private fun bindViews() {
        scroll = findViewById(R.id.eeScroll)
        head = findViewById(R.id.oeHead)
        statusValue = findViewById(R.id.eeStatusValue)
        titleInput = findViewById(R.id.eeTitleInput)
        venueInput = findViewById(R.id.eeVenueInput)
        capacityInput = findViewById(R.id.eeCapacityInput)
        descriptionInput = findViewById(R.id.eeDescriptionInput)
        titleError = findViewById(R.id.eeTitleError)
        capacityNote = findViewById(R.id.eeCapacityNote)
        eventDatesValue = findViewById(R.id.eeEventDatesValue)
        windowValue = findViewById(R.id.eeWindowValue)
        bannerPreview = findViewById(R.id.eeBannerPreview)
        bannerNote = findViewById(R.id.eeBannerNote)
        bannerPickButton = findViewById(R.id.eeBannerPick)
        saveBar = findViewById(R.id.eeSaveBar)
        saveButton = findViewById(R.id.eeSaveButton)
        saveProgress = findViewById(R.id.eeSaveProgress)
        saveError = findViewById(R.id.eeSaveError)
        unsavedLine = findViewById(R.id.eeUnsaved)
        lockedNote = findViewById(R.id.eeLockedNote)
    }

    /** Focus alone is not enough on a resized window: every field brings itself (and its label) in. */
    private fun wireInputs() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onDraftChanged()
        }
        listOf(titleInput, venueInput, capacityInput, descriptionInput).forEach { input ->
            input.addTextChangedListener(watcher)
            input.setOnFocusChangeListener { view, hasFocus -> if (hasFocus) ensureVisible(view) }
        }
    }

    private fun ensureVisible(field: View) {
        field.post {
            val slack = resources.getDimensionPixelSize(R.dimen.space_24)
            scroll.requestChildRectangleOnScreen(
                field,
                Rect(0, -slack, field.width, field.height + slack),
                true,
            )
        }
    }

    // ---------------------------------------------------------------- data

    private fun loadEvent() {
        lifecycleScope.launch {
            when (val result = repository.fetchOrganizerEvent(eventId)) {
                is NetworkResult.Success -> result.data?.let {
                    populate(it)
                    loadCategory()
                }
                is NetworkResult.Error -> showMissingEventScreen(
                    getString(R.string.ui_organizer_edit_title),
                    result.message ?: getString(R.string.event_management_hub_details_load_failed),
                )
                NetworkResult.Loading -> Unit
            }
        }
    }

    /**
     * `OrganizerEventResponse` carries no category and the PATCH replaces it, so the current value is
     * read from the event detail and echoed back. A failed read blocks the save instead of writing null.
     */
    private suspend fun loadCategory() {
        when (val result = repository.fetchEventDetail(eventId)) {
            is NetworkResult.Success -> {
                eventCategory = result.data?.category
                categoryLoaded = true
            }
            is NetworkResult.Error -> Unit
            NetworkResult.Loading -> Unit
        }
    }

    @VisibleForTesting
    internal fun populate(event: OrganizerEventDto) {
        loadedEvent = event
        val status = EventStatusBadgeStyler.resolve(
            EventStatusBadgeStyler.fromLabel(event.status),
            event.eventStartAt,
            event.eventEndAt,
        )
        locked = !status.isEventEditable()
        OrganizerEventHeadBinder.bind(
            root = head,
            title = event.title?.takeIf { it.isNotBlank() }
                ?: getString(R.string.organizer_untitled_event),
            status = status,
            // The badge is the STATUS row here, not a header ornament - same head as the details
            // page, so the band is the title alone and the status is never printed twice.
            showStatus = false,
        )
        // Same status -> label/tone mapping as the head binder (`bindEventBadge`), one code path.
        // The badge starts `invisible` so an empty pill never flashes before this bind.
        statusValue.bindEventBadge(status)
        statusValue.visibility = View.VISIBLE

        populating = true
        titleInput.setText(event.title.orEmpty())
        venueInput.setText(event.venue.orEmpty())
        descriptionInput.setText(event.description.orEmpty())
        // A machine-facing numeric field: no grouping, ASCII digits, so Locale.ROOT is correct.
        capacityInput.setText(String.format(Locale.ROOT, "%d", event.capacity.coerceAtLeast(0)))
        populating = false

        // The details pages break a multi-day range after the start date; these two read-only rows are
        // side-by-side form fields, so the range flows on one line and wraps at its own width.
        eventDatesValue.text = eventDatesText(event).inlineRange()
        windowValue.text = registrationWindow(event).inlineRange()
        // §6.4/§9.3: above fontScale 1.3 STATUS, EVENT DATES and REGISTRATION WINDOW stack through
        // MetricRowStacking - the same path as the details page's spec table, one implementation.
        organizerEventEditStackedRows.forEach { (rowId, valueId) ->
            MetricRowStacking.apply(
                this,
                findViewById<LinearLayout>(rowId),
                findViewById<TextView>(valueId),
            )
        }
        original = draft()
        bannerDirty = false
        selectedBannerFile = null
        // A draft the process saved comes back over the freshly loaded values, which stay the
        // baseline: a restored draft that differs from them is dirty, so back still asks.
        val restored = restoredDraft
        restoredDraft = null
        if (restored != null) {
            populating = true
            titleInput.setText(restored.title)
            venueInput.setText(restored.venue)
            descriptionInput.setText(restored.description)
            capacityInput.setText(restored.capacity)
            populating = false
            // The head and the inline errors follow a typed title; a restore bypasses the watcher.
            applyHeadTitle()
            if (titleError.isVisible) setTitleError(titleErrorFor())
            if (capacityErrorShown) setCapacityError(capacityErrorFor())
        }
        val restoredBanner = restoredBannerPath
            ?.let(::File)
            ?.takeIf { restoredBannerDirty && it.exists() }
        restoredBannerPath = null
        restoredBannerDirty = false
        if (restoredBanner != null) {
            selectedBannerFile = restoredBanner
            bannerDirty = true
            bannerPreview.setImageURI(Uri.fromFile(restoredBanner))
            showBannerNote(R.string.ui_organizer_edit_banner_selected)
        } else {
            loadBannerPreview(event)
        }
        if (locked) applyLock(status) else renderDirty()
    }

    /** The one locked state that survives: no fields, no bar, one line that says why. */
    private fun applyLock(status: EventStatus) {
        val reason = getString(
            if (status == EventStatus.ACTIVE) {
                R.string.edit_event_details_ongoing
            } else {
                R.string.edit_event_details_completed
            },
        )
        lockedNote.text = getString(R.string.edit_event_details_editing_locked, reason)
        lockedNote.visibility = View.VISIBLE
        listOf(titleInput, venueInput, capacityInput, descriptionInput).forEach { it.isEnabled = false }
        bannerPickButton.isEnabled = false
        saveBar.visibility = View.GONE
        // The bar carried the navigation-bar inset; with it gone the scroll takes it over, so the
        // last section never sits under the gesture bar.
        scroll.applyEventQrBottomInsetPadding()
    }

    // The details page's shared formatter, so the editor and the read-only page cannot print the
    // same range two ways. The field is a one-line form value, so the range's deterministic break
    // is flattened here.
    private fun eventDatesText(event: OrganizerEventDto): String =
        formatEventTime(this, event.eventStartAt, event.eventEndAt)?.inlineRange()
            ?: getString(R.string.ui_organizer_value_unavailable)

    // One window, one string: the details page's shared formatter, so the editor and the read-only
    // page cannot print the same window two ways. The field labels and input formats are unchanged.
    private fun registrationWindow(event: OrganizerEventDto): String =
        formatRegistrationWindow(this, event.registrationOpenAt, event.registrationCloseAt)
            ?: getString(R.string.ui_organizer_value_unavailable)

    /** `event_detail_date_range` breaks after the start date; a one-line form field does not want it. */
    private fun String.inlineRange(): String = replace('\n', ' ')

    // ---------------------------------------------------------------- dirty state

    private data class Draft(
        val title: String,
        val venue: String,
        val capacity: String,
        val description: String,
    )

    private fun draft(): Draft = Draft(
        title = titleInput.text.toString().trim(),
        venue = venueInput.text.toString().trim(),
        capacity = capacityInput.text.toString().trim(),
        description = descriptionInput.text.toString().trim(),
    )

    private fun isDirty(): Boolean = bannerDirty || draft() != original

    private fun renderDirty() {
        if (locked) {
            saveButton.isEnabled = false
            unsavedLine.visibility = View.GONE
            return
        }
        val dirty = isDirty()
        saveButton.isEnabled = dirty && !saving && loadedEvent != null
        unsavedLine.isVisible = dirty
    }

    private fun onDraftChanged() {
        if (populating) return
        applyHeadTitle()
        if (titleError.isVisible) setTitleError(titleErrorFor())
        if (capacityErrorShown) setCapacityError(capacityErrorFor())
        renderDirty()
    }

    /**
     * The head is bound from data, so a typed title has to follow it. Delegates to the binder with
     * `showStatus = false` - the band carries the title alone here (the status is the STATUS row), so
     * the a11y composition stays in one place instead of being re-implemented.
     */
    private fun applyHeadTitle() {
        OrganizerEventHeadBinder.bind(
            root = head,
            title = titleInput.text.toString().trim(),
            status = null,
            showStatus = false,
        )
    }

    // ---------------------------------------------------------------- validation

    private fun titleErrorFor(): String? =
        if (titleInput.text.toString().isBlank()) {
            getString(R.string.ui_organizer_edit_title_required)
        } else {
            null
        }

    private fun capacityErrorFor(): String? {
        val registered = loadedEvent?.currentAttendeeCount?.coerceAtLeast(0) ?: 0
        val value = capacityInput.text.toString().trim().toIntOrNull()
        return when {
            value == null || value <= 0 -> getString(R.string.ui_organizer_edit_capacity_required)
            // Data-backed rule: a capacity under the registrations already taken is refused inline
            // instead of round-tripping to the server.
            value < registered ->
                getString(R.string.ui_organizer_edit_capacity_minimum, formatCount(registered))
            else -> null
        }
    }

    private fun setTitleError(message: String?) {
        titleError.text = message.orEmpty()
        titleError.visibility = if (message == null) View.GONE else View.VISIBLE
        titleInput.setBackgroundResource(
            if (message == null) R.drawable.bg_ui_field else R.drawable.bg_ui_field_error,
        )
    }

    /** One micro slot: the helper at rest, the error (in `ui_error`) once the field is invalid. */
    private fun setCapacityError(message: String?) {
        capacityErrorShown = message != null
        if (message == null) {
            capacityNote.setText(R.string.ui_organizer_edit_capacity_helper)
            capacityNote.setTextColor(ContextCompat.getColor(this, R.color.ui_text_secondary))
            capacityInput.setBackgroundResource(R.drawable.bg_ui_field)
        } else {
            capacityNote.text = message
            capacityNote.setTextColor(ContextCompat.getColor(this, R.color.ui_error))
            capacityInput.setBackgroundResource(R.drawable.bg_ui_field_error)
        }
    }

    private fun validate(): Boolean {
        val title = titleErrorFor()
        val capacity = capacityErrorFor()
        setTitleError(title)
        setCapacityError(capacity)
        val firstInvalid = when {
            title != null -> titleInput
            capacity != null -> capacityInput
            else -> null
        }
        firstInvalid?.let {
            it.requestFocus()
            ensureVisible(it)
        }
        return firstInvalid == null
    }

    // ---------------------------------------------------------------- saved state

    /**
     * The typed values and the picked banner ride the saved state only - never our own storage and
     * never the server. A rotation or a killed process comes back to the same form; the loaded
     * values are re-read and stay the baseline, so a restored draft still reads dirty.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (loadedEvent == null) return
        val form = draft()
        outState.putString(KEY_DRAFT_TITLE, form.title)
        outState.putString(KEY_DRAFT_VENUE, form.venue)
        outState.putString(KEY_DRAFT_CAPACITY, form.capacity)
        outState.putString(KEY_DRAFT_DESCRIPTION, form.description)
        outState.putBoolean(KEY_DRAFT_BANNER_DIRTY, bannerDirty)
        selectedBannerFile?.let { outState.putString(KEY_DRAFT_BANNER_PATH, it.absolutePath) }
    }

    private fun restoreDraft(state: Bundle?) {
        if (state == null || !state.containsKey(KEY_DRAFT_TITLE)) return
        restoredDraft = Draft(
            title = state.getString(KEY_DRAFT_TITLE).orEmpty(),
            venue = state.getString(KEY_DRAFT_VENUE).orEmpty(),
            capacity = state.getString(KEY_DRAFT_CAPACITY).orEmpty(),
            description = state.getString(KEY_DRAFT_DESCRIPTION).orEmpty(),
        )
        restoredBannerDirty = state.getBoolean(KEY_DRAFT_BANNER_DIRTY, false)
        restoredBannerPath = state.getString(KEY_DRAFT_BANNER_PATH)
    }

    // ---------------------------------------------------------------- banner

    private fun showBannerNote(stringRes: Int, error: Boolean = false) {
        bannerNote.setText(stringRes)
        bannerNote.setTextColor(
            ContextCompat.getColor(this, if (error) R.color.ui_error else R.color.ui_text_secondary),
        )
    }

    private fun launchBannerCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "event_banner_cropped_${System.currentTimeMillis()}.jpg"))
        val options = UCrop.Options().apply {
            setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
            setCompressionQuality(85)
            setFreeStyleCropEnabled(false)
        }
        val cropIntent = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(16f, 9f)
            .withMaxResultSize(1920, 1080)
            .withOptions(options)
            .getIntent(this)
        bannerCropLauncher.launch(cropIntent)
    }

    private fun handleCroppedBanner(croppedUri: Uri) {
        val croppedFile = File(cacheDir, "event_banner_${System.currentTimeMillis()}.jpg")
        runCatching {
            contentResolver.openInputStream(croppedUri)?.use { input ->
                croppedFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open cropped image")
        }.onSuccess {
            if (croppedFile.length() > MAX_BANNER_BYTES) {
                croppedFile.delete()
                showBannerNote(R.string.ui_organizer_edit_banner_too_large, error = true)
                return
            }
            selectedBannerFile?.delete()
            selectedBannerFile = croppedFile
            bannerDirty = true
            bannerPreview.setImageURI(croppedUri)
            showBannerNote(R.string.ui_organizer_edit_banner_selected)
            renderDirty()
        }.onFailure {
            showBannerNote(R.string.ui_organizer_edit_banner_failed, error = true)
        }
    }

    private fun loadBannerPreview(current: OrganizerEventDto) {
        val fileId = current.eventLogoUrl?.trim().orEmpty()
        if (fileId.isBlank()) {
            showBannerNote(R.string.ui_organizer_edit_banner_note)
            return
        }
        lifecycleScope.launch {
            when (val result = repository.getStoredFile(fileId)) {
                is NetworkResult.Success -> {
                    val encoded = result.data?.contentBase64
                    if (encoded.isNullOrBlank()) {
                        showBannerNote(R.string.ui_organizer_edit_banner_note)
                        return@launch
                    }
                    runCatching {
                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.onSuccess { bitmap ->
                        if (bitmap != null) {
                            bannerPreview.setImageBitmap(bitmap)
                            showBannerNote(R.string.ui_organizer_edit_banner_current)
                        } else {
                            // A stored banner that will not decode leaves the empty plate, and the
                            // note keeps stating the constraint instead of claiming an image.
                            showBannerNote(R.string.ui_organizer_edit_banner_note)
                        }
                    }.onFailure {
                        showBannerNote(R.string.ui_organizer_edit_banner_note)
                    }
                }
                is NetworkResult.Error -> showBannerNote(R.string.ui_organizer_edit_banner_note)
                NetworkResult.Loading -> Unit
            }
        }
    }

    // ---------------------------------------------------------------- save

    private fun setSaving(value: Boolean) {
        saving = value
        saveProgress.visibility = if (value) View.VISIBLE else View.GONE
        saveButton.text = if (value) "" else getString(R.string.ui_organizer_edit_save)
        saveButton.contentDescription =
            if (value) getString(R.string.ui_organizer_edit_saving) else null
        renderDirty()
    }

    private fun showSaveFailure(message: String?) {
        saveError.text = message?.takeIf { it.isNotBlank() }?.let {
            getString(R.string.ui_organizer_edit_save_failed, it)
        } ?: getString(R.string.ui_organizer_edit_save_failed_plain)
        saveError.visibility = View.VISIBLE
    }

    private fun saveChanges() {
        if (saving || locked) return
        if (!validate()) return
        val current = loadedEvent ?: return

        saveError.visibility = View.GONE
        setSaving(true)
        lifecycleScope.launch {
            val uploadResult = selectedBannerFile?.let { file ->
                when (
                    val result = withContext(NonCancellable) {
                        repository.uploadEventBanner(file)
                    }.also { ensureActive() }
                ) {
                    is NetworkResult.Success -> result.data?.fileId?.toString()
                    is NetworkResult.Error -> {
                        setSaving(false)
                        showSaveFailure(result.message)
                        return@launch
                    }
                    NetworkResult.Loading -> null
                }
            }
            val bannerFileId = uploadResult ?: current.eventLogoUrl

            if (!categoryLoaded) loadCategory()
            if (!categoryLoaded) {
                // Unknown category would go back as null and silently clear it.
                setSaving(false)
                showSaveFailure(null)
                return@launch
            }

            val request = buildRequest(current, bannerFileId)
            when (
                val result = withContext(NonCancellable) {
                    repository.updateOrganizerEvent(eventId, request)
                }.also { ensureActive() }
            ) {
                is NetworkResult.Success -> {
                    original = draft()
                    bannerDirty = false
                    Toast.makeText(
                        this@EditEventDetailsActivity,
                        getString(R.string.edit_event_details_event_updated),
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                }
                is NetworkResult.Error -> {
                    // Every entered value stays; the failure lives in the bar.
                    setSaving(false)
                    showSaveFailure(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun buildRequest(current: OrganizerEventDto, bannerFileId: String?): EventRequest = EventRequest(
        title = titleInput.text.toString().trim(),
        description = descriptionInput.text.toString().trim().ifBlank { null },
        location = venueInput.text.toString().trim().ifBlank { null },
        eventLogoUrl = bannerFileId,
        category = eventCategory,
        registrationOpenAt = current.registrationOpenAt,
        registrationCloseAt = current.registrationCloseAt,
        eventStartAt = requireNotNull(current.eventStartAt),
        eventEndAt = current.eventEndAt,
        capacity = capacityInput.text.toString().trim().toInt(),
        rewardsEnabled = current.rewardsEnabled ?: false,
    )

    // ---------------------------------------------------------------- back

    /**
     * One confirm at a time, whatever the dispatch source. Back reaches
     * [OnBackPressedCallback.handleOnBackPressed] from the system gesture/button and from the top
     * bar's own arrow, and both live on the same [OnBackPressedDispatcher]: a fast double-tap (or
     * a repeated back) can deliver a second dispatch while the dialog is already up, which used to
     * stack a second copy of it.
     *
     * The guard reads `isShowing` and [onDestroy] clears the field, so a `show()` that throws
     * cannot latch the screen inert for the rest of the instance's life. A test that dispatches
     * back twice and asserts exactly one shown dialog is how the guard is proven.
     */
    private var discardDialog: AlertDialog? = null

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (saving) return
            if (!isDirty()) {
                finish()
                return
            }
            if (discardDialog?.isShowing == true) return
            val dialog = DiscardChangesDialog.create(this@EditEventDetailsActivity, onDiscard = { finish() })
            discardDialog = dialog
            dialog.setOnDismissListener { discardDialog = null }
            dialog.show()
        }
    }

    override fun onDestroy() {
        discardDialog = null
        super.onDestroy()
    }

    companion object {
        private const val MAX_BANNER_BYTES = 5L * 1024L * 1024L
        private const val KEY_DRAFT_TITLE = "edit_event_draft_title"
        private const val KEY_DRAFT_VENUE = "edit_event_draft_venue"
        private const val KEY_DRAFT_CAPACITY = "edit_event_draft_capacity"
        private const val KEY_DRAFT_DESCRIPTION = "edit_event_draft_description"
        private const val KEY_DRAFT_BANNER_DIRTY = "edit_event_draft_banner_dirty"
        private const val KEY_DRAFT_BANNER_PATH = "edit_event_draft_banner_path"
    }
}

/**
 * The read-only rows the editor stacks above `fontScale` 1.3 (§6.4/§9.3), as (row, value) pairs:
 * `STATUS`, `EVENT DATES`, `REGISTRATION WINDOW` - the same [MetricRowStacking] path the details
 * page's spec table uses, so the two screens reflow identically. `populate()` applies it from this
 * list, and the editor's Robolectric test pins the inflated rows to it.
 */
internal val organizerEventEditStackedRows: List<Pair<Int, Int>> = listOf(
    R.id.eeStatusRow to R.id.eeStatusValue,
    R.id.eeEventDatesRow to R.id.eeEventDatesValue,
    R.id.eeWindowRow to R.id.eeWindowValue,
)

/**
 * The edit lock both organizer screens share, keyed on the date-derived status: editable while the
 * event is still Upcoming (APPROVED), Draft or Pending review, locked once it is Ongoing or
 * Completed. Never key this off the raw backend label: an upcoming event can already be ACTIVE
 * (admin "activate" or an early status sweep), and locking on that label hid editing for a future
 * event. Cancelled and Rejected never reach this screen - the hub routes them to read-only details.
 */
internal fun EventStatus.isEventEditable(): Boolean =
    this == EventStatus.APPROVED || this == EventStatus.DRAFT || this == EventStatus.PENDING_REVIEW
