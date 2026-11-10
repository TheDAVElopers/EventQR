package com.thedavelopers.eventqr.features.organizer

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenPx

/**
 * Shared organizer states (§5.10, §5.11). Every screen uses the same empty state, the same warning
 * strip and the same event selector field; nothing here draws its own card or its own gradient.
 */

internal fun AppCompatActivity.loadingState(message: String): View =
    cardRes(R.dimen.ui_card_padding).apply {
        addView(
            styledText(message, R.dimen.ui_type_body, false, orgColors.muted).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            },
        )
    }

/** Left-aligned empty state at 20dp with exactly one action (§5.10). */
internal fun AppCompatActivity.emptyState(
    iconRes: Int = AppIcons.Event,
    title: String,
    subtext: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    primary: Boolean = false,
): View = EventQrEmptyState(this).apply {
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    setIcon(iconRes)
    show(title, subtext)
    if (!actionLabel.isNullOrBlank() && onAction != null) {
        setAction(actionLabel, primary = primary, onClick = onAction)
    }
}

internal fun AppCompatActivity.centeredEmptyState(
    iconRes: Int = AppIcons.Event,
    title: String,
    subtext: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
): View = emptyState(iconRes, title, subtext, actionLabel, onAction)

/** Error = the same empty state, with the server's message and a Retry action. */
internal fun AppCompatActivity.errorState(
    message: String,
    onRetry: (() -> Unit)? = null,
): View = EventQrEmptyState(this).apply {
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    setIcon(AppIcons.Notification)
    show(getString(R.string.organizer_dashboard_events_load_failed), message)
    if (onRetry != null) {
        setAction(getString(R.string.organizer_retry), primary = false, onClick = onRetry)
    }
}

/** The organizer event list failed to load (not "no events"): the server's message, or a generic one, with Retry. */
internal fun AppCompatActivity.eventsLoadErrorState(message: String?, onRetry: () -> Unit): View =
    errorState(message?.takeIf { it.isNotBlank() } ?: getString(R.string.organizer_dashboard_events_load_failed), onRetry)

/**
 * Offline / limited-data notice: a 1dp semantic warning strip, not a tinted card. It is inside the
 * content flow, so it reads as a status line rather than a second header.
 */
internal fun <T> AppCompatActivity.dataSourceBanner(load: OrganizerMvpLoad<T>): View? {
    if (load.source == OrganizerMvpDataSource.BACKEND) return null
    val message = load.message?.takeIf { it.isNotBlank() } ?: getString(R.string.organizer_limited_local_data)
    return TextView(this).apply {
        text = message
        setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
        setTextColor(color(R.color.ui_on_warning_container))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dimenPx(R.dimen.space_16), dimenPx(R.dimen.space_12), dimenPx(R.dimen.space_16), dimenPx(R.dimen.space_12))
        background = ContextCompat.getDrawable(context, R.drawable.bg_ui_notice_warning)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_8)) }
    }
}

/**
 * Event selector as a 56dp field row (§6.4 Reports): title + date, one 16dp caret. The dropdown
 * is a plain surface popup - it is not a card wrapping a hidden spinner.
 */
internal fun AppCompatActivity.eventSelector(
    events: List<OrganizerMvpEvent>,
    selectedEventId: String,
    onSelected: (OrganizerMvpEvent) -> Unit,
): View {
    val approvedEvents = events.approvedOnly()
    val titles = approvedEvents.map { it.title.ifBlank { getString(R.string.organizer_untitled_event) } }
    var selectedIndex = approvedEvents.indexOfFirst { it.id == selectedEventId }.takeIf { it >= 0 } ?: 0
    if (approvedEvents.isEmpty()) selectedIndex = -1

    val titleText = styledText(
        titles.getOrNull(selectedIndex) ?: getString(R.string.organizer_select_event),
        R.dimen.ui_type_body,
        false,
        orgColors.text,
    )
    val arrow = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.ui_chevron), dimenPx(R.dimen.ui_chevron))
        setImageResource(AppIcons.Expand)
        imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.ui_text_secondary))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val field = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_12), 0)
        minimumHeight = dimenPx(R.dimen.ui_organizer_selector_height)
        isClickable = approvedEvents.isNotEmpty()
        isFocusable = approvedEvents.isNotEmpty()
        background = ContextCompat.getDrawable(context, R.drawable.bg_ui_field)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dimenPx(R.dimen.ui_organizer_selector_height),
        )
        addView(titleText.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        addView(arrow.apply { layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.ui_chevron), dimenPx(R.dimen.ui_chevron)) })
    }

    var popup: PopupWindow? = null

    fun buildDropdown(): LinearLayout = LinearLayout(this@eventSelector).apply {
        orientation = LinearLayout.VERTICAL
        background = ContextCompat.getDrawable(this@eventSelector, R.drawable.bg_ui_card)
        setPadding(0, dimenPx(R.dimen.space_8), 0, dimenPx(R.dimen.space_8))
        approvedEvents.forEachIndexed { index, event ->
            val selected = index == selectedIndex
            addView(
                LinearLayout(this@eventSelector).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dimenPx(R.dimen.ui_touch_min)
                    setPadding(
                        dimenPx(R.dimen.space_16),
                        dimenPx(R.dimen.space_12),
                        dimenPx(R.dimen.space_16),
                        dimenPx(R.dimen.space_12),
                    )
                    setBackgroundColor(
                        ContextCompat.getColor(
                            this@eventSelector,
                            if (selected) R.color.ui_surface_pressed else R.color.ui_surface,
                        ),
                    )
                    setOnClickListener {
                        selectedIndex = index
                        titleText.text = titles[index]
                        titleText.setTextColor(orgColors.text)
                        popup?.dismiss()
                        onSelected(event)
                    }
                    addView(
                        TextView(this@eventSelector).apply {
                            text = titles[index]
                            setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
                            setTextColor(
                                ContextCompat.getColor(
                                    this@eventSelector,
                                    if (selected) R.color.ui_primary else R.color.ui_text_primary,
                                ),
                            )
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        },
                    )
                },
            )
        }
    }

    field.setOnClickListener {
        if (approvedEvents.isEmpty()) return@setOnClickListener
        popup?.dismiss()
        popup = PopupWindow(
            buildDropdown(),
            field.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = 0f
            setBackgroundDrawable(ColorDrawable(0x00000000))
        }
        popup?.showAsDropDown(field, 0, dimenPx(R.dimen.space_4))
    }

    return field
}
