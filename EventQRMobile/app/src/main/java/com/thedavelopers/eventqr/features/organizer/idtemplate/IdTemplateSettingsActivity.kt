package com.thedavelopers.eventqr.features.organizer.idtemplate

import android.graphics.Color
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

        statusView = text("", 13, false).apply {
            id = com.thedavelopers.eventqr.R.id.idt_status
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        content.addView(statusView)

        saveButton = primaryButton("Save ID Display Settings") { saveConfig() }.apply {
            id = com.thedavelopers.eventqr.R.id.idt_save_button
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50),
            ).apply { setMargins(0, dp(12), 0, dp(24)) }
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
            background = rounded(
                color = Color.parseColor("#EEF2FF"),
                radiusDp = 16,
                strokeColor = Color.parseColor("#C7D2FE"),
                density = resources.displayMetrics.density,
            )
            setPadding(dp(12), dp(6), dp(16), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(12)
            }

            // Calendar / Event Icon
            addView(ImageView(this@IdTemplateSettingsActivity).apply {
                setImageResource(com.thedavelopers.eventqr.R.drawable.ic_calendar)
                setColorFilter(Color.parseColor("#4F46E5"))
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    marginEnd = dp(8)
                }
            })

            // Event Title Text
            addView(TextView(this@IdTemplateSettingsActivity).apply {
                text = titleText
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#312E81"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }

    private fun lockedFieldsCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.WHITE, 16, Color.parseColor("#E2E8F0"), density = resources.displayMetrics.density)
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(8), 0, dp(16)) }

        addView(text("Always included", 15, true, Color.parseColor("#0F172A")).apply {
            setPadding(0, 0, 0, dp(12))
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
            setPadding(0, dp(8), 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            // Left Icon Container
            addView(ImageView(this@IdTemplateSettingsActivity).apply {
                setImageResource(iconRes)
                setColorFilter(Color.parseColor("#475569"))
                background = rounded(Color.parseColor("#F1F5F9"), 10, null, density = resources.displayMetrics.density)
                setPadding(dp(9), dp(9), dp(9), dp(9))
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            })

            // Middle Text
            addView(LinearLayout(this@IdTemplateSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(12), 0, dp(8), 0)
                }
                addView(text(title, 15, true, Color.parseColor("#0F172A")))
                addView(text(subtitle, 12, false, Color.parseColor("#64748B")).apply {
                    setPadding(0, dp(2), 0, 0)
                })
            })

            // Right Locked Badge
            addView(LinearLayout(this@IdTemplateSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Color.parseColor("#F1F5F9"), 12, null, density = resources.displayMetrics.density)
                setPadding(dp(8), dp(4), dp(10), dp(4))

                addView(ImageView(this@IdTemplateSettingsActivity).apply {
                    setImageResource(com.thedavelopers.eventqr.R.drawable.ic_lock)
                    setColorFilter(Color.parseColor("#64748B"))
                    layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                        marginEnd = dp(4)
                    }
                })
                addView(text("Locked", 11, true, Color.parseColor("#64748B")))
            })
        }

    private fun toggleCard(): LinearLayout = LinearLayout(this).apply {
        tag = TOGGLES_TAG
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.WHITE, 16, Color.parseColor("#E2E8F0"), density = resources.displayMetrics.density)
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(16)) }

        buildToggleCardContent(this)
    }

    private fun buildToggleCardContent(container: LinearLayout) {
        container.removeAllViews()
        container.addView(text("Show on printed ID", 15, true, Color.parseColor("#0F172A")).apply {
            setPadding(0, 0, 0, dp(12))
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
        setPadding(0, dp(12), 0, dp(12))
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
                background = rounded(Color.parseColor("#0D9488"), 6, null, density = resources.displayMetrics.density)
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setColorFilter(Color.WHITE)
            } else {
                setImageDrawable(null)
                background = rounded(Color.WHITE, 6, Color.parseColor("#CBD5E1"), density = resources.displayMetrics.density)
            }
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        }
        addView(checkBoxView)

        // Title Text
        addView(text(IdCardLayoutConfig.displayName(field), 15, false, Color.parseColor("#0F172A")).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(12), 0, dp(8), 0)
            }
        })

        // Chevron Icon
        addView(ImageView(this@IdTemplateSettingsActivity).apply {
            setImageResource(com.thedavelopers.eventqr.R.drawable.ic_chevron_right)
            setColorFilter(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        })
    }

    private fun createDivider(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#F1F5F9"))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1),
        )
    }

    private fun renderPreview() {
        previewContainer.removeAllViews()

        val outerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            background = rounded(Color.WHITE, 16, Color.parseColor("#E2E8F0"), density = resources.displayMetrics.density)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(16)) }

            addView(text("ID Preview", 15, true, Color.parseColor("#0F172A")).apply {
                setPadding(0, 0, 0, dp(12))
            })
        }

        val previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.parseColor("#F8FAFC"), 12, Color.parseColor("#F1F5F9"), density = resources.displayMetrics.density)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.WHITE, 16, Color.parseColor("#E2E8F0"), density = resources.displayMetrics.density)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                dp(IdCardLayoutConfig.PREVIEW_WIDTH_DP),
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
            ).apply { setMargins(0, 0, 0, dp(10)) }

            addView(text("QR CODE", 10, true, Color.parseColor("#6366F1"), align = Gravity.CENTER_HORIZONTAL).apply {
                includeFontPadding = false
                setPadding(0, 0, 0, dp(6))
            })

            val qrBox = LinearLayout(this@IdTemplateSettingsActivity).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.parseColor("#EEF2FF"), 12, null, density = resources.displayMetrics.density)
                layoutParams = LinearLayout.LayoutParams(dp(previewQrSizeDp), dp(previewQrSizeDp)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                addView(ImageView(this@IdTemplateSettingsActivity).apply {
                    setImageResource(com.thedavelopers.eventqr.R.drawable.dark_ic_qr_code)
                    layoutParams = LinearLayout.LayoutParams(dp(previewQrSizeDp - 20), dp(previewQrSizeDp - 20))
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
            background = rounded(Color.parseColor("#CBD5E1"), 2, null, density = resources.displayMetrics.density)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(12), 0, dp(4))
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
        setPadding(0, dp(3), 0, dp(3))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(text(label, previewLabelFontSp, true, Color.parseColor("#6366F1"), align = Gravity.CENTER_HORIZONTAL).apply {
            includeFontPadding = false
        })
        addView(text(value, valueSizeSp, isBoldValue, Color.parseColor("#0F172A"), align = Gravity.CENTER_HORIZONTAL).apply {
            includeFontPadding = false
            setPadding(0, dp(2), 0, 0)
        })
    }

    private fun createPreviewDivider(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#F1F5F9"))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1),
        ).apply {
            setMargins(dp(12), dp(4), dp(12), dp(4))
        }
    }

    private fun loadConfig() {
        MainScope().launch {
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
            buildToggleCardContent(togglesCard)
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
