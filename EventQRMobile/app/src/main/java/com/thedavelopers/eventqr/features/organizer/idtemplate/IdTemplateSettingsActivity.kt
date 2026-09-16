package com.thedavelopers.eventqr.features.organizer.idtemplate

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.idprinting.IdCardLayoutConfig
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.registrations.RegistrationNumberFormatter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * SDD Module 3.7 — Configure ID Display Fields.
 *
 * Deviation note (capstone defense): SRS UC-22 describes logo upload, color editing, and
 * predefined template selection; SDD 3.7 explicitly overrides it — "organizer cannot edit the
 * ID layout, design, colors, logo, or visual format." Only field visibility toggling is
 * implemented here. No color picker, logo upload, or template style selector exists in this
 * screen (no leftover scaffolding for those was present to remove).
 *
 * Architecture note: implemented with the programmatic View toolkit used by every other
 * organizer screen (team decision), not Compose as the SDD text implies.
 *
 * Layout proportions are driven by [IdCardLayoutConfig] so this preview stays
 * in sync with the print output from [com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter].
 */
class IdTemplateSettingsActivity : AppCompatActivity() {

    private lateinit var repository: IdTemplateConfigRepository
    private lateinit var eventId: String
    private lateinit var content: LinearLayout
    private lateinit var previewContainer: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var saveButton: Button

    private val fieldStates = linkedMapOf(
        IdCardLayoutConfig.FIELD_ATTENDEE_ID to false,
        IdCardLayoutConfig.FIELD_ROLE to false,
        IdCardLayoutConfig.FIELD_EVENT_NAME to false,
        IdCardLayoutConfig.FIELD_EVENT_DATE to false,
    )

    // Preview sizes derived from shared config ratios × preview card dimensions
    private val previewQrSizeDp = (IdCardLayoutConfig.PREVIEW_WIDTH_DP * IdCardLayoutConfig.QR_SIZE_RATIO).roundToInt()
    private val previewQrSpacingDp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.QR_SPACING_RATIO).roundToInt()
    private val previewMarginDp = (IdCardLayoutConfig.PREVIEW_WIDTH_DP * IdCardLayoutConfig.MARGIN_RATIO).roundToInt()
    private val previewFieldSpacingDp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.FIELD_SPACING_RATIO).roundToInt()
    private val previewLabelFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.LABEL_FONT_RATIO).roundToInt()
    private val previewNameFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.NAME_FONT_RATIO).roundToInt()
    private val previewEventNameFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.EVENT_NAME_FONT_RATIO).roundToInt()
    private val previewRoleFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.ROLE_FONT_RATIO).roundToInt()
    private val previewIdFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.ID_FONT_RATIO).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = IdTemplateConfigRepository(this)
        eventId = intentEventId() ?: return showMissingEventScreen("ID Display Settings")

        content = organizerShell(
            title = "ID Display Settings",
            subtitle = intentEventTitle()?.takeIf { it.isNotBlank() },
            showBack = true,
        )
        content.addView(lockedFieldsCard().apply {
            id = com.thedavelopers.eventqr.R.id.idt_locked_card
        })
        content.addView(toggleCard().apply {
            id = com.thedavelopers.eventqr.R.id.idt_toggles_card
        })
        previewContainer = LinearLayout(this).apply {
            id = com.thedavelopers.eventqr.R.id.idt_preview_container
            orientation = LinearLayout.VERTICAL
        }
        content.addView(previewContainer)
        renderPreview()
        statusView = text("", 13, false).apply {
            id = com.thedavelopers.eventqr.R.id.idt_status
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        content.addView(statusView)
        saveButton = primaryButton("Save ID Display Settings") { saveConfig() }.apply {
            id = com.thedavelopers.eventqr.R.id.idt_save_button
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply { setMargins(0, dp(12), 0, 0) }
        }
        content.addView(saveButton)

        loadConfig()
    }

    private fun lockedFieldsCard(): LinearLayout = card(14).apply {
        addView(sectionLabel("Always included"))
        IdCardLayoutConfig.LOCKED_FIELDS.forEach { addView(lockedRow(IdCardLayoutConfig.displayName(it))) }
    }

    private fun lockedRow(label: String): LinearLayout = row().apply {
        setPadding(0, dp(6), 0, dp(6))
        addView(text(label, 15, false, MUTED).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(chip("Locked", false, MUTED).apply { alpha = 0.75f })
    }

    private fun toggleCard(): LinearLayout = card(14).apply {
        tag = TOGGLES_TAG
        addView(sectionLabel("Show on printed ID"))
        IdCardLayoutConfig.OPTIONAL_FIELDS.forEach { field ->
            addView(CheckBox(this@IdTemplateSettingsActivity).apply {
                text = IdCardLayoutConfig.displayName(field)
                textSize = 15f
                isChecked = fieldStates.getValue(field)
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnCheckedChangeListener { _, checked ->
                    fieldStates[field] = checked
                    renderPreview()
                }
            })
        }
    }

    private fun sectionLabel(label: String): TextView = text(label, 13, true).apply {
        setPadding(dp(2), dp(2), dp(2), dp(8))
    }

    private fun renderPreview() {
        previewContainer.removeAllViews()

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(previewMarginDp), dp(previewMarginDp), dp(previewMarginDp), dp(previewMarginDp))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.BLACK)
            }
            layoutParams = LinearLayout.LayoutParams(
                dp(IdCardLayoutConfig.PREVIEW_WIDTH_DP),
                dp(IdCardLayoutConfig.PREVIEW_HEIGHT_DP),
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(10), 0, dp(4))
            }
        }

        cardView.addView(text("ID Preview", 11, true, TEXT).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
            includeFontPadding = false
        })

        // QR code (locked) — size from shared config ratio
        cardView.addView(qrPlaceholder())
        // Attendee Name stays the prominent (largest/bold) identifier, now placed first below the
        // QR code per the canonical FIELD_ORDER, before Role, Event Name, Attendee ID and Event Date.
        cardView.addView(previewValue("Attendee Name", "Juan Dela Cruz", valueSizeSp = previewNameFontSp))
        if (fieldStates.getValue(IdCardLayoutConfig.FIELD_ROLE)) {
            cardView.addView(previewValue(
                IdCardLayoutConfig.displayName(IdCardLayoutConfig.FIELD_ROLE),
                IdCardLayoutConfig.sampleValue(IdCardLayoutConfig.FIELD_ROLE),
                valueSizeSp = previewRoleFontSp,
            ))
        }
        if (fieldStates.getValue(IdCardLayoutConfig.FIELD_EVENT_NAME)) {
            cardView.addView(previewBanner(
                IdCardLayoutConfig.displayName(IdCardLayoutConfig.FIELD_EVENT_NAME),
                IdCardLayoutConfig.sampleValue(IdCardLayoutConfig.FIELD_EVENT_NAME),
            ))
        }
        if (fieldStates.getValue(IdCardLayoutConfig.FIELD_ATTENDEE_ID)) {
            cardView.addView(previewValue(
                IdCardLayoutConfig.displayName(IdCardLayoutConfig.FIELD_ATTENDEE_ID),
                RegistrationNumberFormatter.format(IdCardLayoutConfig.sampleValue(IdCardLayoutConfig.FIELD_ATTENDEE_ID).toIntOrNull()) ?: "N/A",
                valueSizeSp = previewIdFontSp,
            ))
        }
        if (fieldStates.getValue(IdCardLayoutConfig.FIELD_EVENT_DATE)) {
            cardView.addView(previewValue(
                IdCardLayoutConfig.displayName(IdCardLayoutConfig.FIELD_EVENT_DATE),
                IdCardLayoutConfig.sampleValue(IdCardLayoutConfig.FIELD_EVENT_DATE),
                valueSizeSp = previewIdFontSp,
            ))
        }

        previewContainer.addView(cardView)
    }

    private fun previewBanner(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, 0, dp(previewFieldSpacingDp))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(text(label.uppercase(), previewLabelFontSp, true, MUTED, align = Gravity.CENTER_HORIZONTAL).apply { includeFontPadding = false })
        addView(text(value, previewEventNameFontSp, true, TEXT, align = Gravity.CENTER_HORIZONTAL).apply { includeFontPadding = false })
    }

    private fun qrPlaceholder(): TextView = TextView(this).apply {
        text = "QR CODE"
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(Color.BLACK)
        textSize = previewIdFontSp.toFloat()
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), Color.BLACK)
        }
        layoutParams = LinearLayout.LayoutParams(
            dp(previewQrSizeDp),
            dp(previewQrSizeDp),
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, dp(previewQrSpacingDp))
        }
    }

    private fun previewValue(label: String, value: String, valueSizeSp: Int = 16): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, 0, dp(previewFieldSpacingDp))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(text(label.uppercase(), previewLabelFontSp, true, MUTED, align = Gravity.CENTER_HORIZONTAL).apply { includeFontPadding = false })
        addView(text(value, valueSizeSp, true, TEXT, align = Gravity.CENTER_HORIZONTAL).apply { includeFontPadding = false })
    }

    private fun loadConfig() {
        MainScope().launch {
            when (val result = repository.fetchConfig(eventId)) {
                is NetworkResult.Success -> {
                    result.data.visibleFields.filterNotNull().forEach { field ->
                        if (fieldStates.containsKey(field)) fieldStates[field] = true
                    }
                    refreshCheckboxes()
                    renderPreview()
                }

                is NetworkResult.Error -> showStatus(result.message, isError = true)
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun saveConfig() {
        val visibleFields = IdCardLayoutConfig.OPTIONAL_FIELDS.filter { fieldStates.getValue(it) }
        saveButton.isEnabled = false
        MainScope().launch {
            when (val result = repository.saveConfig(eventId, visibleFields)) {
                is NetworkResult.Success -> showStatus("ID display settings saved.", isError = false)
                is NetworkResult.Error -> showStatus(result.message, isError = true)
                NetworkResult.Loading -> Unit
            }
            saveButton.isEnabled = true
        }
    }

    private fun refreshCheckboxes() {
        content.findViewWithTag<LinearLayout>(TOGGLES_TAG)?.let { togglesCard ->
            togglesCard.removeAllViews()
            togglesCard.addView(sectionLabel("Show on printed ID"))
            IdCardLayoutConfig.OPTIONAL_FIELDS.forEach { field ->
                togglesCard.addView(CheckBox(this).apply {
                    text = IdCardLayoutConfig.displayName(field)
                    textSize = 15f
                    isChecked = fieldStates.getValue(field)
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    setOnCheckedChangeListener { _, checked ->
                        fieldStates[field] = checked
                        renderPreview()
                    }
                })
            }
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        statusView.text = message
        statusView.setTextColor(if (isError) ERROR else SUCCESS)
    }

    companion object {
        private const val TOGGLES_TAG = "id_display_toggles"
    }
}
