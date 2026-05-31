package com.thedavelopers.eventqr.features.organizer

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
): View {
    return card(18).apply {
        gravity = Gravity.CENTER
        addView(text(message, 14, false, MUTED).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        })
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            addView(primaryButton(actionLabel, onAction).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(44),
                ).apply { setMargins(0, dp(14), 0, 0) }
            })
        }
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
): Spinner {
    val approvedEvents = events.approvedOnly()
    return Spinner(this).apply {
        val labels = approvedEvents.map { event ->
            event.title.ifBlank { "Untitled Event" }
        }
        adapter = ArrayAdapter(
            this@eventSelector,
            android.R.layout.simple_spinner_item,
            labels,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val selectedIndex = approvedEvents.indexOfFirst { it.id == selectedEventId }.takeIf { it >= 0 } ?: 0
        if (approvedEvents.isNotEmpty()) {
            setSelection(selectedIndex, false)
        }

        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                approvedEvents.getOrNull(position)?.let { onSelected(it) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }
}
