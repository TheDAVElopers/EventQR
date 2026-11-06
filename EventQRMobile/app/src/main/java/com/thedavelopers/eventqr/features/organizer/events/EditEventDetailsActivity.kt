package com.thedavelopers.eventqr.features.organizer.events

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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.formatCount
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Organizer event editor (§6.4 v1.7) - the Draft / Pending review / Approved screen.
 *
 * Five eyebrowed sections in the view page's block order (Event, Schedule, Registration, About,
 * Banner), the shared identity head with both meta lines hidden, and one bottom-anchored save bar.
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

    private lateinit var repository: OrganizerRepository
    private lateinit var eventId: String

    private lateinit var scroll: ScrollView
    private lateinit var head: View
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

    /** Values as the server sent them; dirty is a plain comparison against this. */
    private var original = Draft("", "", "", "")
    private var bannerDirty = false
    private var capacityErrorShown = false
    private var saving = false
    private var locked = false

    /** Suppresses the dirty recalc while the loaded values are written into the fields. */
    private var populating = false

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
        repository = OrganizerRepository(this)
        bindViews()
        applySaveBarInsets()

        eventId = intentEventId()
            ?: return showMissingEventScreen(getString(R.string.ui_organizer_edit_title))

        findViewById<TextView>(R.id.topBarTitle).setText(R.string.ui_organizer_edit_title)
        findViewById<View>(R.id.topBarBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        bannerPickButton.setOnClickListener { bannerPicker.launch("image/*") }
        wireInputs()
        onBackPressedDispatcher.addCallback(this, backCallback)
        loadEvent()
    }

    private fun bindViews() {
        scroll = findViewById(R.id.eeScroll)
        head = findViewById(R.id.oeHead)
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

    /**
     * The bar is the last block in normal layout flow, so the keyboard and the gesture bar push it
     * up instead of hiding it. `max(nav, ime)` holds for both the resized and the edge-to-edge
     * window, and `adjustResize` in the manifest covers the older path.
     */
    private fun applySaveBarInsets() {
        val baseBottom = saveBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(saveBar) { view, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(bottom = baseBottom + max(nav, ime))
            insets
        }
        ViewCompat.requestApplyInsets(saveBar)
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
                is NetworkResult.Success -> result.data?.let { populate(it) }
                is NetworkResult.Error -> showMissingEventScreen(
                    getString(R.string.ui_organizer_edit_title),
                    result.message ?: getString(R.string.event_management_hub_details_load_failed),
                )
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun populate(event: OrganizerEventDto) {
        loadedEvent = event
        locked = isLocked(event)
        val status = EventStatusBadgeStyler.resolve(
            EventStatusBadgeStyler.fromLabel(event.status),
            event.eventStartAt,
            event.eventEndAt,
        )
        OrganizerEventHeadBinder.bind(
            root = head,
            title = event.title?.takeIf { it.isNotBlank() }
                ?: getString(R.string.organizer_untitled_event),
            status = status,
            whenText = null,
            whereText = null,
        )

        populating = true
        titleInput.setText(event.title.orEmpty())
        venueInput.setText(event.venue.orEmpty())
        descriptionInput.setText(event.description.orEmpty())
        // A machine-facing numeric field: no grouping, ASCII digits, so Locale.ROOT is correct.
        capacityInput.setText(String.format(Locale.ROOT, "%d", event.capacity.coerceAtLeast(0)))
        populating = false

        eventDatesValue.text = eventDatesText(event)
        windowValue.text = registrationWindow(event)
        original = draft()
        bannerDirty = false
        selectedBannerFile = null
        loadBannerPreview(event)
        if (locked) applyLock(event) else renderDirty()
    }

    private fun isLocked(event: OrganizerEventDto): Boolean =
        event.status.equals("Active", ignoreCase = true) ||
            event.status.equals("Completed", ignoreCase = true)

    /** The one locked state that survives: no fields, no bar, one line that says why. */
    private fun applyLock(event: OrganizerEventDto) {
        val reason = getString(
            if (event.status.equals("Active", ignoreCase = true)) {
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

    /** `12 Jun 2026, 9:00 AM - 5:00 PM`, the range when the event spans days. */
    private fun eventDatesText(event: OrganizerEventDto): String {
        val start = event.eventStartAt ?: return getString(R.string.ui_organizer_value_unavailable)
        val end = event.eventEndAt
        val date = if (end != null &&
            DateFormatters.formatEventDate(end) != DateFormatters.formatEventDate(start)
        ) {
            getString(
                R.string.event_detail_date_range,
                DateFormatters.formatEventDate(start),
                DateFormatters.formatEventDate(end),
            )
        } else {
            DateFormatters.formatEventDate(start)
        }
        val time = if (end != null) {
            "${DateFormatters.formatTime(start)} - ${DateFormatters.formatTime(end)}"
        } else {
            DateFormatters.formatTime(start)
        }
        return "$date, $time"
    }

    private fun registrationWindow(event: OrganizerEventDto): String {
        val open = event.registrationOpenAt?.let { DateFormatters.displayFormatter.format(it) }
        val close = event.registrationCloseAt?.let { DateFormatters.displayFormatter.format(it) }
        return when {
            open != null && close != null -> "$open - $close"
            else -> open ?: close ?: getString(R.string.ui_organizer_value_unavailable)
        }
    }

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

    /** The head is bound from data, so a typed title has to follow it - text and its one a11y node. */
    private fun applyHeadTitle() {
        val title = titleInput.text.toString().trim()
        head.findViewById<TextView>(R.id.oeHeadTitle).text = title
        val status = head.findViewById<TextView>(R.id.oeHeadStatus).text?.toString().orEmpty()
        head.findViewById<View>(R.id.oeHeadTitleRow).contentDescription =
            if (status.isNotBlank()) "$title, $status" else title
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
        registrationOpenAt = current.registrationOpenAt,
        registrationCloseAt = current.registrationCloseAt,
        eventStartAt = requireNotNull(current.eventStartAt),
        eventEndAt = current.eventEndAt,
        capacity = capacityInput.text.toString().trim().toInt(),
        rewardsEnabled = current.rewardsEnabled ?: false,
    )

    // ---------------------------------------------------------------- back

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (saving) return
            if (!isDirty()) {
                finish()
                return
            }
            DiscardChangesDialog.create(this@EditEventDetailsActivity) { finish() }.show()
        }
    }

    companion object {
        private const val MAX_BANNER_BYTES = 5L * 1024L * 1024L
    }
}
