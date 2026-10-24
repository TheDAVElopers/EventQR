package com.thedavelopers.eventqr.features.organizer.idtemplate

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.configDpToPx
import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.roundedRect
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.idprinting.IdCardLayoutConfig
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.registrations.RegistrationNumberFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
    private lateinit var organizerRepository: OrganizerRepository
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
    private val previewMarginDp = (IdCardLayoutConfig.PREVIEW_WIDTH_DP * IdCardLayoutConfig.MARGIN_RATIO).roundToInt()
    private val previewLabelFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.LABEL_FONT_RATIO).roundToInt()
    private val previewNameFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.NAME_FONT_RATIO).roundToInt()
    private val previewEventNameFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.EVENT_NAME_FONT_RATIO).roundToInt()
    private val previewRoleFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.ROLE_FONT_RATIO).roundToInt()
    private val previewIdFontSp = (IdCardLayoutConfig.PREVIEW_HEIGHT_DP * IdCardLayoutConfig.ID_FONT_RATIO).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = IdTemplateConfigRepository(this)
        organizerRepository = OrganizerRepository(this)
        eventId = intentEventId() ?: return showMissingEventScreen("ID Display Settings")

        val initialTitle = intentEventTitle()?.takeIf { it.isNotBlank() }
        content = organizerShell(
            title = "ID Display Settings",
            showBack = true,
        )

        // The detail header has no subtitle, so the event chip sits at the top of the content.
        if (initialTitle != null) content.addView(createHighlightedEventTitleView(initialTitle).also { eventTitleView = it }, 0)

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

        statusView = styledText("", R.dimen.type_body_compact, false).apply {
            id = com.thedavelopers.eventqr.R.id.idt_status
            setPadding(dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_xs), 0)
        }
        content.addView(statusView)

        saveButton = primaryButton("Save ID Display Settings") { saveConfig() }.apply {
            id = com.thedavelopers.eventqr.R.id.idt_save_button
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dimenPx(R.dimen.control_height_50),
            ).apply { setMargins(0, dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_xl)) }
        }
        content.addView(saveButton)

        loadConfig()
    }

    private var eventTitleView: View? = null

    private fun updateHeaderSubtitle(title: String) {
        if (title.isBlank()) return
        eventTitleView?.let { content.removeView(it) }
        eventTitleView = createHighlightedEventTitleView(title).also { content.addView(it, 0) }
    }

    private fun createHighlightedEventTitleView(titleText: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedRect(color(R.color.accent_container), R.dimen.radius_lg, color(R.color.accent_outline))
            setPadding(dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_6), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dimenPx(R.dimen.space_md)
            }

            // Calendar / Event Icon
            addView(ImageView(this@IdTemplateSettingsActivity).apply {
                setImageResource(com.thedavelopers.eventqr.R.drawable.ic_calendar)
                setColorFilter(color(R.color.primary))
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_sm), dimenPx(R.dimen.icon_sm)).apply {
                    marginEnd = dimenPx(R.dimen.space_sm)
                }
            })

            // Event Title Text
            addView(TextView(this@IdTemplateSettingsActivity).apply {
                text = titleText
                setTextSizeRes(R.dimen.type_body)
                strong()
                setTextColor(color(R.color.primary))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }

    private fun lockedFieldsCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg))
        background = roundedRect(color(R.color.surface), R.dimen.radius_lg, color(R.color.outline))
        elevation = dimenPx(R.dimen.elevation_card).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dimenPx(R.dimen.space_sm), 0, dimenPx(R.dimen.space_lg)) }

        addView(styledText("Always included", R.dimen.type_body, true, color(R.color.text_primary)).apply {
            setPadding(0, 0, 0, dimenPx(R.dimen.space_md))
        })

        addView(createLockedRow(
            title = "QR Code",
            subtitle = "Always shown on printed ID",
            iconRes = com.thedavelopers.eventqr.R.drawable.ic_scan,
        ))

        addView(createDivider())

        addView(createLockedRow(
            title = "Attendee Name",
            subtitle = "Always shown on printed ID",
            iconRes = com.thedavelopers.eventqr.R.drawable.ic_profile_person,
        ))
    }

    private fun createLockedRow(title: String, subtitle: String, iconRes: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dimenPx(R.dimen.space_sm), 0, dimenPx(R.dimen.space_sm))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            // Left Icon Container
            addView(ImageView(this@IdTemplateSettingsActivity).apply {
                setImageResource(iconRes)
                setColorFilter(color(R.color.text_body))
                background = roundedRect(color(R.color.surface_muted), R.dimen.radius_10)
                setPadding(dimenPx(R.dimen.space_9), dimenPx(R.dimen.space_9), dimenPx(R.dimen.space_9), dimenPx(R.dimen.space_9))
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_38), dimenPx(R.dimen.tile_38))
            })

            // Middle Text
            addView(LinearLayout(this@IdTemplateSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_sm), 0)
                }
                addView(styledText(title, R.dimen.type_body, true, color(R.color.text_primary)))
                addView(styledText(subtitle, R.dimen.type_caption, false, color(R.color.text_secondary)).apply {
                    setPadding(0, dimenPx(R.dimen.space_xxs), 0, 0)
                })
            })

            // Right Locked Badge
            addView(LinearLayout(this@IdTemplateSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedRect(color(R.color.surface_muted), R.dimen.radius_md)
                setPadding(dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_xs))

                addView(ImageView(this@IdTemplateSettingsActivity).apply {
                    setImageResource(com.thedavelopers.eventqr.R.drawable.ic_lock)
                    setColorFilter(color(R.color.text_secondary))
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_md)).apply {
                        marginEnd = dimenPx(R.dimen.space_xs)
                    }
                })
                addView(styledText("Locked", R.dimen.type_overline, true, color(R.color.text_secondary)))
            })
        }

    private fun toggleCard(): LinearLayout = LinearLayout(this).apply {
        tag = TOGGLES_TAG
        orientation = LinearLayout.VERTICAL
        setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg))
        background = roundedRect(color(R.color.surface), R.dimen.radius_lg, color(R.color.outline))
        elevation = dimenPx(R.dimen.elevation_card).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_lg)) }

        buildToggleCardContent(this)
    }

    private fun buildToggleCardContent(container: LinearLayout) {
        container.removeAllViews()
        container.addView(styledText("Show on printed ID", R.dimen.type_body, true, color(R.color.text_primary)).apply {
            setPadding(0, 0, 0, dimenPx(R.dimen.space_md))
        })

        IdCardLayoutConfig.OPTIONAL_FIELDS.forEachIndexed { index, field ->
            val isChecked = fieldStates.getValue(field)
            val row = createToggleRow(field, isChecked) { checked ->
                fieldStates[field] = checked
                renderPreview()
                buildToggleCardContent(container)
            }
            container.addView(row)

            if (index < IdCardLayoutConfig.OPTIONAL_FIELDS.lastIndex) {
                container.addView(createDivider())
            }
        }
    }

    private fun createToggleRow(
        field: String,
        isChecked: Boolean,
        onToggle: (Boolean) -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_md))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            onToggle(!isChecked)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        // Custom Check Box view
        val checkBoxView = ImageView(this@IdTemplateSettingsActivity).apply {
            if (isChecked) {
                setImageResource(com.thedavelopers.eventqr.R.drawable.ic_check_white)
                background = roundedRect(color(R.color.deco_check_teal), R.dimen.radius_6)
                setPadding(dimenPx(R.dimen.space_3), dimenPx(R.dimen.space_3), dimenPx(R.dimen.space_3), dimenPx(R.dimen.space_3))
                setColorFilter(color(R.color.on_primary))
            } else {
                setImageDrawable(null)
                background = roundedRect(color(R.color.surface), R.dimen.radius_6, color(R.color.outline_strong))
            }
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_22), dimenPx(R.dimen.icon_22))
        }
        addView(checkBoxView)

        // Title Text
        addView(styledText(IdCardLayoutConfig.displayName(field), R.dimen.type_body, false, color(R.color.text_primary)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_sm), 0)
            }
        })

        // Chevron Icon
        addView(ImageView(this@IdTemplateSettingsActivity).apply {
            setImageResource(com.thedavelopers.eventqr.R.drawable.ic_chevron_right)
            setColorFilter(color(R.color.neutral))
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_18), dimenPx(R.dimen.icon_18))
        })
    }

    private fun createDivider(): View = View(this).apply {
        setBackgroundColor(color(R.color.surface_muted))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dimenPx(R.dimen.stroke_hairline),
        )
    }

    private fun renderPreview() {
        previewContainer.removeAllViews()

        val outerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_20))
            background = roundedRect(color(R.color.surface), R.dimen.radius_lg, color(R.color.outline))
            elevation = dimenPx(R.dimen.elevation_card).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_lg)) }

            addView(styledText("ID Preview", R.dimen.type_body, true, color(R.color.text_primary)).apply {
                setPadding(0, 0, 0, dimenPx(R.dimen.space_md))
            })
        }

        val previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_20))
            background = roundedRect(color(R.color.background), R.dimen.radius_md, color(R.color.surface_muted))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg))
            background = roundedRect(color(R.color.surface), R.dimen.radius_lg, color(R.color.outline))
            elevation = dimenPx(R.dimen.elevation_raised).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                configDp(IdCardLayoutConfig.PREVIEW_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // 1. QR Code section
        val qrSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_10)) }

            addView(styledText("QR CODE", R.dimen.type_micro, true, color(R.color.chart_5), align = Gravity.CENTER_HORIZONTAL).apply {
                includeFontPadding = false
                setPadding(0, 0, 0, dimenPx(R.dimen.space_6))
            })

            val qrBox = LinearLayout(this@IdTemplateSettingsActivity).apply {
                gravity = Gravity.CENTER
                background = roundedRect(color(R.color.accent_container), R.dimen.radius_md)
                layoutParams = LinearLayout.LayoutParams(configDp(previewQrSizeDp), configDp(previewQrSizeDp)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                addView(ImageView(this@IdTemplateSettingsActivity).apply {
                    setImageResource(com.thedavelopers.eventqr.R.drawable.dark_ic_qr_code)
                    layoutParams = LinearLayout.LayoutParams(configDp(previewQrSizeDp - 20), configDp(previewQrSizeDp - 20))
                })
            }
            addView(qrBox)
        }
        cardView.addView(qrSection)

        // 2. Attendee Name (ALWAYS shown)
        cardView.addView(previewField(
            label = "ATTENDEE NAME",
            value = "Juan Dela Cruz",
            valueSizeSp = previewNameFontSp,
            isBoldValue = true,
        ))

        // 3. Role
        if (fieldStates.getValue(IdCardLayoutConfig.FIELD_ROLE)) {
            cardView.addView(previewField(
                label = IdCardLayoutConfig.displayName(IdCardLayoutConfig.FIELD_ROLE).uppercase(),
                value = IdCardLayoutConfig.sampleValue(IdCardLayoutConfig.FIELD_ROLE),
                valueSizeSp = previewRoleFontSp,
                isBoldValue = true,
            ))
        }

        // 4. Event Name
        if (fieldStates.getValue(IdCardLayoutConfig.FIELD_EVENT_NAME)) {
            cardView.addView(createPreviewDivider())
            val displayEventTitle = intentEventTitle()?.takeIf { it.isNotBlank() }
                ?: IdCardLayoutConfig.sampleValue(IdCardLayoutConfig.FIELD_EVENT_NAME)
            cardView.addView(previewField(
                label = IdCardLayoutConfig.displayName(IdCardLayoutConfig.FIELD_EVENT_NAME).uppercase(),
                value = displayEventTitle,
                valueSizeSp = previewEventNameFontSp,
                isBoldValue = true,
            ))
        }

        // 5. Attendee ID
        if (fieldStates.getValue(IdCardLayoutConfig.FIELD_ATTENDEE_ID)) {
            cardView.addView(createPreviewDivider())
            val formattedId = RegistrationNumberFormatter.format(
                IdCardLayoutConfig.sampleValue(IdCardLayoutConfig.FIELD_ATTENDEE_ID).toIntOrNull()
            ) ?: "#012"
            cardView.addView(previewField(
                label = IdCardLayoutConfig.displayName(IdCardLayoutConfig.FIELD_ATTENDEE_ID).uppercase(),
                value = formattedId,
                valueSizeSp = previewIdFontSp,
                isBoldValue = true,
            ))
        }

        // 6. Event Date
        if (fieldStates.getValue(IdCardLayoutConfig.FIELD_EVENT_DATE)) {
            cardView.addView(createPreviewDivider())
            cardView.addView(previewField(
                label = IdCardLayoutConfig.displayName(IdCardLayoutConfig.FIELD_EVENT_DATE).uppercase(),
                value = IdCardLayoutConfig.sampleValue(IdCardLayoutConfig.FIELD_EVENT_DATE),
                valueSizeSp = previewIdFontSp,
                isBoldValue = true,
            ))
        }

        // Bottom pill handle bar
        cardView.addView(View(this).apply {
            background = roundedRect(color(R.color.outline_strong), R.dimen.radius_2)
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_tile_md), dimenPx(R.dimen.bar_thin)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_xs))
            }
        })

        previewBox.addView(cardView)
        outerCard.addView(previewBox)
        previewContainer.addView(outerCard)
    }

    private fun previewField(
        label: String,
        value: String,
        valueSizeSp: Int = 14,
        isBoldValue: Boolean = true,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dimenPx(R.dimen.space_3), 0, dimenPx(R.dimen.space_3))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(configText(label, previewLabelFontSp, true, color(R.color.chart_5), align = Gravity.CENTER_HORIZONTAL).apply {
            includeFontPadding = false
        })
        addView(configText(value, valueSizeSp, isBoldValue, color(R.color.text_primary), align = Gravity.CENTER_HORIZONTAL).apply {
            includeFontPadding = false
            setPadding(0, dimenPx(R.dimen.space_xxs), 0, 0)
        })
    }

    private fun createPreviewDivider(): View = View(this).apply {
        setBackgroundColor(color(R.color.surface_muted))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dimenPx(R.dimen.stroke_hairline),
        ).apply {
            setMargins(dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_xs))
        }
    }

    private fun loadConfig() {
        lifecycleScope.launch {
            if (intentEventTitle().isNullOrBlank()) {
                val eventLoad = organizerRepository.loadEventForMvp(eventId)
                eventLoad.data?.title?.takeIf { it.isNotBlank() }?.let { fetchedTitle ->
                    updateHeaderSubtitle(fetchedTitle)
                }
            }

            when (val result = repository.fetchConfig(eventId)) {
                is NetworkResult.Success -> {
                    result.data.visibleFields.filterNotNull().forEach { field ->
                        if (fieldStates.containsKey(field)) fieldStates[field] = true
                    }
                    refreshCheckboxes()
                    renderPreview()
                }

                is NetworkResult.Error -> {
                    // Older servers 404 when no template was saved yet: show the default (QR + name) card.
                    if ((result.throwable as? retrofit2.HttpException)?.code() == 404) {
                        refreshCheckboxes()
                        renderPreview()
                    } else {
                        showStatus(result.message, isError = true)
                    }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun saveConfig() {
        val visibleFields = IdCardLayoutConfig.OPTIONAL_FIELDS.filter { fieldStates.getValue(it) }
        saveButton.isEnabled = false
        lifecycleScope.launch {
            when (val result = withContext(NonCancellable) { repository.saveConfig(eventId, visibleFields) }.also { ensureActive() }) {
                is NetworkResult.Success -> showStatus("ID display settings saved.", isError = false)
                is NetworkResult.Error -> showStatus(result.message, isError = true)
                NetworkResult.Loading -> Unit
            }
            saveButton.isEnabled = true
        }
    }

    private fun refreshCheckboxes() {
        content.findViewWithTag<LinearLayout>(TOGGLES_TAG)?.let { togglesCard ->
            buildToggleCardContent(togglesCard)
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        statusView.text = message
        statusView.setTextColor(if (isError) orgColors.error else orgColors.success)
    }

    /**
     * Config-derived dp -> px for the ID preview geometry ([IdCardLayoutConfig] widths, ratios). These numbers mirror the printed
     * card, they are not design tokens, so they intentionally bypass the `R.dimen` scale.
     */
    private fun configDp(value: Int): Int = configDpToPx(value)

    /** Same as [configDp] for the preview font sizes (sp) derived from [IdCardLayoutConfig] ratios. */
    private fun configText(value: String, sizeSp: Int, bold: Boolean, color: Int, align: Int? = null): TextView =
        TextView(this).apply {
            text = value
            textSize = sizeSp.toFloat()
            setTextColor(color)
            if (bold) strong()
            includeFontPadding = true
            align?.let { gravity = it }
        }

    companion object {
        private const val TOGGLES_TAG = "id_display_toggles"
    }
}
