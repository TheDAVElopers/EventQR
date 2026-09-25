package com.thedavelopers.eventqr.features.organizer

import android.graphics.Color
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

internal fun AppCompatActivity.loadingState(message: String): View {
    return card(18).apply {
        gravity = Gravity.CENTER
        addView(text(message, 14, false, MUTED).apply {
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
        setPadding(dp(32), dp(24), dp(32), dp(24))
        addView(ImageView(this@emptyState).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            setImageResource(iconRes)
            setColorFilter(resources.getColor(R.color.text_disabled, theme))
            contentDescription = null
        })
        addView(text(title, 16, true, resources.getColor(R.color.text_primary, theme)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        addView(text(subtext, 14, false, resources.getColor(R.color.text_secondary, theme)).apply {
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
                    dp(48),
                ).apply {
                    setMargins(0, dp(16), 0, 0)
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
    return card(18).apply {
        gravity = Gravity.CENTER
        addView(text(message, 14, false, ERROR).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        })
        if (onRetry != null) {
            addView(ghostButton("Retry", onRetry).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(44),
                ).apply { setMargins(0, dp(14), 0, 0) }
            })
        }
    }
}

internal fun <T> AppCompatActivity.dataSourceBanner(load: OrganizerMvpLoad<T>): View? {
    if (load.source == OrganizerMvpDataSource.BACKEND) return null
    val message = load.message?.takeIf { it.isNotBlank() } ?: "Showing limited local data. Pull down to refresh."
    return TextView(this).apply {
        text = message
        textSize = 13f
        setTextColor(Color.parseColor("#92400E"))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(Color.parseColor("#FEF3C7"), 12, Color.parseColor("#FDE68A"), density = resources.displayMetrics.density)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(10)) }
    }
}

internal fun AppCompatActivity.eventSelector(
    events: List<OrganizerMvpEvent>,
    selectedEventId: String,
    onSelected: (OrganizerMvpEvent) -> Unit,
): View {
    val approvedEvents = events.approvedOnly()
    val titles = approvedEvents.map { it.title.ifBlank { "Untitled Event" } }
    var selectedIndex = approvedEvents.indexOfFirst { it.id == selectedEventId }.takeIf { it >= 0 } ?: 0
    if (approvedEvents.isEmpty()) selectedIndex = -1

    val titleText = text(titles.getOrNull(selectedIndex) ?: "Select Event", 15, false, TEXT)
    val arrow = ImageView(this).apply {
        setImageResource(R.drawable.ic_arrow_drop_down)
        contentDescription = "Select event"
    }
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(16), 0)
        minimumHeight = dp(52)
        isClickable = approvedEvents.isNotEmpty()
        isFocusable = approvedEvents.isNotEmpty()
        background = rounded(Color.WHITE, 14, BORDER, density = resources.displayMetrics.density)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(titleText.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(arrow.apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })
    }

    var popup: PopupWindow? = null
    var isOpen = false

    fun buildDropdown(): LinearLayout = LinearLayout(this@eventSelector).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 14, BORDER, density = resources.displayMetrics.density)
        approvedEvents.forEachIndexed { index, event ->
            val selected = index == selectedIndex
            addView(LinearLayout(this@eventSelector).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundColor(if (selected) Color.parseColor("#EEF2FF") else Color.WHITE)
                setOnClickListener {
                    selectedIndex = index
                    titleText.text = titles[index]
                    titleText.setTextColor(TEXT)
                    popup?.dismiss()
                    isOpen = false
                    arrow.rotation = 0f
                    onSelected(event)
                }
                addView(TextView(this@eventSelector).apply {
                    text = titles[index]
                    setTextColor(if (selected) 0xFF4F46E5.toInt() else TEXT)
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
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
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
