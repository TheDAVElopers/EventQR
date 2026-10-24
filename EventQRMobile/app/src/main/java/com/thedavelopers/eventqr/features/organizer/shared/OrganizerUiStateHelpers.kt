package com.thedavelopers.eventqr.features.organizer

import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong
import com.thedavelopers.eventqr.ui.theme.roundedRect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color

internal fun AppCompatActivity.loadingState(message: String): View {
    return cardRes(R.dimen.space_18).apply {
        gravity = Gravity.CENTER
        addView(styledText(message, R.dimen.type_body_medium, false, orgColors.muted).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        })
    }
}

internal fun AppCompatActivity.emptyState(
    iconRes: Int = R.drawable.ic_calendar,
    title: String,
    subtext: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setPadding(dimenPx(R.dimen.space_xxl), dimenPx(R.dimen.space_xl), dimenPx(R.dimen.space_xxl), dimenPx(R.dimen.space_xl))
        addView(ImageView(this@emptyState).apply {
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_64), dimenPx(R.dimen.tile_64))
            setImageResource(iconRes)
            setColorFilter(color(R.color.neutral))
            contentDescription = null
        })
        addView(styledText(title, R.dimen.type_body_large, true, color(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_xs))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        addView(styledText(subtext, R.dimen.type_body_medium, false, color(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            addView(primaryButton(actionLabel, onAction).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dimenPx(R.dimen.space_xxxl),
                ).apply {
                    setMargins(0, dimenPx(R.dimen.space_lg), 0, 0)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            })
        }
    }
}

internal fun AppCompatActivity.centeredEmptyState(
    iconRes: Int = R.drawable.ic_calendar,
    title: String,
    subtext: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        addView(emptyState(iconRes, title, subtext, actionLabel, onAction))
    }
}

internal fun AppCompatActivity.errorState(
    message: String,
    onRetry: (() -> Unit)? = null,
): View {
    return cardRes(R.dimen.space_18).apply {
        gravity = Gravity.CENTER
        addView(styledText(message, R.dimen.type_body_medium, false, orgColors.error).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        })
        if (onRetry != null) {
            addView(ghostButton(getString(R.string.organizer_retry), onRetry).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dimenPx(R.dimen.tile_44),
                ).apply { setMargins(0, dimenPx(R.dimen.space_14), 0, 0) }
            })
        }
    }
}

/** The organizer event list failed to load (not "no events"): the server's message, or a generic one, with Retry. */
internal fun AppCompatActivity.eventsLoadErrorState(message: String?, onRetry: () -> Unit): View =
    errorState(message?.takeIf { it.isNotBlank() } ?: getString(R.string.organizer_dashboard_events_load_failed), onRetry)

internal fun <T> AppCompatActivity.dataSourceBanner(load: OrganizerMvpLoad<T>): View? {
    if (load.source == OrganizerMvpDataSource.BACKEND) return null
    val message = load.message?.takeIf { it.isNotBlank() } ?: getString(R.string.organizer_limited_local_data)
    return TextView(this).apply {
        text = message
        setTextSizeRes(R.dimen.type_body_compact)
        setTextColor(color(R.color.on_warning_container))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_10))
        background = roundedRect(color(R.color.warning_container), R.dimen.radius_md, color(R.color.warning_outline))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_10)) }
    }
}

internal fun AppCompatActivity.eventSelector(
    events: List<OrganizerMvpEvent>,
    selectedEventId: String,
    onSelected: (OrganizerMvpEvent) -> Unit,
): View {
    val approvedEvents = events.approvedOnly()
    val titles = approvedEvents.map { it.title.ifBlank { getString(R.string.organizer_untitled_event) } }
    var selectedIndex = approvedEvents.indexOfFirst { it.id == selectedEventId }.takeIf { it >= 0 } ?: 0
    if (approvedEvents.isEmpty()) selectedIndex = -1

    val titleText = styledText(titles.getOrNull(selectedIndex) ?: getString(R.string.organizer_select_event), R.dimen.type_body, false, orgColors.text)
    val arrow = ImageView(this).apply {
        setImageResource(R.drawable.ic_arrow_drop_down)
        contentDescription = getString(R.string.organizer_select_event_description)
    }
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dimenPx(R.dimen.space_lg), 0, dimenPx(R.dimen.space_lg), 0)
        minimumHeight = dimenPx(R.dimen.input_height)
        isClickable = approvedEvents.isNotEmpty()
        isFocusable = approvedEvents.isNotEmpty()
        background = roundedRect(orgColors.card, R.dimen.radius_14, orgColors.border)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(titleText.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(arrow.apply {
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
        })
    }

    var popup: PopupWindow? = null
    var isOpen = false

    fun buildDropdown(): LinearLayout = LinearLayout(this@eventSelector).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedRect(orgColors.card, R.dimen.radius_14, orgColors.border)
        approvedEvents.forEachIndexed { index, event ->
            val selected = index == selectedIndex
            addView(LinearLayout(this@eventSelector).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14))
                setBackgroundColor(if (selected) color(R.color.accent_container) else orgColors.card)
                setOnClickListener {
                    selectedIndex = index
                    titleText.text = titles[index]
                    titleText.setTextColor(orgColors.text)
                    popup?.dismiss()
                    isOpen = false
                    arrow.rotation = 0f
                    onSelected(event)
                }
                addView(TextView(this@eventSelector).apply {
                    text = titles[index]
                    setTextColor(if (selected) orgColors.accent else orgColors.text)
                    setTextSizeRes(R.dimen.type_body)
                    strong()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            })
        }
    }

    fun open() {
        if (approvedEvents.isEmpty()) return
        popup?.dismiss()
        popup = PopupWindow(
            buildDropdown(),
            card.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = dimenPx(R.dimen.elevation_overlay).toFloat()
            setBackgroundDrawable(ColorDrawable(color(R.color.transparent)))
            setOnDismissListener { isOpen = false; arrow.rotation = 0f }
        }
        isOpen = true
        arrow.rotation = 180f
        popup?.showAsDropDown(card, 0, 0)
    }

    card.setOnClickListener {
        if (isOpen) {
            popup?.dismiss()
            isOpen = false
            arrow.rotation = 0f
        } else {
            open()
        }
    }

    return card
}
