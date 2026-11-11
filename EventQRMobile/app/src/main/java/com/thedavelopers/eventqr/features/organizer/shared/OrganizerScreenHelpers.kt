package com.thedavelopers.eventqr.features.organizer

import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader
import com.thedavelopers.eventqr.ui.components.EventQrSwipeRefreshLayout
import com.thedavelopers.eventqr.ui.components.EventQrTabHeader
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
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
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.features.attendee.AttendeeBadges
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.ui.components.EventQrBottomNavBar
import com.thedavelopers.eventqr.ui.components.ListRowBinder
import com.thedavelopers.eventqr.ui.components.OrganizerNavItems
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimen
import com.thedavelopers.eventqr.ui.theme.dimenPx
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
 * Organizer colour accessors. Every value is a `ui_*` ROLE from res/values/colors.xml - no
 * primitives, no hex, no legacy `danger`/`deco_*` shortcuts. Resolved per Context so they follow
 * the semantic layer.
 */
internal class OrganizerColors(private val context: Context) {
    val primary: Int get() = context.color(R.color.ui_primary)
    val accent: Int get() = context.color(R.color.ui_primary)
    val bg: Int get() = context.color(R.color.ui_background)
    val card: Int get() = context.color(R.color.ui_surface)
    val text: Int get() = context.color(R.color.ui_text_primary)
    val muted: Int get() = context.color(R.color.ui_text_secondary)
    /** Decorative separators only - never a control boundary (§9.1). */
    val border: Int get() = context.color(R.color.ui_hairline)
    val outline: Int get() = context.color(R.color.ui_outline_interactive)
    val sunken: Int get() = context.color(R.color.ui_surface_sunken)
    val success: Int get() = context.color(R.color.ui_success)
    val successContainer: Int get() = context.color(R.color.ui_success_container)
    val onSuccessContainer: Int get() = context.color(R.color.ui_on_success_container)
    val error: Int get() = context.color(R.color.ui_error)
    val errorContainer: Int get() = context.color(R.color.ui_error_container)
    val onErrorContainer: Int get() = context.color(R.color.ui_on_error_container)
    val warning: Int get() = context.color(R.color.ui_warning)
    val warningContainer: Int get() = context.color(R.color.ui_warning_container)
    /** Amber as TEXT: the semantic warning role is already AA on its container. */
    val warningText: Int get() = context.color(R.color.ui_on_warning_container)
    val info: Int get() = context.color(R.color.ui_info)
    val infoContainer: Int get() = context.color(R.color.ui_info_container)
    val onInfoContainer: Int get() = context.color(R.color.ui_on_info_container)
    val onPrimary: Int get() = context.color(R.color.ui_on_primary)
    val accentContainer: Int get() = context.color(R.color.ui_accent_container)
    val accentDeep: Int get() = context.color(R.color.ui_accent_deep)
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
    styledText(title, R.dimen.ui_type_overline, true, orgColors.muted).apply {
        setPadding(dimenPx(R.dimen.space_2), dimenPx(R.dimen.space_24), dimenPx(R.dimen.space_2), dimenPx(R.dimen.space_8))
    }

internal fun AppCompatActivity.primaryButton(label: String, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        isAllCaps = false
        // The selector, not a flat white: a disabled primary is a neutral fill, and white on it is
        // invisible (the style's own ui_button_primary_text states this).
        setTextColor(ContextCompat.getColorStateList(this@primaryButton, R.color.ui_button_primary_text))
        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_ui_button_primary, theme)
        minHeight = dimenPx(R.dimen.ui_button_height)
        minWidth = dimenPx(R.dimen.ui_touch_min)
        setPadding(dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_16), 0)
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { onClick() }
    }

internal fun AppCompatActivity.ghostButton(label: String, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(orgColors.primary)
        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_ui_button_ghost, theme)
        minHeight = dimenPx(R.dimen.ui_button_height_inline)
        minWidth = dimenPx(R.dimen.ui_touch_min)
        setPadding(dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_16), 0)
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { onClick() }
    }

/** 36dp chip, radius 4, never a pill (§5.6). Selected = primary fill, no check icon. */
internal fun AppCompatActivity.chip(label: String, active: Boolean = false, color: Int = orgColors.primary): TextView =
    styledText(label, R.dimen.ui_type_label, active, if (active) orgColors.onPrimary else orgColors.text).apply {
        gravity = Gravity.CENTER
        background = ResourcesCompat.getDrawable(
            resources,
            if (active) R.drawable.bg_ui_chip_selected else R.drawable.bg_ui_chip_unselected,
            theme,
        )
        minHeight = dimenPx(R.dimen.ui_touch_min)
        setPadding(dimenPx(R.dimen.ui_chip_pad_h), dimenPx(R.dimen.space_8), dimenPx(R.dimen.ui_chip_pad_h), dimenPx(R.dimen.space_8))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, dimenPx(R.dimen.ui_chip_gap), 0) }
    }

/**
 * Status text as the ONE badge (§5.4). Tones come from the semantic mapping; a status that is not
 * a confirmation never renders lime.
 */
internal fun AppCompatActivity.badge(value: String): TextView {
    val (bgRes, fgRes) = when (value.lowercase()) {
        "approved", "active", "successful", "accepted" -> R.color.ui_success_container to R.color.ui_on_success_container
        "pending", "pending review" -> R.color.ui_warning_container to R.color.ui_on_warning_container
        "rejected", "disabled", "blocked" -> R.color.ui_error_container to R.color.ui_on_error_container
        "upcoming" -> R.color.ui_info_container to R.color.ui_on_info_container
        else -> R.color.ui_surface_sunken to R.color.ui_text_secondary
    }
    return styledText(value, R.dimen.ui_type_overline, true, color(fgRes)).apply {
        gravity = Gravity.CENTER
        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_ui_badge, theme)?.mutate()
        backgroundTintList = android.content.res.ColorStateList.valueOf(color(bgRes))
        minHeight = dimenPx(R.dimen.ui_badge_height)
        setPadding(dimenPx(R.dimen.ui_badge_pad_h), 0, dimenPx(R.dimen.ui_badge_pad_h), 0)
        maxLines = 1
    }
}

/**
 * The §5.4 event badge for organizer screens: one label + tone table ([AttendeeBadges]), so a status
 * never reads "Pending Review" on one screen and "Pending review" on another. Unknown stays neutral.
 */
internal fun TextView.bindEventBadge(rawStatus: String?) {
    AttendeeBadges.bind(this, EventStatusBadgeStyler.fromLabel(rawStatus))
}

/** Binds an already date-resolved status (see [EventStatusBadgeStyler.resolve]). */
internal fun TextView.bindEventBadge(status: EventStatus) {
    AttendeeBadges.bind(this, status)
}

/**
 * A secondary metric: label left, value right, hairline under, no card (§5.9 form 2).
 * Was a centred card in a 2x2/3-up grid; that grid is what the redesign deletes.
 */
internal fun AppCompatActivity.summaryCard(title: String, value: String, accent: Int = orgColors.primary): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(
            LinearLayout(this@summaryCard).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dimenPx(R.dimen.ui_metric_row_height)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(dimenPx(R.dimen.ui_gutter), 0, dimenPx(R.dimen.ui_gutter), 0)
                addView(styledText(title, R.dimen.ui_type_overline, false, orgColors.muted).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(styledText(value, R.dimen.ui_type_numeric_m, true, orgColors.text).apply {
                    gravity = Gravity.END
                })
            },
        )
        addView(View(this@summaryCard).apply {
            setBackgroundColor(orgColors.border)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.stroke_hairline))
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
            iconRes = AppIcons.Event,
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

/**
 * A grouped management row (§5.5 `list_row_icon`): 40dp neutral icon tile, title, meta, one 16dp
 * chevron. No per-row card, no tinted tile, no hand-rolled surface.
 *
 * `iconTint`/`iconBg`/`iconColorOverride` are accepted for call-site compatibility and ignored:
 * §3.6 permits exactly three icon colour sources and a per-section pastel tile is not one.
 */
internal fun AppCompatActivity.menuCard(
    label: String,
    iconRes: Int,
    iconTint: Int = orgColors.primary,
    iconBg: Int = orgColors.accentContainer,
    onClick: () -> Unit,
    hideBackground: Boolean = false,
    iconColorOverride: Int? = null,
): View {
    val row = LayoutInflater.from(this).inflate(R.layout.list_row_icon, null, false)
    ListRowBinder.bind(
        row = row,
        title = label,
        trailing = ListRowBinder.Trailing.CHEVRON,
        showDivider = true,
        onClick = onClick,
    )
    row.findViewById<ImageView>(R.id.listRowIcon)?.setImageResource(iconRes)
    return row
}

/** A scan-purpose row: name + optional description, trailing switch. 72dp, hairline divider. */
internal fun AppCompatActivity.purposeCard(
    title: String,
    subtitle: String,
    iconRes: Int = AppIcons.Scan,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
): View {
    val holder = LayoutInflater.from(this).inflate(R.layout.item_organizer_toggle_row, null, false)
    holder.findViewById<TextView>(R.id.txtToggleTitle)?.text = title
    holder.findViewById<TextView>(R.id.txtToggleMeta)?.apply {
        text = subtitle
        visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
    }
    holder.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchToggle)?.apply {
        isChecked = enabled
        setOnCheckedChangeListener { _, checked -> onToggle(checked) }
    }
    return holder
}

/** A rule row: title + description, trailing switch, 56dp, hairline divider. */
internal fun AppCompatActivity.ruleToggle(
    title: String,
    description: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit,
): View {
    val holder = LayoutInflater.from(this).inflate(R.layout.item_organizer_toggle_row, null, false)
    holder.findViewById<TextView>(R.id.txtToggleTitle)?.text = title
    holder.findViewById<TextView>(R.id.txtToggleMeta)?.apply {
        text = description
        visibility = if (description.isBlank()) View.GONE else View.VISIBLE
    }
    holder.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchToggle)?.apply {
        this.isChecked = isChecked
        setOnCheckedChangeListener { _, checked -> onToggle(checked) }
    }
    return holder
}

/** §5.2 form field: `overline` label 8dp above a 56dp filled box. No floating label. */
internal fun AppCompatActivity.labeledInput(
    label: String,
    value: String,
    hint: String? = null,
    inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    onChanged: (String) -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    addView(TextView(this@labeledInput).apply {
        text = label
        setTextAppearance(R.style.TextAppearance_EventQR_Type_FieldLabel)
        setPadding(0, dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_8))
    })
    addView(EditText(this@labeledInput).apply {
        this.inputType = inputType
        this.hint = hint
        setText(value)
        setTextAppearance(R.style.TextAppearance_EventQR_Type_Body)
        setTextColor(orgColors.text)
        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_ui_field, theme)
        minHeight = dimenPx(R.dimen.ui_control_height)
        setPadding(dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_16), 0)
        afterTextChanged { onChanged(text.toString()) }
    })
}

/**
 * The compact pass head (§4.6) for every secondary organizer screen: 64dp identity block + tear.
 * A back page puts a 44dp arrow in the eyebrow row's trailing slot; a tab page puts the current
 * section's action there. No gradient banner, no 96dp glyph, no centred title.
 */
internal fun AppCompatActivity.organizerHeader(
    title: String,
    subtitle: String?,
    selectedNav: String?,
    showBack: Boolean,
    topRightLabel: String?,
    onTopRight: (() -> Unit)?,
): View {
    // A back page is a secondary screen: §5.8 top bar (back at the LEFT, title at 56dp, one
    // trailing action). The compact pass head is for tab pages, which have no back affordance.
    if (showBack) {
        val bar = layoutInflater.inflate(R.layout.include_top_bar, null, false)
        bar.findViewById<TextView>(R.id.topBarTitle).text = title
        bar.findViewById<View>(R.id.topBarBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        val action = topRightLabel?.let { label ->
            TextView(this).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_EventQR_Type_Label)
                setTextColor(orgColors.primary)
                minHeight = dimenPx(R.dimen.ui_touch_min)
                minWidth = dimenPx(R.dimen.ui_touch_min)
                gravity = Gravity.CENTER
                setPadding(dimenPx(R.dimen.space_12), 0, dimenPx(R.dimen.space_12), 0)
                setOnClickListener { onTopRight?.invoke() }
            }
        }
        if (action != null) {
            // include_top_bar's action slot is an ImageButton; swap it for the text action.
            val row = bar.findViewById<LinearLayout>(R.id.topBar).getChildAt(0) as LinearLayout
            row.removeView(bar.findViewById(R.id.topBarAction))
            row.addView(
                action,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dimenPx(R.dimen.space_8) },
            )
        }
        bar.findViewById<View>(R.id.topBarDivider).setBackgroundColor(orgColors.border)
        return bar
    }

    val head = layoutInflater.inflate(R.layout.include_pass_head_compact, null, false)

    val action = topRightLabel?.let { label ->
        TextView(this).apply {
            text = label
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Label)
            setTextColor(orgColors.onPrimary)
            minHeight = dimenPx(R.dimen.ui_touch_min)
            minWidth = dimenPx(R.dimen.ui_touch_min)
            gravity = Gravity.CENTER
            setPadding(dimenPx(R.dimen.space_12), 0, dimenPx(R.dimen.space_12), 0)
            setOnClickListener { onTopRight?.invoke() }
        }
    }
    action?.let { head.findViewById<LinearLayout>(R.id.passHeadTrailing)?.addView(it) }

    PassHeadBinder.bindCompact(
        root = head,
        title = title,
        eyebrow = subtitle ?: getString(R.string.ui_organizer_role),
    )
    head.findViewById<View>(R.id.passHeadIdentity)?.applyEventQrTopInsetPadding()
    return head
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
        setBackgroundColor(orgColors.bg)
    }
    setContentView(root)
    applyEventQrSystemBarAppearance(lightStatusBars = false)

    root.addView(organizerHeader(title, subtitle, selectedNav, showBack, topRightLabel, onTopRight))

    val swipeRefreshLayout = EventQrSwipeRefreshLayout(this).apply {
        setOnRefreshListener { onRefresh() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dimenPx(R.dimen.ui_gutter), dimenPx(R.dimen.space_16), dimenPx(R.dimen.ui_gutter), dimenPx(R.dimen.space_32))
    }
    scroll.addView(content)
    swipeRefreshLayout.addView(scroll)
    root.addView(swipeRefreshLayout)

    if (selectedNav != null) root.addView(bottomNav(selectedNav)) else root.applyEventQrBottomInsetPadding()
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
        setBackgroundColor(orgColors.bg)
    }
    setContentView(root)
    applyEventQrSystemBarAppearance(lightStatusBars = false)

    root.addView(organizerHeader(title, subtitle, selectedNav, showBack, topRightLabel, onTopRight))

    val scroll = ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dimenPx(R.dimen.ui_gutter), dimenPx(R.dimen.space_16), dimenPx(R.dimen.ui_gutter), dimenPx(R.dimen.space_32))
    }
    scroll.addView(content)
    root.addView(scroll)

    if (selectedNav != null) root.addView(bottomNav(selectedNav)) else root.applyEventQrBottomInsetPadding()
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
    EventStatusBadgeStyler.displayLabel(
        EventStatusBadgeStyler.resolve(EventStatusBadgeStyler.fromLabel(status), eventStartAt, eventEndAt),
        status,
    )

/**
 * Hairline card (§5.3): `ui_surface` fill, 1dp `ui_hairline`, radius 10, elevation 0.
 * [padding] is a `space_*` dimen.
 */
internal fun AppCompatActivity.cardRes(@DimenRes padding: Int = R.dimen.ui_card_padding): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dimenPx(padding)
        setPadding(pad, pad, pad, pad)
        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_ui_card, theme)
        elevation = 0f
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dimenPx(R.dimen.space_8), 0, dimenPx(R.dimen.space_8)) }
    }

/**
 * Text built from a token size. The family is IBM Plex Sans (semibold when [bold]) and
 * `includeFontPadding` is off, matching `TextAppearance.EventQR.Type.*`.
 *
 * [size] is still a dimen id because ~200 call sites pass one; the new call sites should pass the
 * `ui_type_*` tokens. Numbers should use a `Numeric*` appearance instead of this helper.
 */
internal fun AppCompatActivity.styledText(
    value: String,
    @DimenRes size: Int = R.dimen.ui_type_body,
    bold: Boolean = false,
    color: Int = orgColors.text,
    align: Int? = null,
): TextView = TextView(this).apply {
    text = value
    setTextSizeRes(size)
    setTextColor(color)
    typeface = ResourcesCompat.getFont(
        this@styledText,
        if (bold) R.font.ibm_plex_sans_semibold else R.font.ibm_plex_sans_regular,
    )
    includeFontPadding = false
    // Vertical LinearLayouts hand children MATCH_PARENT width by default, so text inside a MATCH_PARENT TextView stays
    // start-aligned no matter what gravity the parent declares: callers that need centered rows set it on the TextView itself.
    align?.let { gravity = it }
}

/**
 * A tinted block with no code-built shape: one of the `bg_ui_block_*` drawables, tinted with a
 * `ui_*` role. Replaces every on-the-fly `GradientDrawable` in the View-based organizer screens.
 */
internal fun View.tintBlock(
    @ColorRes fill: Int,
    @DrawableRes shape: Int = R.drawable.bg_ui_block_r10,
) {
    background = androidx.core.content.ContextCompat.getDrawable(context, shape)?.mutate()
    backgroundTintList = android.content.res.ColorStateList.valueOf(
        androidx.core.content.ContextCompat.getColor(context, fill),
    )
}

/** Same as [tintBlock] but for a colour already resolved to ARGB (e.g. `orgColors.card`). */
internal fun View.tintBlockArgb(
    fill: Int,
    @DrawableRes shape: Int = R.drawable.bg_ui_block_r10,
) {
    background = androidx.core.content.ContextCompat.getDrawable(context, shape)?.mutate()
    backgroundTintList = android.content.res.ColorStateList.valueOf(fill)
}

/**
 * A backend event date string rendered in the §7.6 shape. The MVP DTO carries either an ISO date,
 * an ISO instant or an already-formatted line; numeric/ISO output is banned in UI copy, so an
 * unparseable value is dropped rather than echoed.
 */
internal fun formatOrganizerEventDate(raw: String): String {
    val value = raw.trim().replace(Regex("\\s*·.*$"), "").substringBefore(" - ").trim()
    if (value.isBlank() || value == "-") return ""
    val zone = java.time.ZoneId.of("Asia/Manila")
    val date = runCatching { java.time.LocalDate.parse(value) }.getOrNull()
        ?: runCatching { java.time.Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(value).toLocalDate() }.getOrNull()
    return date?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)).orEmpty()
}

/** Vertical spacer: [height] is a dimen token. */
internal fun AppCompatActivity.spacerRes(@DimenRes height: Int): View =
    View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dimenPx(height)) }
