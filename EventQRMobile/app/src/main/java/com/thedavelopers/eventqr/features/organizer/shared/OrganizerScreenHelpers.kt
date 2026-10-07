package com.thedavelopers.eventqr.features.organizer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.widget.TextViewCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.ui.components.EventQrBottomNavBar
import com.thedavelopers.eventqr.ui.components.OrganizerNavItems
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val EXTRA_EVENT_ID = "event_id"
internal const val EXTRA_EVENT_TITLE = "event_title"
internal const val EXTRA_EVENT_VIEW_ONLY = "event_view_only"
internal const val EXTRA_PLACEHOLDER_TITLE = "placeholder_title"
internal const val EXTRA_PLACEHOLDER_MESSAGE = "placeholder_message"
internal const val EXTRA_PLACEHOLDER_NAV = "placeholder_nav"
internal const val NAV_DASHBOARD = "Dashboard"
internal const val NAV_EVENTS = "Events"
internal const val NAV_ATTENDEES = "Attendees"
internal const val NAV_LOGS = "Logs"
internal const val NAV_REPORTS = "Reports"
internal const val NAV_REWARDS = "Rewards"

internal val PRIMARY = Color.parseColor("#25215F")
internal val PURPLE = Color.parseColor("#5B25C9")
internal val NAV_PURPLE = Color.parseColor("#4F46E5")
internal val BG = Color.parseColor("#F7F7FA")
internal val CARD = Color.WHITE
internal val TEXT = Color.parseColor("#111827")
internal val MUTED = Color.parseColor("#6B7280")
internal val BORDER = Color.parseColor("#E5E7EB")
internal val SUCCESS = Color.parseColor("#009688")
internal val ERROR = Color.parseColor("#EF4444")
internal val WARNING = Color.parseColor("#F97316")

internal data class OrganizerRefreshShell(
    val content: LinearLayout,
    val swipeRefreshLayout: SwipeRefreshLayout,
)

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
    align: Int? = null,
): TextView = TextView(this).apply {
    text = value
    textSize = size.toFloat()
    setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
    includeFontPadding = true
    // Vertical LinearLayouts hand children MATCH_PARENT width by default, so text inside a
    // MATCH_PARENT TextView stays start-aligned no matter what gravity the parent declares.
    // Callers that need centered rows must set it here, on the TextView itself.
    align?.let { gravity = it }
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
        setPadding(dp(16), 0, dp(16), 0)
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
    card(12).apply {
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { setMargins(dp(4), dp(6), dp(4), dp(6)) }
        addView(text(value, 20, true, TEXT).apply { gravity = Gravity.CENTER })
        addView(text(title, 11, false, MUTED).apply { gravity = Gravity.CENTER })
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

internal fun AppCompatActivity.intentEventViewOnly(): Boolean =
    intent.getBooleanExtra(EXTRA_EVENT_VIEW_ONLY, false)

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

@Suppress("DEPRECATION")
internal fun AppCompatActivity.openOrganizerPage(target: Class<*>, eventId: String? = null, eventTitle: String? = null, viewOnly: Boolean = false) {
    if (this::class.java == target) return
    val intent = Intent(this, target).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    eventId?.let {
        saveSelectedEventId(it)
        intent.putExtra(EXTRA_EVENT_ID, it)
    }
    eventTitle?.takeIf { it.isNotBlank() }?.let { intent.putExtra(EXTRA_EVENT_TITLE, it) }
    if (viewOnly) intent.putExtra(EXTRA_EVENT_VIEW_ONLY, true)
    startActivity(intent)
    overridePendingTransition(0, 0)
}

internal fun AppCompatActivity.showMissingEventScreen(screenTitle: String, message: String = "Event ID is missing.") {
    organizerShell(screenTitle, message, showBack = true)
        .addView(emptyState(
            iconRes = R.drawable.ic_calendar,
            title = "No event selected",
            subtext = "Open this screen from My Events or the event hub.",
            actionLabel = "Open My Events",
            onAction = { openOrganizerPage(com.thedavelopers.eventqr.features.organizer.events.ManageEventsActivity::class.java) },
        ))
}

internal fun AppCompatActivity.menuCard(
    label: String,
    iconRes: Int,
    iconTint: Int = PURPLE,
    iconBg: Int = Color.parseColor("#EEF0FF"),
    onClick: () -> Unit,
    hideBackground: Boolean = false,
    iconColorOverride: Int? = null,
): LinearLayout = card(12).apply {
    setOnClickListener { onClick() }
    val content = row()
    content.addView(ImageView(this@menuCard).apply {
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        if (!hideBackground) {
            background = rounded(iconBg, 10, null, density = resources.displayMetrics.density)
        }
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setImageResource(iconRes)
        setColorFilter(iconColorOverride ?: iconTint)
    })
    content.addView(text(label, 16, true).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(16), 0, dp(8), 0)
        }
    })
    content.addView(ImageView(this@menuCard).apply {
        layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        setImageResource(com.thedavelopers.eventqr.R.drawable.ic_chevron_right)
        setColorFilter(MUTED)
    })
    addView(content)
}

internal fun AppCompatActivity.purposeCard(
    title: String,
    subtitle: String,
    iconRes: Int = com.thedavelopers.eventqr.R.drawable.ic_qr_scan,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
): LinearLayout = card(12).apply {
    val content = row()
    content.addView(ImageView(this@purposeCard).apply {
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        background = rounded(Color.parseColor("#EEF0FF"), 10, null, density = resources.displayMetrics.density)
        setPadding(dp(11), dp(11), dp(11), dp(11))
        setImageResource(iconRes)
        setColorFilter(PURPLE)
    })
    val middle = LinearLayout(this@purposeCard).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(16), 0, dp(8), 0)
        }
    }
    middle.addView(text(title, 16, true))
    middle.addView(text(subtitle, 13, false, MUTED))
    content.addView(middle)
    val switch = androidx.appcompat.widget.SwitchCompat(this@purposeCard).apply {
        isChecked = enabled
        setOnCheckedChangeListener { _, checked -> onToggle(checked) }
    }
    content.addView(switch)
    addView(content)
}

internal fun AppCompatActivity.ruleToggle(
    title: String,
    description: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val top = row()
    top.addView(LinearLayout(this@ruleToggle).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(text(title, 15, true))
        addView(text(description, 13, false, MUTED))
    })
    top.addView(androidx.appcompat.widget.SwitchCompat(this@ruleToggle).apply {
        this.isChecked = isChecked
        setOnCheckedChangeListener { _, checked -> onToggle(checked) }
    })
    addView(top)
    setPadding(0, dp(10), 0, dp(10))
}

internal fun AppCompatActivity.labeledInput(
    label: String,
    value: String,
    hint: String? = null,
    inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    onChanged: (String) -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    addView(text(label, 14, true).apply { setPadding(0, dp(12), 0, dp(6)) })
    addView(EditText(this@labeledInput).apply {
        this.inputType = inputType
        this.hint = hint
        setText(value)
        background = rounded(Color.parseColor("#F9FAFB"), 10, BORDER, density = resources.displayMetrics.density)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        afterTextChanged { onChanged(text.toString()) }
    })
}

internal fun AppCompatActivity.organizerRefreshShell(
    title: String,
    subtitle: String? = null,
    selectedNav: String? = null,
    showBack: Boolean = false,
    darkHeader: Boolean = false,
    topRightLabel: String? = null,
    onTopRight: (() -> Unit)? = null,
    onRefresh: () -> Unit,
): OrganizerRefreshShell {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_organizer_events_screen)
    }
    setContentView(root)

    val header = android.widget.RelativeLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setPadding(dp(20), dp(20), dp(20), dp(8))
    }

    val titleContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val params = android.widget.RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
            addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
            if (topRightLabel != null) {
                addRule(android.widget.RelativeLayout.LEFT_OF, com.thedavelopers.eventqr.R.id.nav_header_action)
            }
        }
        layoutParams = params
    }

    if (showBack) {
        val backRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val outVal = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)
        val backIcon = ImageView(this).apply {
            id = com.thedavelopers.eventqr.R.id.nav_header_back
            setImageResource(com.thedavelopers.eventqr.R.drawable.ic_back_chevron)
            setColorFilter(if (darkHeader) Color.WHITE else Color.parseColor("#121735"))
            contentDescription = "Back"
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundResource(outVal.resourceId)
            setOnClickListener { finish() }
        }
        backRow.addView(backIcon)
        backRow.addView(TextView(this).apply {
            id = com.thedavelopers.eventqr.R.id.nav_header_title
            text = title
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (darkHeader) Color.WHITE else Color.parseColor("#121735"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        })
        titleContainer.addView(backRow)
    } else {
        titleContainer.addView(TextView(this).apply {
            id = com.thedavelopers.eventqr.R.id.nav_header_title
            text = title
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (darkHeader) Color.WHITE else Color.parseColor("#121735"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    val defaultSub = when (selectedNav) {
        NAV_REPORTS -> "View and generate event reports"
        NAV_REWARDS -> "View and manage event rewards"
        else -> subtitle
    }
    val effectiveSub = subtitle ?: defaultSub
    effectiveSub?.takeIf { it.isNotBlank() }?.let { sub ->
        titleContainer.addView(TextView(this).apply {
            text = sub
            textSize = 13f
            setTextColor(if (darkHeader) Color.parseColor("#D7D4F8") else Color.parseColor("#8E8EA9"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
        })
    }

    header.addView(titleContainer)

    if (topRightLabel != null) {
        val topBtn = Button(this).apply {
            id = com.thedavelopers.eventqr.R.id.nav_header_action
            text = topRightLabel
            setAllCaps(false)
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(PURPLE, 10, null, density = resources.displayMetrics.density)
            setOnClickListener { onTopRight?.invoke() }
            layoutParams = android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
            }
            setPadding(dp(14), 0, dp(14), 0)
        }
        header.addView(topBtn)
    }

    root.addView(header)

    val swipeRefreshLayout = SwipeRefreshLayout(this).apply {
        setColorSchemeColors(PURPLE)
        setOnRefreshListener { onRefresh() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(24))
    }
    scroll.addView(content)
    swipeRefreshLayout.addView(scroll)
    root.addView(swipeRefreshLayout)

    selectedNav?.let { root.addView(bottomNav(it)) }
    return OrganizerRefreshShell(content, swipeRefreshLayout)
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
        setBackgroundResource(R.drawable.bg_organizer_events_screen)
    }
    setContentView(root)

    val header = android.widget.RelativeLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setPadding(dp(20), dp(20), dp(20), dp(8))
    }

    val titleContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val params = android.widget.RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
            addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
            if (topRightLabel != null) {
                addRule(android.widget.RelativeLayout.LEFT_OF, com.thedavelopers.eventqr.R.id.nav_header_action)
            }
        }
        layoutParams = params
    }

    if (showBack) {
        val backRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val outVal = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)
        val backIcon = ImageView(this).apply {
            id = com.thedavelopers.eventqr.R.id.nav_header_back
            setImageResource(com.thedavelopers.eventqr.R.drawable.ic_back_chevron)
            setColorFilter(if (darkHeader) Color.WHITE else Color.parseColor("#121735"))
            contentDescription = "Back"
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundResource(outVal.resourceId)
            setOnClickListener { finish() }
        }
        backRow.addView(backIcon)
        backRow.addView(TextView(this).apply {
            id = com.thedavelopers.eventqr.R.id.nav_header_title
            text = title
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (darkHeader) Color.WHITE else Color.parseColor("#121735"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        })
        titleContainer.addView(backRow)
    } else {
        titleContainer.addView(TextView(this).apply {
            id = com.thedavelopers.eventqr.R.id.nav_header_title
            text = title
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (darkHeader) Color.WHITE else Color.parseColor("#121735"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    val defaultSub = when (selectedNav) {
        NAV_REPORTS -> "View and generate event reports"
        NAV_REWARDS -> "View and manage event rewards"
        else -> subtitle
    }
    val effectiveSub = subtitle ?: defaultSub
    effectiveSub?.takeIf { it.isNotBlank() }?.let { sub ->
        titleContainer.addView(TextView(this).apply {
            text = sub
            textSize = 13f
            setTextColor(if (darkHeader) Color.parseColor("#D7D4F8") else Color.parseColor("#8E8EA9"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
        })
    }

    header.addView(titleContainer)

    if (topRightLabel != null) {
        val topBtn = Button(this).apply {
            id = com.thedavelopers.eventqr.R.id.nav_header_action
            text = topRightLabel
            setAllCaps(false)
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(PURPLE, 10, null, density = resources.displayMetrics.density)
            setOnClickListener { onTopRight?.invoke() }
            layoutParams = android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
            }
            setPadding(dp(14), 0, dp(14), 0)
        }
        header.addView(topBtn)
    }

    root.addView(header)

    val scroll = ScrollView(this).apply {
        isFillViewport = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(24))
    }
    scroll.addView(content)
    root.addView(scroll)

    selectedNav?.let { root.addView(bottomNav(it)) }
    return content
}

/** Pure id-to-destination mapping for the organizer nav; unmapped ids resolve to null (no-op). */
internal fun organizerNavDestination(id: String): Class<out AppCompatActivity>? = when (id) {
    "dashboard" -> com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java
    "events" -> com.thedavelopers.eventqr.features.organizer.events.ManageEventsActivity::class.java
    "attendees" -> com.thedavelopers.eventqr.features.organizer.attendees.AttendeeManagementActivity::class.java
    "reports" -> com.thedavelopers.eventqr.features.organizer.reports.EventReportsActivity::class.java
    "rewards" -> com.thedavelopers.eventqr.features.organizer.rewards.ManageRewardsActivity::class.java
    else -> null
}

/**
 * Same-tab guard used by the shared organizer bottom nav: never re-open the screen hosting
 * the nav, and never navigate for unknown ids (null destination). A ReportPreview screen
 * resolves "reports" to [com.thedavelopers.eventqr.features.organizer.reports.EventReportsActivity],
 * so that tab is a navigation target again from the preview.
 */
internal fun shouldNavigateAway(currentClass: Class<*>, destination: Class<*>?): Boolean =
    destination != null && currentClass != destination

internal fun AppCompatActivity.bottomNav(selected: String): LinearLayout {
    val currentEventId = selectedEventId().takeIf { it.isNotBlank() }
    val selectedId = selected.lowercase()
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(ComposeView(this@bottomNav).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                EventQrTheme {
                    EventQrBottomNavBar(
                        items = OrganizerNavItems,
                        selectedId = selectedId,
                        onItemSelected = { id ->
                            val dest = organizerNavDestination(id)
                            if (shouldNavigateAway(this@bottomNav::class.java, dest)) {
                                openOrganizerNavPage(id, currentEventId)
                            }
                        },
                    )
                }
            }
        })
    }
}

/**
 * Mirrors the legacy organizer nav destinations: dashboard carries no event id,
 * every event-scoped tab (events, attendees, reports, rewards) carries [currentEventId].
 */
internal fun AppCompatActivity.openOrganizerNavPage(id: String, currentEventId: String?) {
    val destination = organizerNavDestination(id) ?: return
    if (this::class.java == destination) return
    openOrganizerPage(destination, if (id == "dashboard") null else currentEventId)
}

internal fun AppCompatActivity.formatCount(value: Int): String = String.format("%,d", value)

internal fun AppCompatActivity.formatCount(value: Long): String = String.format("%,d", value)

internal fun OrganizerMvpEvent.lifecycleStatus(): String =
    EventStatusBadgeStyler.displayLabel(EventStatusBadgeStyler.fromLabel(status), status)