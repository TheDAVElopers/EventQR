package com.thedavelopers.eventqr.ui.components

import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.thedavelopers.eventqr.R

/**
 * XML binder for the pass head (§4.6).
 *
 * The pass head is a plain `<include>` of `include_pass_head.xml` /
 * `include_pass_head_compact.xml`; this object is the small piece of Kotlin that fills it, so no
 * screen has to repeat eight `findViewById` + visibility calls and get the "hide cell 3 AND its
 * divider" rule wrong.
 *
 *     PassHeadBinder.bind(
 *         findViewById(R.id.passHead),
 *         PassHeadModel(
 *             eyebrow = "ATTENDEE",
 *             title = "Juan",
 *             stats = listOf(
 *                 PassStat("6", "Registered"),
 *                 PassStat("4", "Check-ins"),
 *                 PassStat("12", "Events"),
 *             ),
 *             bellCount = 3,
 *         ),
 *     )
 *
 * The binder never touches insets. The screen applies the status-bar inset to
 * `R.id.passHeadIdentity` (`View.applyEventQrTopInsetPadding()`), exactly like the old headers -
 * that keeps the brand fill running behind the status bar instead of pushing the layout down.
 *
 * Compose parity: [PassHead] in EventQrHeaders.kt renders the same anatomy from the same tokens.
 */

/** One stub cell. Max three per pass head (§5.9). */
data class PassStat(
    val value: String,
    val label: String,
)

/**
 * Everything the pass head can show. All fields are optional so a screen only states what it has.
 *
 * @param tabs role tabs in row0 (Attendee / Staff / Organizer / Admin). Empty = no tabs.
 * @param selectedTabIndex index into [tabs]; ignored when [tabs] is empty.
 * @param eyebrow shown in row0 when there are no [tabs] and no [portalChip].
 * @param portalChip one portal label (chip) when a screen has a single role and no tabs.
 * @param bellCount null = no bell; 0 = bell with no badge; >0 = bell with a count badge.
 * @param stats 0..3 stub cells. Zero hides the stub block; the tear line stays.
 */
data class PassHeadModel(
    val title: String,
    val eyebrow: String? = null,
    val subtitle: String? = null,
    val tabs: List<String> = emptyList(),
    val selectedTabIndex: Int = 0,
    val portalChip: String? = null,
    val bellCount: Int? = null,
    val stats: List<PassStat> = emptyList(),
    val onTabSelected: ((Int) -> Unit)? = null,
    val onBellClick: (() -> Unit)? = null,
)

object PassHeadBinder {

    private val TAB_LABELS = intArrayOf(
        R.id.passHeadTab1,
        R.id.passHeadTab2,
        R.id.passHeadTab3,
        R.id.passHeadTab4,
    )
    private val TAB_WRAPPERS = intArrayOf(
        R.id.passHeadTabWrap1,
        R.id.passHeadTabWrap2,
        R.id.passHeadTabWrap3,
        R.id.passHeadTabWrap4,
    )
    private val STAT_VALUES = intArrayOf(
        R.id.passHeadStat1Value,
        R.id.passHeadStat2Value,
        R.id.passHeadStat3Value,
    )
    private val STAT_LABELS = intArrayOf(
        R.id.passHeadStat1Label,
        R.id.passHeadStat2Label,
        R.id.passHeadStat3Label,
    )

    /** Fills a `include_pass_head.xml` (three-block head). */
    fun bind(root: View, model: PassHeadModel) {
        bindRowZero(root, model)

        root.findViewById<TextView>(R.id.passHeadTitle)?.text = model.title

        root.findViewById<TextView>(R.id.passHeadSubtitle)?.apply {
            text = model.subtitle
            visibility = if (model.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        val shownStats = model.stats.take(3)
        val stub = root.findViewById<View>(R.id.passHeadStub)
        stub?.visibility = if (shownStats.isEmpty()) View.GONE else View.VISIBLE

        for (index in STAT_VALUES.indices) {
            val cellVisible = index < shownStats.size
            val cell = when (index) {
                0 -> R.id.passHeadStat1Value
                1 -> R.id.passHeadCell2
                else -> R.id.passHeadCell3
            }
            root.findViewById<View>(cell)?.visibility = if (cellVisible) View.VISIBLE else View.GONE
            if (cellVisible) {
                root.findViewById<TextView>(STAT_VALUES[index])?.text = shownStats[index].value
                root.findViewById<TextView>(STAT_LABELS[index])?.text = statLabel(root, shownStats[index])
            }
        }

        // Divider 2 is only meaningful with two or more cells; divider 3 with three.
        root.findViewById<View>(R.id.passHeadDivider2)?.visibility =
            if (shownStats.size >= 2) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.passHeadDivider3)?.visibility =
            if (shownStats.size >= 3) View.VISIBLE else View.GONE
    }

    private fun statLabel(root: View, stat: PassStat): String {
        val singularEvent = stat.value == "1" &&
            stat.label.equals(root.context.getString(R.string.ui_attendee_events), ignoreCase = true)
        return if (singularEvent) root.context.getString(R.string.ui_pass_head_stat_event) else stat.label
    }

    /**
     * Fills a `include_pass_head_compact.xml` (64dp, Block A only). [trailing] receives the
     * end slot of the eyebrow row - put a 44dp icon button or a short action label in it. A result
     * count is not a trailing action: it goes on the title row, `R.id.passHeadTitleCount`.
     */
    fun bindCompact(
        root: View,
        title: String,
        eyebrow: String? = null,
        trailing: View? = null,
    ) {
        root.findViewById<TextView>(R.id.passHeadTitle)?.text = title
        root.findViewById<TextView>(R.id.passHeadEyebrow)?.apply {
            text = eyebrow
            visibility = if (eyebrow.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
        }
        trailing?.let {
            val slot = root.findViewById<LinearLayout>(R.id.passHeadTrailing) ?: return@let
            slot.removeAllViews()
            slot.addView(it)
        }
    }

    /** Flips the role tabs without re-binding the title or the stats. */
    fun selectTab(root: View, index: Int) {
        for (position in TAB_LABELS.indices) {
            val label = root.findViewById<TextView>(TAB_LABELS[position]) ?: continue
            label.isSelected = position == index
            label.isClickable = true
        }
    }

    /** Hides the whole row0 cluster (a detail screen that only wants eyebrow + title). */
    fun hideRowZero(root: View) {
        root.findViewById<View>(R.id.passHeadTabs)?.visibility = View.GONE
        root.findViewById<View>(R.id.passHeadEyebrow)?.visibility = View.GONE
        root.findViewById<View>(R.id.passHeadChip)?.visibility = View.GONE
        root.findViewById<View>(R.id.passHeadBell)?.visibility = View.GONE
    }

    private fun bindRowZero(root: View, model: PassHeadModel) {
        val tabsContainer = root.findViewById<LinearLayout>(R.id.passHeadTabs)
        val hasTabs = model.tabs.isNotEmpty() && model.tabs.size <= TAB_LABELS.size

        tabsContainer?.visibility = if (hasTabs) View.VISIBLE else View.GONE
        if (hasTabs) {
            for (index in TAB_LABELS.indices) {
                val label = root.findViewById<TextView>(TAB_LABELS[index]) ?: continue
                val wrapper = root.findViewById<View>(TAB_WRAPPERS[index]) ?: continue
                val tab = model.tabs.getOrNull(index)
                wrapper.visibility = if (tab == null) View.GONE else View.VISIBLE
                if (tab != null) {
                    label.text = tab
                    label.isSelected = index == model.selectedTabIndex
                }
            }
            model.onTabSelected?.let { callback ->
                for (index in model.tabs.indices) {
                    root.findViewById<View>(TAB_WRAPPERS[index])?.setOnClickListener { callback(index) }
                }
            }
        }

        val eyebrow = root.findViewById<TextView>(R.id.passHeadEyebrow)
        eyebrow?.apply {
            text = model.eyebrow
            visibility = if (!hasTabs && model.portalChip == null && !model.eyebrow.isNullOrBlank()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        val chip = root.findViewById<TextView>(R.id.passHeadChip)
        chip?.apply {
            text = model.portalChip
            visibility = if (!hasTabs && model.portalChip != null) View.VISIBLE else View.GONE
        }

        val bell = root.findViewById<View>(R.id.passHeadBell)
        bell?.visibility = if (model.bellCount == null) View.GONE else View.VISIBLE
        model.onBellClick?.let { callback ->
            root.findViewById<ImageButton>(R.id.passHeadBellButton)?.setOnClickListener { callback() }
        }
        val badge = root.findViewById<TextView>(R.id.passHeadBellBadge)
        badge?.apply {
            val count = model.bellCount ?: 0
            visibility = if (count > 0) View.VISIBLE else View.GONE
            text = if (count > 99) "99+" else count.toString()
        }
    }
}

/**
 * XML binder for the three list-row layouts (§5.5). One trailing thing only - the binder enforces
 * that so a row can never grow both a chevron and a value. The one shape that carries two is
 * [Trailing.VALUE_WITH_CHEVRON]: a row whose current value *is* the label and still opens something,
 * which is the §5.2 select-field anatomy. It has no call site since the profile's Switch portal row
 * was deleted (v1.34).
 *
 * Accessibility: a row that navigates is one TalkBack node with the Button role; above
 * `fontScale` 1.3 the title takes a second line instead of ellipsizing a real name (§9.3).
 */
object ListRowBinder {

    enum class Trailing { NONE, CHEVRON, VALUE, VALUE_WITH_CHEVRON, BADGE }

    /**
     * @param badgeStyle one of the `Widget.EventQR.Ui.StatusBadge.*` styles when [trailing] is
     *   [Trailing.BADGE]; both its text colour and its container tint are applied. Ignored otherwise.
     * @param contentDescription the row's one TalkBack name, when the title alone is not the whole
     *   story (a row that also prints a value). Reset to null on every bind, so a recycled row
     *   never keeps the previous one's name.
     */
    fun bind(
        row: View,
        title: String,
        meta: String? = null,
        trailing: Trailing = Trailing.NONE,
        trailingText: String? = null,
        @StyleRes badgeStyle: Int? = null,
        showDivider: Boolean = true,
        contentDescription: String? = null,
        onClick: (() -> Unit)? = null,
    ) {
        row.findViewById<TextView>(R.id.listRowTitle)?.apply {
            text = title
            // One line is the row's default; a real title at fontScale 2.0 would be cut to a
            // fragment, so the cap lifts to two lines above 1.3 (§9.3).
            maxLines = if (MetricRowStacking.stacks(row.resources.configuration.fontScale)) 2 else 1
        }
        row.findViewById<TextView>(R.id.listRowMeta)?.apply {
            text = meta
            visibility = if (meta.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        val chevron = row.findViewById<View>(R.id.listRowChevron)
        val value = row.findViewById<TextView>(R.id.listRowValue)
        val badge = row.findViewById<TextView>(R.id.listRowBadge)

        chevron?.visibility =
            if (trailing == Trailing.CHEVRON || trailing == Trailing.VALUE_WITH_CHEVRON) View.VISIBLE
            else View.GONE
        value?.apply {
            visibility = when (trailing) {
                Trailing.VALUE, Trailing.VALUE_WITH_CHEVRON -> View.VISIBLE
                else -> View.GONE
            }
            text = trailingText
            // The value's tone is the shape's, not the caller's: a plain value is data (primary),
            // the value in a value + chevron row is context (secondary). Reset on every bind.
            setTextColor(
                ContextCompat.getColor(
                    row.context,
                    if (trailing == Trailing.VALUE_WITH_CHEVRON) R.color.ui_text_secondary
                    else R.color.ui_text_primary,
                ),
            )
        }
        badge?.apply {
            visibility = if (trailing == Trailing.BADGE) View.VISIBLE else View.GONE
            text = trailingText
            badgeStyle?.let { applyBadgeTone(this, it) }
        }

        val container = row.findViewById<View>(R.id.listRowContainer)
        container?.isClickable = onClick != null
        container?.setOnClickListener { onClick?.invoke() }
        container?.contentDescription = contentDescription
        // Clear first: rows are recycled, and a dead delegate would keep announcing a Button.
        container?.let {
            ViewCompat.setAccessibilityDelegate(it, null)
            if (onClick != null) it.asButtonRole()
        }

        row.findViewById<View>(R.id.listRowDivider)?.visibility =
            if (showDivider) View.VISIBLE else View.GONE

        bindDateBlock(row)
    }

    /**
     * `list_row_date` only. Its 44dp date block grows past the min width at large font scales, so
     * the hairline follows the block's measured width instead of the fixed [R.dimen.ui_divider_inset].
     * Bound here so every host (Discover, both dashboards, Manage events) aligns on its own; the
     * listener is re-attached rather than stacked, because rows are recycled.
     */
    private fun bindDateBlock(row: View) {
        val block = row.findViewById<View>(R.id.listRowDateBlock) ?: return
        val previous = block.getTag(R.id.list_row_date_listener)
        if (previous is View.OnLayoutChangeListener) {
            block.removeOnLayoutChangeListener(previous)
        }
        val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            alignDividerToDateBlock(row, view.width)
        }
        block.setTag(R.id.list_row_date_listener, listener)
        block.addOnLayoutChangeListener(listener)
        alignDividerToDateBlock(row, block.width)
    }

    /** 20dp row start + the measured block + the 16dp gap to the text column. */
    private fun alignDividerToDateBlock(row: View, blockWidth: Int) {
        if (blockWidth <= 0) return
        val divider = row.findViewById<View>(R.id.listRowDivider) ?: return
        val params = divider.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
        val inset = row.resources.getDimensionPixelSize(R.dimen.ui_row_start) +
            blockWidth + row.resources.getDimensionPixelSize(R.dimen.space_lg)
        if (params.marginStart != inset) {
            params.marginStart = inset
            divider.layoutParams = params
        }
    }

    /**
     * Applies a `Widget.EventQR.Ui.StatusBadge.*` tone to [badge].
     *
     * `setTextAppearance` alone ignores `android:backgroundTint` (a text appearance never tints the
     * background), so a Warning badge kept the layout's Success fill. Both tone colours are resolved
     * from the style and set directly instead.
     */
    internal fun applyBadgeTone(badge: TextView, @StyleRes style: Int) {
        badge.setTextAppearance(style)
        val tone = badge.context.theme.obtainStyledAttributes(
            style,
            intArrayOf(android.R.attr.textColor, android.R.attr.backgroundTint),
        )
        try {
            tone.getColorStateList(TONE_TEXT_COLOR)?.let { badge.setTextColor(it) }
            tone.getColorStateList(TONE_BACKGROUND_TINT)?.let { badge.backgroundTintList = it }
        } finally {
            tone.recycle()
        }
    }

    private const val TONE_TEXT_COLOR = 0
    private const val TONE_BACKGROUND_TINT = 1
}

