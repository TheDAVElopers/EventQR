package com.thedavelopers.eventqr.features.organizer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val EXTRA_EVENT_ID = "event_id"
internal const val EXTRA_EVENT_TITLE = "event_title"
internal const val EXTRA_PLACEHOLDER_TITLE = "placeholder_title"
internal const val EXTRA_PLACEHOLDER_MESSAGE = "placeholder_message"
internal const val EXTRA_PLACEHOLDER_NAV = "placeholder_nav"
internal const val NAV_DASHBOARD = "Dashboard"
internal const val NAV_EVENTS = "Events"
internal const val NAV_ATTENDEES = "Attendees"
internal const val NAV_LOGS = "Logs"
internal const val NAV_REPORTS = "Reports"

internal val PRIMARY = Color.parseColor("#25215F")
internal val PURPLE = Color.parseColor("#5B25C9")
internal val BG = Color.parseColor("#F7F7FA")
internal val CARD = Color.WHITE
internal val TEXT = Color.parseColor("#111827")
internal val MUTED = Color.parseColor("#6B7280")
internal val BORDER = Color.parseColor("#E5E7EB")
internal val SUCCESS = Color.parseColor("#009688")
internal val ERROR = Color.parseColor("#EF4444")
internal val WARNING = Color.parseColor("#F97316")

internal fun AppCompatActivity.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

internal fun rounded(
    color: Int,
    radiusDp: Int,
    strokeColor: Int? = BORDER,
    strokeDp: Int = 1,
    density: Float = 1f,
): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radiusDp * density
    strokeColor?.let { setStroke((strokeDp * density).roundToInt(), it) }
}

internal fun AppCompatActivity.text(
    value: String,
    size: Int = 14,
    bold: Boolean = false,
    color: Int = TEXT,
): TextView = TextView(this).apply {
    text = value
    textSize = size.toFloat()
    setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
    includeFontPadding = true
}

internal fun AppCompatActivity.card(padding: Int = 16): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        background = rounded(CARD, 14, BORDER, density = resources.displayMetrics.density)
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(8), 0, dp(10)) }
    }

internal fun AppCompatActivity.row(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

internal fun AppCompatActivity.section(title: String): TextView =
    text(title, 16, true).apply {
        setPadding(dp(2), dp(16), dp(2), dp(6))
    }

internal fun AppCompatActivity.spacer(height: Int): View =
    View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

internal fun AppCompatActivity.primaryButton(label: String, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        setAllCaps(false)
        setTextColor(Color.WHITE)
        background = rounded(PURPLE, 8, null, density = resources.displayMetrics.density)
        setOnClickListener { onClick() }
    }

internal fun AppCompatActivity.ghostButton(label: String, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        setAllCaps(false)
        setTextColor(PRIMARY)
        background = rounded(Color.WHITE, 8, BORDER, density = resources.displayMetrics.density)
        setOnClickListener { onClick() }
    }

internal fun AppCompatActivity.chip(label: String, active: Boolean = false, color: Int = PRIMARY): TextView =
    text(label, 12, active, if (active) Color.WHITE else color).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(7), dp(12), dp(7))
        background = rounded(if (active) color else Color.WHITE, 18, if (active) null else BORDER, density = resources.displayMetrics.density)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, dp(8), dp(8)) }
    }

internal fun AppCompatActivity.badge(value: String): TextView {
    val color = when (value.lowercase()) {
        "approved", "active", "successful", "accepted" -> SUCCESS
        "pending" -> WARNING
        "rejected", "disabled" -> ERROR
        else -> PRIMARY
    }
    return chip(value, false, color).apply {
        background = rounded(color and 0x22FFFFFF or 0x22000000, 18, null, density = resources.displayMetrics.density)
        setTextColor(color)
    }
}

internal fun AppCompatActivity.summaryCard(title: String, value: String, accent: Int = PRIMARY): LinearLayout =
    card(14).apply {
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { setMargins(dp(4), dp(6), dp(4), dp(6)) }
        addView(text(value, 24, true, TEXT).apply { gravity = Gravity.CENTER })
        addView(text(title, 12, false, MUTED).apply { gravity = Gravity.CENTER })
        addView(View(this@summaryCard).apply {
            background = rounded(accent, 2, null, density = resources.displayMetrics.density)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(3)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(8), 0, 0)
            }
        })
    }

internal fun EditText.afterTextChanged(onChanged: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
        override fun afterTextChanged(s: Editable?) = Unit
    })
}

internal fun AppCompatActivity.selectedEventId(): String =
    intent.getStringExtra(EXTRA_EVENT_ID)
        ?: getSharedPreferences("organizer_mvp_selection", Context.MODE_PRIVATE).getString("selected_event_id", null)
        ?: ""

internal fun AppCompatActivity.intentEventId(): String? =
    intent.getStringExtra(EXTRA_EVENT_ID)?.takeIf { it.isNotBlank() }

internal fun AppCompatActivity.intentEventTitle(): String? =
    intent.getStringExtra(EXTRA_EVENT_TITLE)?.takeIf { it.isNotBlank() }

internal fun AppCompatActivity.saveSelectedEventId(eventId: String?) {
    getSharedPreferences("organizer_mvp_selection", Context.MODE_PRIVATE).edit().apply {
        if (eventId.isNullOrBlank()) remove("selected_event_id") else putString("selected_event_id", eventId)
    }.apply()
}

internal fun List<OrganizerMvpEvent>.approvedOnly(): List<OrganizerMvpEvent> =
    filter {
        it.status.equals("Approved", ignoreCase = true) ||
            it.status.equals("Active", ignoreCase = true) ||
            it.status.equals("Completed", ignoreCase = true)
    }

internal fun AppCompatActivity.resolveSelectedEvent(events: List<OrganizerMvpEvent>, requestedEventId: String? = null): OrganizerMvpEvent? {
    val approved = events.approvedOnly()
    val selected = if (requestedEventId.isNullOrBlank()) {
        approved.firstOrNull { it.id == selectedEventId() } ?: approved.firstOrNull()
    } else {
        approved.firstOrNull { it.id == requestedEventId }
    }
    saveSelectedEventId(selected?.id)
    return selected
}

internal fun AppCompatActivity.openOrganizerPage(target: Class<*>, eventId: String? = null, eventTitle: String? = null) {
    val intent = Intent(this, target)
    eventId?.let {
        saveSelectedEventId(it)
        intent.putExtra(EXTRA_EVENT_ID, it)
    }
    eventTitle?.takeIf { it.isNotBlank() }?.let { intent.putExtra(EXTRA_EVENT_TITLE, it) }
    startActivity(intent)
}

internal fun AppCompatActivity.openOrganizerPlaceholder(
    title: String,
    message: String,
    selectedNav: String? = null,
) {
    startActivity(Intent(this, OrganizerPlaceholderActivity::class.java).apply {
        putExtra(EXTRA_PLACEHOLDER_TITLE, title)
        putExtra(EXTRA_PLACEHOLDER_MESSAGE, message)
        selectedNav?.let { putExtra(EXTRA_PLACEHOLDER_NAV, it) }
    })
}

internal fun AppCompatActivity.showMissingEventScreen(screenTitle: String) {
    organizerShell(screenTitle, "Event ID is missing.", showBack = true)
        .addView(emptyState("Open this screen from My Events or the event hub.", "Open My Events") {
            openOrganizerPage(ManageEventsActivity::class.java)
        })
}

internal fun AppCompatActivity.organizerShell(
    title: String,
    subtitle: String? = null,
    selectedNav: String? = null,
    showBack: Boolean = false,
    darkHeader: Boolean = false,
    topRightLabel: String? = null,
    onTopRight: (() -> Unit)? = null,
): LinearLayout {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BG)
    }
    setContentView(root)

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(18), dp(16), dp(if (darkHeader) 22 else 12))
        setBackgroundColor(if (darkHeader) PRIMARY else Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val headerRow = row()
    if (showBack) {
        headerRow.addView(text("<", 26, false, if (darkHeader) Color.WHITE else TEXT).apply {
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(42))
        })
    }
    val titleBox = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    titleBox.addView(text(title, 21, true, if (darkHeader) Color.WHITE else TEXT))
    subtitle?.takeIf { it.isNotBlank() }?.let {
        titleBox.addView(text(it, 13, false, if (darkHeader) Color.parseColor("#D7D4F8") else MUTED))
    }
    headerRow.addView(titleBox)
    if (topRightLabel != null) {
        headerRow.addView(ghostButton(topRightLabel) { onTopRight?.invoke() }.apply {
            minWidth = dp(44)
            minHeight = dp(36)
        })
    }
    header.addView(headerRow)
    root.addView(header)

    val scroll = ScrollView(this).apply {
        isFillViewport = false
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(18))
    }
    scroll.addView(content)
    root.addView(scroll)

    selectedNav?.let { root.addView(bottomNav(it)) }
    return content
}

internal fun AppCompatActivity.bottomNav(selected: String): LinearLayout {
    val nav = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(6), dp(18), dp(6))
        setBackgroundColor(Color.WHITE)
        elevation = dp(6).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(76),
        )
    }
    val items = listOf(
        Triple(NAV_DASHBOARD, com.thedavelopers.eventqr.R.drawable.ic_nav_home, {
            if (this@bottomNav !is OrganizerDashboardActivity) openOrganizerPage(OrganizerDashboardActivity::class.java)
        }),
        Triple(NAV_EVENTS, com.thedavelopers.eventqr.R.drawable.ic_nav_calendar, {
            if (this@bottomNav !is ManageEventsActivity) openOrganizerPage(ManageEventsActivity::class.java)
        }),
        Triple(NAV_ATTENDEES, com.thedavelopers.eventqr.R.drawable.ic_group, {
            if (selected != NAV_ATTENDEES) {
                openOrganizerPlaceholder(
                    title = "Attendees",
                    message = "Attendee management details will be available in a follow-up release.",
                    selectedNav = NAV_ATTENDEES,
                )
            }
        }),
        Triple(NAV_REPORTS, com.thedavelopers.eventqr.R.drawable.ic_search, {
            if (selected != NAV_REPORTS) {
                openOrganizerPlaceholder(
                    title = "Reports",
                    message = "Reports and analytics will be available in a follow-up release.",
                    selectedNav = NAV_REPORTS,
                )
            }
        }),
    )
    items.forEach { (label, iconRes, onClick) ->
        val isSelected = selected == label
        nav.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(4), dp(2), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            addView(ImageView(this@bottomNav).apply {
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                background = if (isSelected) {
                    rounded(PURPLE, 16, null, density = resources.displayMetrics.density)
                } else {
                    rounded(Color.TRANSPARENT, 16, null, density = resources.displayMetrics.density)
                }
                setImageResource(iconRes)
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setColorFilter(if (isSelected) Color.WHITE else Color.parseColor("#9CA3AF"))
            })
            addView(text(label, 11, isSelected, if (isSelected) PURPLE else Color.parseColor("#9CA3AF")).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
            setOnClickListener { onClick() }
        })
    }
    return nav
}

internal fun AppCompatActivity.formatCount(value: Int): String = String.format("%,d", value)

internal fun OrganizerMvpEvent.lifecycleStatus(): String {
    val normalized = status.lowercase()
    return when {
        normalized.contains("completed") || normalized.contains("ended") -> "Completed"
        normalized.contains("active") -> "Active"
        else -> "Upcoming"
    }
}

internal fun OrganizerMvpEvent.progressRatio(): Float {
    if (capacity <= 0) return if (registeredCount > 0) 1f else 0f
    return (registeredCount.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)
}

internal fun AppCompatActivity.dateBadgeParts(shortDate: String): Pair<String, String> {
    val tokens = shortDate.split(" ", ",", "-", "/").filter { it.isNotBlank() }
    val day = tokens.firstOrNull { it.all(Char::isDigit) }?.padStart(2, '0') ?: "--"
    val month = tokens.firstOrNull { it.any(Char::isLetter) }
        ?.take(3)
        ?.uppercase()
        ?: "---"
    return day to month
}

internal fun AppCompatActivity.statusBadge(status: String): TextView {
    val color = when (status) {
        "Active" -> SUCCESS
        "Completed" -> MUTED
        else -> PURPLE
    }
    return text(status, 12, true, color).apply {
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = rounded(color and 0x22FFFFFF or 0x22000000, 16, null, density = resources.displayMetrics.density)
    }
}

internal fun AppCompatActivity.eventListCard(
    event: OrganizerMvpEvent,
    onClick: () -> Unit,
): LinearLayout {
    val (day, month) = dateBadgeParts(event.shortDate)
    val lifecycle = event.lifecycleStatus()
    val ratio = event.progressRatio()
    val percent = (ratio * 100f).roundToInt()

    return card(14).apply {
        setOnClickListener { onClick() }
        addView(View(this@eventListCard).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4))
            background = rounded(
                when (lifecycle) {
                    "Active" -> Color.parseColor("#06B6D4")
                    "Completed" -> Color.parseColor("#10B981")
                    else -> PURPLE
                },
                10,
                null,
                density = resources.displayMetrics.density,
            )
        })

        addView(row().apply {
            addView(LinearLayout(this@eventListCard).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(Color.parseColor("#EEF0FF"), 10, null, density = resources.displayMetrics.density)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
                addView(text(day, 22, true, PURPLE).apply { gravity = Gravity.CENTER })
                addView(text(month, 11, true, PURPLE).apply { gravity = Gravity.CENTER })
            })

            addView(LinearLayout(this@eventListCard).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(12), 0, dp(10), 0)
                }
                addView(text(event.title, 18, true))
                addView(text("${event.dateTime} · ${event.venue}", 13, false, MUTED))
            })

            addView(statusBadge(lifecycle))
        })

        addView(row().apply {
            addView(text("${formatCount(event.registeredCount)} / ${formatCount(max(event.capacity, 0))} registered", 12, false, MUTED).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(text("${min(percent, 100)}%", 12, false, MUTED))
        })

        addView(row().apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply {
                setMargins(0, dp(8), 0, 0)
            }
            addView(View(this@eventListCard).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, ratio)
                background = rounded(PURPLE, 8, null, density = resources.displayMetrics.density)
            })
            addView(View(this@eventListCard).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - ratio)
                background = rounded(Color.parseColor("#E5E7EB"), 8, null, density = resources.displayMetrics.density)
            })
        })
    }
}

internal fun AppCompatActivity.eventSelector(
    events: List<OrganizerMvpEvent>,
    selectedId: String?,
    onSelected: (OrganizerMvpEvent) -> Unit,
): Spinner = Spinner(this).apply {
    val approved = events.approvedOnly()
    adapter = ArrayAdapter(
        this@eventSelector,
        android.R.layout.simple_spinner_item,
        approved.map { it.title },
    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    val selectedIndex = approved.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
    if (approved.isNotEmpty()) setSelection(selectedIndex)
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            approved.getOrNull(position)?.let(onSelected)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}

internal fun AppCompatActivity.stateCard(): LinearLayout =
    LinearLayout(this).apply {
        visibility = View.GONE
    }

internal fun AppCompatActivity.loadingState(message: String = "Loading..."): LinearLayout =
    card(18).apply {
        gravity = Gravity.CENTER
        addView(text(message, 15, true, MUTED).apply { gravity = Gravity.CENTER })
    }

internal fun AppCompatActivity.errorState(message: String, retry: () -> Unit): LinearLayout =
    card(18).apply {
        gravity = Gravity.CENTER
        addView(text("Unable to load live data", 16, true, ERROR).apply { gravity = Gravity.CENTER })
        addView(text(message, 13, false, MUTED).apply { gravity = Gravity.CENTER })
        addView(primaryButton("Retry") { retry() })
    }

internal fun AppCompatActivity.dataSourceBanner(load: OrganizerMvpLoad<*>): LinearLayout? =
    if (load.source == OrganizerMvpDataSource.BACKEND) null else card(10).apply {
        elevation = 0f
        background = rounded(Color.parseColor("#FFF7ED"), 10, Color.parseColor("#FED7AA"), density = resources.displayMetrics.density)
        addView(text("Live data temporarily unavailable", 13, true, WARNING))
        addView(text(load.message ?: "Backend request failed. Showing an empty state.", 12, false, MUTED))
    }

internal fun AppCompatActivity.emptyState(message: String, button: String? = null, action: (() -> Unit)? = null): LinearLayout =
    card(18).apply {
        gravity = Gravity.CENTER
        addView(text(message, 15, false, MUTED).apply { gravity = Gravity.CENTER })
        if (button != null && action != null) addView(primaryButton(button, action))
    }
