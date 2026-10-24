package com.thedavelopers.eventqr.features.organizer

import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader
import com.thedavelopers.eventqr.ui.components.EventQrSwipeRefreshLayout
import com.thedavelopers.eventqr.ui.components.EventQrTabHeader
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import androidx.annotation.DimenRes
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
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimen
import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.roundedRect
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong
import kotlin.math.max
import kotlin.math.min

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

/**
 * Organizer colour accessors. Resolve per Context so they follow the design tokens (res/values/colors.xml roles);
 * never cache the resolved Ints in a top-level val. Usage inside any AppCompatActivity helper: `orgColors.muted`.
 */
internal class OrganizerColors(private val context: Context) {
    val primary: Int get() = context.color(R.color.primary)
    /** Former PURPLE / NAV_PURPLE accents: unified with the brand colour (D1). */
    val accent: Int get() = context.color(R.color.primary)
    val bg: Int get() = context.color(R.color.background)
    val card: Int get() = context.color(R.color.surface)
    val text: Int get() = context.color(R.color.text_primary)
    val muted: Int get() = context.color(R.color.text_secondary)
    val border: Int get() = context.color(R.color.outline)
    val success: Int get() = context.color(R.color.deco_status_ok_teal)
    /**
     * Error/destructive ACCENT for text, icons and fills: resolves to the `danger` role (EF4444), NOT the `error` role (C62828).
     * Use `color(R.color.error)` directly where the darker on-tint error text is wanted.
     */
    val error: Int get() = context.color(R.color.danger)
    /** Amber FILL / dot colour. Do not use for small text (2.1:1): use [warningText]. */
    val warning: Int get() = context.color(R.color.warning)
    /** Amber for small text and icons on white or light tints (`warning_dark`). */
    val warningText: Int get() = context.color(R.color.warning_dark)
    val onPrimary: Int get() = context.color(R.color.on_primary)
    val accentContainer: Int get() = context.color(R.color.accent_container)
}

internal val Context.orgColors: OrganizerColors get() = OrganizerColors(this)

internal data class OrganizerRefreshShell(
    val content: LinearLayout,
    val swipeRefreshLayout: SwipeRefreshLayout,
)

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
    styledText(title, R.dimen.type_body_large, true).apply {
        setPadding(dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_6))
    }

internal fun AppCompatActivity.primaryButton(label: String, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        setAllCaps(false)
        setTextColor(orgColors.onPrimary)
        background = roundedRect(orgColors.accent, R.dimen.radius_sm)
        setPadding(dimenPx(R.dimen.space_lg), 0, dimenPx(R.dimen.space_lg), 0)
        setOnClickListener { onClick() }
    }

internal fun AppCompatActivity.ghostButton(label: String, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        setAllCaps(false)
        setTextColor(orgColors.primary)
        background = roundedRect(orgColors.card, R.dimen.radius_sm, orgColors.border)
        setOnClickListener { onClick() }
    }

internal fun AppCompatActivity.chip(label: String, active: Boolean = false, color: Int = orgColors.primary): TextView =
    styledText(label, R.dimen.type_caption, active, if (active) orgColors.onPrimary else color).apply {
        gravity = Gravity.CENTER
        setPadding(dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_7), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_7))
        background = roundedRect(if (active) color else orgColors.card, R.dimen.radius_18, if (active) null else orgColors.border)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_sm)) }
    }

internal fun AppCompatActivity.badge(value: String): TextView {
    val color = when (value.lowercase()) {
        "approved", "active", "successful", "accepted" -> orgColors.success
        "pending" -> orgColors.warning
        "rejected", "disabled" -> orgColors.error
        else -> orgColors.primary
    }
    // The 13% tint is derived from the base colour; amber is only ~2.1:1 as small text, so the label uses warning_dark.
    val labelColor = if (color == orgColors.warning) orgColors.warningText else color
    return chip(value, false, color).apply {
        background = roundedRect(color and 0x22FFFFFF or 0x22000000, R.dimen.radius_18)
        setTextColor(labelColor)
    }
}

internal fun AppCompatActivity.summaryCard(title: String, value: String, accent: Int = orgColors.primary): LinearLayout =
    cardRes(R.dimen.space_md).apply {
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { setMargins(dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_6), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_6)) }
        addView(styledText(value, R.dimen.type_title_large, true, orgColors.text).apply { gravity = Gravity.CENTER })
        addView(styledText(title, R.dimen.type_overline, false, orgColors.muted).apply { gravity = Gravity.CENTER })
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

internal fun AppCompatActivity.showMissingEventScreen(screenTitle: String, message: String = getString(R.string.organizer_event_id_missing)) {
    organizerShell(screenTitle, message, showBack = true)
        .addView(emptyState(
            iconRes = R.drawable.ic_calendar,
            title = getString(R.string.organizer_no_event_selected),
            subtext = getString(R.string.organizer_no_event_selected_hint, message),
            actionLabel = getString(R.string.organizer_open_my_events),
            onAction = { openOrganizerPage(com.thedavelopers.eventqr.features.organizer.events.ManageEventsActivity::class.java) },
        ))
}

/** Header (with back) and a loading card while the event list loads, instead of a blank window. */
internal fun AppCompatActivity.showEventsLoadingScreen(screenTitle: String) {
    organizerShell(screenTitle, showBack = true).addView(loadingState(getString(R.string.organizer_events_loading)))
}

/** The event list failed to load: say so with Retry instead of claiming no event is selected. */
internal fun AppCompatActivity.showEventsLoadErrorScreen(screenTitle: String, message: String?, onRetry: () -> Unit) {
    var state: View? = null
    state = eventsLoadErrorState(message) {
        // Retry recreates the screen; a second tap before that happens must not queue another recreate.
        (state as? ViewGroup)?.let { card ->
            (0 until card.childCount).map(card::getChildAt).filterIsInstance<Button>().forEach { it.isEnabled = false }
        }
        onRetry()
    }
    organizerShell(screenTitle, showBack = true).addView(state)
}

internal fun AppCompatActivity.menuCard(
    label: String,
    iconRes: Int,
    iconTint: Int = orgColors.accent,
    iconBg: Int = orgColors.accentContainer,
    onClick: () -> Unit,
    hideBackground: Boolean = false,
    iconColorOverride: Int? = null,
): LinearLayout = cardRes(R.dimen.space_md).apply {
    setOnClickListener { onClick() }
    val content = row()
    content.addView(ImageView(this@menuCard).apply {
        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_42), dimenPx(R.dimen.tile_42))
        if (!hideBackground) {
            background = roundedRect(iconBg, R.dimen.radius_10)
        }
        setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10))
        setImageResource(iconRes)
        setColorFilter(iconColorOverride ?: iconTint)
    })
    content.addView(styledText(label, R.dimen.type_body_large, true).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dimenPx(R.dimen.space_lg), 0, dimenPx(R.dimen.space_sm), 0)
        }
    })
    content.addView(ImageView(this@menuCard).apply {
        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
        setImageResource(com.thedavelopers.eventqr.R.drawable.ic_chevron_right)
        setColorFilter(orgColors.muted)
    })
    addView(content)
}

internal fun AppCompatActivity.purposeCard(
    title: String,
    subtitle: String,
    iconRes: Int = com.thedavelopers.eventqr.R.drawable.ic_qr_scan,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
): LinearLayout = cardRes(R.dimen.space_md).apply {
    val content = row()
    content.addView(ImageView(this@purposeCard).apply {
        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_44), dimenPx(R.dimen.tile_44))
        background = roundedRect(orgColors.accentContainer, R.dimen.radius_10)
        setPadding(dimenPx(R.dimen.space_11), dimenPx(R.dimen.space_11), dimenPx(R.dimen.space_11), dimenPx(R.dimen.space_11))
        setImageResource(iconRes)
        setColorFilter(orgColors.accent)
    })
    val middle = LinearLayout(this@purposeCard).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dimenPx(R.dimen.space_lg), 0, dimenPx(R.dimen.space_sm), 0)
        }
    }
    middle.addView(styledText(title, R.dimen.type_body_large, true))
    middle.addView(styledText(subtitle, R.dimen.type_body_compact, false, orgColors.muted))
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
        addView(styledText(title, R.dimen.type_body, true))
        addView(styledText(description, R.dimen.type_body_compact, false, orgColors.muted))
    })
    top.addView(androidx.appcompat.widget.SwitchCompat(this@ruleToggle).apply {
        this.isChecked = isChecked
        setOnCheckedChangeListener { _, checked -> onToggle(checked) }
    })
    addView(top)
    setPadding(0, dimenPx(R.dimen.space_10), 0, dimenPx(R.dimen.space_10))
}

internal fun AppCompatActivity.labeledInput(
    label: String,
    value: String,
    hint: String? = null,
    inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    onChanged: (String) -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    addView(styledText(label, R.dimen.type_body_medium, true).apply { setPadding(0, dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_6)) })
    addView(EditText(this@labeledInput).apply {
        this.inputType = inputType
        this.hint = hint
        setText(value)
        background = roundedRect(orgColors.bg, R.dimen.radius_10, orgColors.border)
        setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14))
        afterTextChanged { onChanged(text.toString()) }
    })
}

/**
 * Back pages get the centered-title detail header; bottom-nav pages get the My Events style tab header.
 * Both come from ui/components/EventQrHeaders.kt, so restyle them there.
 */
internal fun AppCompatActivity.organizerHeader(
    title: String,
    subtitle: String?,
    selectedNav: String?,
    showBack: Boolean,
    topRightLabel: String?,
    onTopRight: (() -> Unit)?,
): View {
    val action = topRightLabel?.let { label ->
        Button(this).apply {
            id = R.id.nav_header_action
            text = label
            setAllCaps(false)
            setTextColor(orgColors.onPrimary)
            setTextSizeRes(R.dimen.type_body_compact)
            strong()
            background = roundedRect(orgColors.accent, R.dimen.radius_10)
            setOnClickListener { onTopRight?.invoke() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dimenPx(R.dimen.tile_38))
            setPadding(dimenPx(R.dimen.space_14), 0, dimenPx(R.dimen.space_14), 0)
        }
    }
    if (showBack) {
        return EventQrDetailHeader(this).apply {
            this.title = title
            action?.let { endSlot.addView(it) }
        }
    }
    val defaultSub = when (selectedNav) {
        NAV_REPORTS -> getString(R.string.organizer_reports_subtitle)
        NAV_REWARDS -> getString(R.string.organizer_rewards_subtitle)
        else -> null
    }
    return EventQrTabHeader(this).apply {
        this.title = title
        subtitleText = subtitle ?: defaultSub
        action?.let { endSlot.addView(it) }
    }
}

internal fun AppCompatActivity.organizerRefreshShell(
    title: String,
    subtitle: String? = null,
    selectedNav: String? = null,
    showBack: Boolean = false,
    topRightLabel: String? = null,
    onTopRight: (() -> Unit)? = null,
    onRefresh: () -> Unit,
): OrganizerRefreshShell {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_organizer_events_screen)
    }
    setContentView(root)

    root.addView(organizerHeader(title, subtitle, selectedNav, showBack, topRightLabel, onTopRight))

    val swipeRefreshLayout = EventQrSwipeRefreshLayout(this).apply {
        setOnRefreshListener { onRefresh() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_xl))
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
    topRightLabel: String? = null,
    onTopRight: (() -> Unit)? = null,
): LinearLayout {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_organizer_events_screen)
    }
    setContentView(root)

    root.addView(organizerHeader(title, subtitle, selectedNav, showBack, topRightLabel, onTopRight))

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
        setPadding(dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_xl))
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

/** Text view: [size] is a `type_*` dimen (sp), bold goes through [strong]. */
internal fun AppCompatActivity.styledText(
    value: String,
    @DimenRes size: Int = R.dimen.type_body_medium,
    bold: Boolean = false,
    color: Int = orgColors.text,
    align: Int? = null,
): TextView = TextView(this).apply {
    text = value
    setTextSizeRes(size)
    setTextColor(color)
    if (bold) strong()
    includeFontPadding = true
    // Vertical LinearLayouts hand children MATCH_PARENT width by default, so text inside a MATCH_PARENT TextView stays
    // start-aligned no matter what gravity the parent declares: callers that need centered rows set it on the TextView itself.
    align?.let { gravity = it }
}

/** Card container: [padding] is a `space_*` dimen; radius_14, hairline border and elevation_raised. */
internal fun AppCompatActivity.cardRes(@DimenRes padding: Int = R.dimen.space_lg): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dimenPx(padding)
        setPadding(pad, pad, pad, pad)
        background = roundedRect(orgColors.card, R.dimen.radius_14, orgColors.border)
        elevation = dimenPx(R.dimen.elevation_raised).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dimenPx(R.dimen.space_sm), 0, dimenPx(R.dimen.space_10)) }
    }

/** Vertical spacer: [height] is a dimen token. */
internal fun AppCompatActivity.spacerRes(@DimenRes height: Int): View =
    View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dimenPx(height)) }
