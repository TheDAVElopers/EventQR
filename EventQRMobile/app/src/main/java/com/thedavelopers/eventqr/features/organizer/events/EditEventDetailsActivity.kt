package com.thedavelopers.eventqr.features.organizer.events

import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.roundedRect
import com.thedavelopers.eventqr.features.organizer.styledText
import com.thedavelopers.eventqr.features.organizer.cardRes
import androidx.lifecycle.lifecycleScope
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.ghostButton
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.intentEventTitle
import com.thedavelopers.eventqr.features.organizer.intentEventViewOnly
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerEventDto
import com.thedavelopers.eventqr.features.organizer.orgColors
import com.thedavelopers.eventqr.features.organizer.organizerShell
import com.thedavelopers.eventqr.features.organizer.primaryButton
import com.thedavelopers.eventqr.features.organizer.showMissingEventScreen
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SDD 3.5 (UC-20) — Manage Approved Event Details.
 */
class EditEventDetailsActivity : AppCompatActivity() {

    private lateinit var repository: OrganizerRepository
    private lateinit var eventId: String
    private lateinit var content: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var saveButton: Button
    private lateinit var eventHeaderTitleView: TextView
    private var screenTitle: String = EDIT_LABEL

    private lateinit var titleInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var venueInput: EditText
    private lateinit var capacityInput: EditText

    private lateinit var regOpenView: TextView
    private lateinit var regCloseView: TextView
    private lateinit var eventStartView: TextView
    private lateinit var eventEndView: TextView

    private lateinit var bannerPreview: ImageView
    private lateinit var bannerStatus: TextView
    private lateinit var bannerPickButton: Button

    private var loadedEvent: OrganizerEventDto? = null
    private var selectedBannerFile: File? = null
    private var newBannerFileId: String? = null

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val displayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

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
                        bannerStatus.text = getString(R.string.edit_event_details_unable_to_process_banner_please_try)
                        bannerStatus.setTextColor(orgColors.error)
                    }
                }
                result.resultCode == UCrop.RESULT_ERROR -> {
                    result.data?.let { UCrop.getError(it) }
                    bannerStatus.text = getString(R.string.edit_event_details_unable_to_crop_banner_please_choose)
                    bannerStatus.setTextColor(orgColors.error)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        eventId = intentEventId() ?: return showMissingEventScreen("Edit Event Details")

        screenTitle = if (intentEventViewOnly()) VIEW_LABEL else EDIT_LABEL
        content = organizerShell(
            title = screenTitle,
            subtitle = null,
            showBack = true,
        )
        content.addView(buildEventHeaderCard())
        content.addView(buildLockedSchedule())
        content.addView(buildForm())
        statusView = styledText("", R.dimen.type_body_compact, false).apply {
            id = com.thedavelopers.eventqr.R.id.eed_status
            setPadding(dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_xs), 0)
        }
        content.addView(statusView)
        saveButton = primaryButton("Save Changes") { saveChanges() }.apply {
            id = com.thedavelopers.eventqr.R.id.eed_save_button
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dimenPx(R.dimen.space_xxxl),
            ).apply { setMargins(0, dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_lg)) }
        }
        content.addView(saveButton)

        loadEvent()
    }

    private fun buildEventHeaderCard(): LinearLayout = cardRes(R.dimen.space_lg).apply {
        id = com.thedavelopers.eventqr.R.id.eed_event_header_card
        background = roundedRect(color(R.color.accent_container), R.dimen.radius_lg, color(R.color.outline))
        elevation = dimenPx(R.dimen.elevation_raised).toFloat()

        val row = LinearLayout(this@EditEventDetailsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val iconBox = FrameLayout(this@EditEventDetailsActivity).apply {
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_44), dimenPx(R.dimen.tile_44)).apply { marginEnd = dimenPx(R.dimen.space_md) }
            background = roundedRect(color(R.color.secondary_container), R.dimen.radius_md)
            addView(ImageView(this@EditEventDetailsActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dimenPx(R.dimen.icon_22), dimenPx(R.dimen.icon_22), android.view.Gravity.CENTER)
                setImageResource(R.drawable.ic_calendar)
                setColorFilter(color(R.color.primary))
            })
        }
        row.addView(iconBox)

        val textStack = LinearLayout(this@EditEventDetailsActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            addView(styledText("EVENT NAME", R.dimen.type_overline, true, color(R.color.text_label)).apply {
                setPadding(0, 0, 0, dimenPx(R.dimen.space_xxs))
            })

            eventHeaderTitleView = styledText(intentEventTitle()?.takeIf { it.isNotBlank() } ?: "-", R.dimen.type_title_large, true, color(R.color.text_primary)).apply {
                id = com.thedavelopers.eventqr.R.id.eed_event_header_title
            }
            addView(eventHeaderTitleView)
        }
        row.addView(textStack)

        addView(row)
    }

    private fun buildForm(): LinearLayout {
        val formCard = cardRes(R.dimen.space_lg).apply {
            id = com.thedavelopers.eventqr.R.id.eed_form_card
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dimenPx(R.dimen.space_md))
        }

        val iconBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_38), dimenPx(R.dimen.tile_38)).apply { marginEnd = dimenPx(R.dimen.space_10) }
            background = roundedRect(color(R.color.secondary_container), R.dimen.radius_10)
            addView(ImageView(this@EditEventDetailsActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md), android.view.Gravity.CENTER)
                setImageResource(R.drawable.ic_edit_pencil)
                setColorFilter(color(R.color.primary))
            })
        }
        headerRow.addView(iconBox)

        headerRow.addView(styledText("Editable details", R.dimen.type_body_large, true, orgColors.text).apply {
            id = com.thedavelopers.eventqr.R.id.eed_form_title
        })

        formCard.addView(headerRow)

        titleInput = addInputRow(
            formCard,
            "Title",
            R.drawable.ic_edit_note,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        ).apply {
            id = com.thedavelopers.eventqr.R.id.eed_title_input
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val newTitle = s?.toString()?.trim().orEmpty()
                    if (newTitle.isNotBlank()) {
                        eventHeaderTitleView.text = newTitle
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        descriptionInput = addInputRow(
            formCard,
            "Description",
            R.drawable.ic_file,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            singleLine = false,
            minLines = 3,
        ).apply {
            id = com.thedavelopers.eventqr.R.id.eed_description_input
        }

        venueInput = addInputRow(
            formCard,
            "Venue",
            R.drawable.ic_location,
        ).apply {
            id = com.thedavelopers.eventqr.R.id.eed_venue_input
        }

        capacityInput = addInputRow(
            formCard,
            "Capacity",
            R.drawable.ic_group,
            inputType = InputType.TYPE_CLASS_NUMBER,
        ).apply {
            id = com.thedavelopers.eventqr.R.id.eed_capacity_input
        }

        val bannerHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dimenPx(R.dimen.space_lg), 0, dimenPx(R.dimen.space_sm))
        }
        val bannerIconBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_tile_sm), dimenPx(R.dimen.icon_tile_sm)).apply { marginEnd = dimenPx(R.dimen.space_10) }
            background = roundedRect(color(R.color.secondary_container), R.dimen.radius_10)
            addView(ImageView(this@EditEventDetailsActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dimenPx(R.dimen.icon_18), dimenPx(R.dimen.icon_18), android.view.Gravity.CENTER)
                setImageResource(R.drawable.ic_camera)
                setColorFilter(color(R.color.primary))
            })
        }
        bannerHeaderRow.addView(bannerIconBox)
        bannerHeaderRow.addView(styledText("Event Banner", R.dimen.type_body, true, orgColors.text))
        formCard.addView(bannerHeaderRow)

        bannerPreview = ImageView(this).apply {
            id = com.thedavelopers.eventqr.R.id.eed_banner_preview
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedRect(color(R.color.surface_muted), R.dimen.radius_md, orgColors.border)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.size_180))
        }
        formCard.addView(bannerPreview)

        formCard.addView(
            styledText("No banner set. Tap below to choose a 16:9 landscape image.", R.dimen.type_caption, false, orgColors.muted).apply {
                id = com.thedavelopers.eventqr.R.id.eed_banner_status
                setPadding(0, dimenPx(R.dimen.space_sm), 0, 0)
            }.also { bannerStatus = it },
        )

        formCard.addView(ghostButton("Choose new banner") { bannerPicker.launch("image/*") }.apply {
            id = com.thedavelopers.eventqr.R.id.eed_banner_pick_button
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.tile_44)).apply {
                setMargins(0, dimenPx(R.dimen.space_10), 0, 0)
            }
        }.also { bannerPickButton = it })

        formCard.addView(
            styledText("Locked: eventId, organizer, approval status, registration windows, start date, end date and rewards stay unchanged.", R.dimen.type_caption, false, orgColors.muted).apply {
                id = com.thedavelopers.eventqr.R.id.eed_locked_note
                setPadding(0, dimenPx(R.dimen.space_14), 0, 0)
            },
        )
        return formCard
    }

    private fun buildLockedSchedule(): LinearLayout {
        val scheduleCard = cardRes(R.dimen.space_lg).apply {
            id = com.thedavelopers.eventqr.R.id.eed_schedule_card
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dimenPx(R.dimen.space_md))
        }

        val iconBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_38), dimenPx(R.dimen.tile_38)).apply { marginEnd = dimenPx(R.dimen.space_10) }
            background = roundedRect(color(R.color.secondary_container), R.dimen.radius_10)
            addView(ImageView(this@EditEventDetailsActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md), android.view.Gravity.CENTER)
                setImageResource(R.drawable.ic_calendar)
                setColorFilter(color(R.color.primary))
            })
        }
        headerRow.addView(iconBox)

        headerRow.addView(styledText("Schedule (Locked)", R.dimen.type_body_large, true, orgColors.text).apply {
            id = com.thedavelopers.eventqr.R.id.eed_schedule_title
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        headerRow.addView(styledText("🔒 Locked", R.dimen.type_overline, true, color(R.color.primary)).apply {
            setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_xs))
            background = roundedRect(color(R.color.secondary_container), R.dimen.radius_md)
        })

        scheduleCard.addView(headerRow)

        regOpenView = addLockedRow(scheduleCard, "Registration Start Date & Time", R.drawable.ic_row_clock)!!.apply {
            id = com.thedavelopers.eventqr.R.id.eed_reg_open_value
        }
        regCloseView = addLockedRow(scheduleCard, "Registration End Date & Time", R.drawable.ic_row_clock)!!.apply {
            id = com.thedavelopers.eventqr.R.id.eed_reg_close_value
        }
        eventStartView = addLockedRow(scheduleCard, "Event Start Date & Time", R.drawable.ic_calendar)!!.apply {
            id = com.thedavelopers.eventqr.R.id.eed_event_start_value
        }
        eventEndView = addLockedRow(scheduleCard, "Event End Date & Time", R.drawable.ic_calendar)!!.apply {
            id = com.thedavelopers.eventqr.R.id.eed_event_end_value
        }
        return scheduleCard
    }

    private fun addLockedRow(parent: LinearLayout, label: String, iconRes: Int): TextView? {
        val rowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dimenPx(R.dimen.space_sm), 0, dimenPx(R.dimen.space_sm))
        }

        val iconBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_tile_sm), dimenPx(R.dimen.icon_tile_sm)).apply { marginEnd = dimenPx(R.dimen.space_10) }
            background = roundedRect(color(R.color.secondary_container), R.dimen.radius_10)
            addView(ImageView(this@EditEventDetailsActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dimenPx(R.dimen.icon_18), dimenPx(R.dimen.icon_18), android.view.Gravity.CENTER)
                setImageResource(iconRes)
                setColorFilter(color(R.color.primary))
            })
        }
        rowContainer.addView(iconBox)

        val rightStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(styledText(label, R.dimen.type_body_compact, true, orgColors.text).apply { setPadding(0, 0, 0, dimenPx(R.dimen.space_xs)) })

            val value = styledText("-", R.dimen.type_body_compact, false, color(R.color.text_secondary)).apply {
                background = roundedRect(color(R.color.surface_muted), R.dimen.radius_10, orgColors.border)
                setPadding(dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_10))
                isEnabled = false
            }
            addView(value)
        }
        rowContainer.addView(rightStack)
        parent.addView(rowContainer)

        return rightStack.getChildAt(1) as? TextView
    }

    private fun addInputRow(
        parent: LinearLayout,
        label: String,
        iconRes: Int,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        singleLine: Boolean = true,
        minLines: Int = 1,
    ): EditText {
        val rowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (singleLine) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.TOP
            setPadding(0, dimenPx(R.dimen.space_sm), 0, dimenPx(R.dimen.space_sm))
        }

        val iconBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_tile_sm), dimenPx(R.dimen.icon_tile_sm)).apply {
                marginEnd = dimenPx(R.dimen.space_10)
                if (!singleLine) topMargin = dimenPx(R.dimen.space_xs)
            }
            background = roundedRect(color(R.color.secondary_container), R.dimen.radius_10)
            addView(ImageView(this@EditEventDetailsActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dimenPx(R.dimen.icon_18), dimenPx(R.dimen.icon_18), android.view.Gravity.CENTER)
                setImageResource(iconRes)
                setColorFilter(color(R.color.primary))
            })
        }
        rowContainer.addView(iconBox)

        val rightStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(styledText(label, R.dimen.type_body_compact, true, orgColors.text).apply { setPadding(0, 0, 0, dimenPx(R.dimen.space_xs)) })

            val editText = EditText(this@EditEventDetailsActivity).apply {
                this.inputType = inputType
                isSingleLine = singleLine
                this.minLines = minLines
                setTextSizeRes(R.dimen.type_body_medium)
                setTextColor(orgColors.text)
                background = roundedRect(color(R.color.background), R.dimen.radius_10, orgColors.border)
                setPadding(dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_10))
            }
            addView(editText)
        }
        rowContainer.addView(rightStack)
        parent.addView(rowContainer)

        return rightStack.getChildAt(1) as EditText
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
                bannerStatus.text = getString(R.string.edit_event_details_banner_must_not_exceed_5_mb_please_c)
                bannerStatus.setTextColor(orgColors.error)
                return
            }
            selectedBannerFile?.delete()
            selectedBannerFile = croppedFile
            bannerPreview.setImageURI(croppedUri)
            bannerPreview.background = null
            bannerStatus.text = getString(R.string.edit_event_details_new_banner_selected_16_9_it_will_be)
            bannerStatus.setTextColor(orgColors.accent)
        }.onFailure {
            bannerStatus.text = getString(R.string.edit_event_details_unable_to_attach_banner_please_choos)
            bannerStatus.setTextColor(orgColors.error)
        }
    }

    private fun loadBannerPreview(current: OrganizerEventDto) {
        val fileId = current.eventLogoUrl?.trim().orEmpty()
        if (fileId.isBlank()) {
            bannerPreview.visibility = View.GONE
            bannerStatus.text = getString(R.string.edit_event_details_no_banner_set_tap_below_to_choose_a)
            return
        }
        lifecycleScope.launch {
            when (val result = repository.getStoredFile(fileId)) {
                is NetworkResult.Success -> {
                    val encoded = result.data?.contentBase64
                    if (encoded.isNullOrBlank()) {
                        bannerPreview.visibility = View.GONE
                        return@launch
                    }
                    runCatching {
                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.onSuccess { bitmap ->
                        if (bitmap != null) {
                            bannerPreview.setImageBitmap(bitmap)
                            bannerPreview.background = null
                            bannerPreview.visibility = View.VISIBLE
                            bannerStatus.text = getString(R.string.edit_event_details_current_banner_tap_below_to_replace)
                        } else {
                            bannerPreview.visibility = View.GONE
                        }
                    }.onFailure {
                        bannerPreview.visibility = View.GONE
                    }
                }
                is NetworkResult.Error -> bannerPreview.visibility = View.GONE
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun loadEvent() {
        lifecycleScope.launch {
            when (val result = repository.fetchOrganizerEvent(eventId)) {
                is NetworkResult.Success -> result.data?.let { populate(it) }
                is NetworkResult.Error -> {
                    statusView.text = result.message
                    statusView.setTextColor(orgColors.error)
                    saveButton.isEnabled = false
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun populate(event: OrganizerEventDto) {
        loadedEvent = event
        val titleText = event.title.orEmpty()
        eventHeaderTitleView.text = titleText.ifBlank { "-" }
        titleInput.setText(titleText)
        descriptionInput.setText(event.description.orEmpty())
        venueInput.setText(event.venue.orEmpty())
        capacityInput.setText(event.capacity.coerceAtLeast(0).toString())
        regOpenView.text = event.registrationOpenAt?.let { formatLockedInstant(it) } ?: "-"
        regCloseView.text = event.registrationCloseAt?.let { formatLockedInstant(it) } ?: "-"
        eventStartView.text = event.eventStartAt?.let { formatLockedInstant(it) } ?: "-"
        eventEndView.text = event.eventEndAt?.let { formatLockedInstant(it) } ?: "-"
        loadBannerPreview(event)
        if (isEditLocked(event)) applyEditLock(event)
    }

    private fun formatLockedInstant(instant: java.time.Instant): String =
        java.time.LocalDateTime.ofInstant(instant, zoneId).format(displayFormatter)

    private fun isEditLocked(event: OrganizerEventDto): Boolean =
        event.status.equals("Active", ignoreCase = true) ||
            event.status.equals("Completed", ignoreCase = true)

    private fun applyEditLock(event: OrganizerEventDto) {
        val reason = getString(if (event.status.equals("Active", ignoreCase = true)) R.string.edit_event_details_ongoing else R.string.edit_event_details_completed)
        statusView.text = getString(R.string.edit_event_details_editing_locked, reason)
        statusView.setTextColor(orgColors.error)
        screenTitle = VIEW_LABEL
        updateHeaderTitle(VIEW_LABEL)
        listOf(titleInput, descriptionInput, venueInput, capacityInput).forEach { it.isEnabled = false }
        bannerPickButton.isEnabled = false
        saveButton.visibility = View.GONE
    }

    private fun updateHeaderTitle(label: String) {
        var parent: android.view.ViewParent? = content.parent
        while (parent is ViewGroup && parent.parent is ViewGroup) parent = parent.parent
        val root = parent as? ViewGroup ?: return
        fun find(group: ViewGroup): TextView? {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is TextView) {
                    val text = child.text?.toString()
                    if (text == EDIT_LABEL || text == VIEW_LABEL) return child
                    continue
                }
                if (child is ViewGroup) find(child)?.let { return it }
            }
            return null
        }
        find(root)?.text = label
    }

    private fun saveChanges() {
        val title = titleInput.text.toString().trim()
        val capacityValue = capacityInput.text.toString().trim().toIntOrNull()

        titleInput.error = if (title.isBlank()) "Title is required" else null
        if (capacityValue == null || capacityValue <= 0) capacityInput.error = getString(R.string.edit_event_details_capacity_must_be_greater_than_0)
        if (title.isBlank() || capacityValue == null || capacityValue <= 0) return

        val current = loadedEvent ?: run {
            Toast.makeText(this, this.getString(R.string.edit_event_details_event_not_loaded_yet), Toast.LENGTH_SHORT).show()
            return
        }

        saveButton.isEnabled = false
        statusView.text = ""
        lifecycleScope.launch {
            val uploadResult = selectedBannerFile?.let { file ->
                when (val result = withContext(NonCancellable) { repository.uploadEventBanner(file) }.also { ensureActive() }) {
                    is NetworkResult.Success -> result.data?.fileId?.toString()
                    is NetworkResult.Error -> {
                        statusView.text = result.message.ifBlank { "Could not upload banner. Please try another image." }
                        statusView.setTextColor(orgColors.error)
                        saveButton.isEnabled = true
                        return@launch
                    }
                    NetworkResult.Loading -> null
                }
            }
            val bannerFileId = uploadResult ?: newBannerFileId ?: current.eventLogoUrl

            val request = buildRequest(current, bannerFileId)
            when (val result = withContext(NonCancellable) { repository.updateOrganizerEvent(eventId, request) }.also { ensureActive() }) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@EditEventDetailsActivity, this@EditEventDetailsActivity.getString(R.string.edit_event_details_event_updated), Toast.LENGTH_SHORT).show()
                    finish()
                }
                is NetworkResult.Error -> {
                    statusView.text = result.message
                    statusView.setTextColor(orgColors.error)
                    saveButton.isEnabled = true
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

    companion object {
        private const val MAX_BANNER_BYTES = 5L * 1024L * 1024L
        private const val EDIT_LABEL = "Edit Event Details"
        private const val VIEW_LABEL = "View Event Details"
    }
}
