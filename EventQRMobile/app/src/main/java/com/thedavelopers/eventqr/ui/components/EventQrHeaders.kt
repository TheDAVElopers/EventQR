package com.thedavelopers.eventqr.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.icons.MirroredIcon
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiOnDarkHairline
import com.thedavelopers.eventqr.ui.theme.UiOnPrimary
import com.thedavelopers.eventqr.ui.theme.UiPassStub
import com.thedavelopers.eventqr.ui.theme.UiPrimary
import com.thedavelopers.eventqr.ui.theme.UiPrimaryMuted
import com.thedavelopers.eventqr.ui.theme.UiSurface
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import com.thedavelopers.eventqr.ui.theme.spResource

/*
 * Screen headers.
 *
 * The redesign's archetype is the PASS HEAD (§4.6): flat brand identity block, a 1dp tear line,
 * a 56dp stub of at most three numbers. All four role dashboards and both credential screens use
 * it; secondary screens use the 64dp compact variant. XML gets it from include_pass_head.xml /
 * include_pass_head_compact.xml (bound by PassHeadBinder), Compose from [PassHead] /
 * [PassHeadCompact] below - same anatomy, same tokens, same output.
 *
 * The two View classes are the legacy headers the remaining screens still inflate. Their geometry
 * has been moved onto the new tokens (56dp bar, 44dp targets, hairline, radius 0, zero elevation)
 * so those screens inherit the new look without a layout edit.
 */

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun Context.readHeaderAttrs(
    attrs: AttributeSet?,
    block: (title: String?, subtitle: String?, showBack: Boolean) -> Unit,
) {
    if (attrs == null) return block(null, null, true)
    val a = obtainStyledAttributes(attrs, R.styleable.EventQrHeader)
    try {
        block(
            a.getString(R.styleable.EventQrHeader_title),
            a.getString(R.styleable.EventQrHeader_subtitle),
            a.getBoolean(R.styleable.EventQrHeader_showBack, true),
        )
    } finally {
        a.recycle()
    }
}

/**
 * Detail top bar (§5.8): 56dp, ui_surface, 44dp back at start=4dp, 18/600 title at x=56dp, one
 * 44dp trailing action, 1dp hairline below, elevation 0. Any child views declared inside the tag
 * are moved into the end slot.
 */
class EventQrDetailHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    val backButton: ImageButton
    val titleView: TextView
    val endSlot: LinearLayout
    private val divider: View

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.ui_surface))
        setPadding(0, 0, 0, 0)
        minimumHeight = dimenOffset(R.dimen.ui_top_bar_height)

        backButton = ImageButton(context).apply {
            id = R.id.nav_header_back
            setBackgroundResource(R.drawable.bg_ui_button_icon)
            setImageResource(AppIcons.Back)
            ImageViewCompat.setImageTintList(this, ContextCompat.getColorStateList(context, R.color.ui_text_primary))
            contentDescription = context.getString(R.string.back)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LayoutParams(dimenOffset(R.dimen.ui_top_bar_action), dimenOffset(R.dimen.ui_top_bar_action), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = dimenOffset(R.dimen.ui_top_bar_back_inset)
            }
            setOnClickListener {
                val activity = context.findActivity()
                (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() ?: activity?.finish()
            }
        }

        titleView = TextView(context).apply {
            id = R.id.nav_header_title
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
            setTextColor(ContextCompat.getColor(context, R.color.ui_text_primary))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL)
        }

        endSlot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = dimenOffset(R.dimen.ui_top_bar_back_inset)
            }
        }

        divider = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.ui_hairline))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenOffset(R.dimen.stroke_hairline), Gravity.BOTTOM)
        }

        addView(titleView)
        addView(backButton)
        addView(endSlot)
        addView(divider)

        context.readHeaderAttrs(attrs) { title, _, showBack ->
            title?.let { titleView.text = it }
            backButton.visibility = if (showBack) VISIBLE else INVISIBLE
        }
        applyEventQrTopInsetPadding()
    }

    var title: CharSequence?
        get() = titleView.text
        set(value) { titleView.text = value }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // Everything declared inside the tag after the four built-in children belongs to the end slot.
        while (childCount > 4) {
            val child = getChildAt(4)
            removeViewAt(4)
            endSlot.addView(child)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChild(endSlot, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
        val start = dimenOffset(R.dimen.ui_top_bar_title_start)
        val end = if (endSlot.childCount > 0) endSlot.measuredWidth + dimenOffset(R.dimen.space_sm) else dimenOffset(R.dimen.space_sm)
        if (titleView.paddingLeft != start || titleView.paddingRight != end) titleView.setPadding(start, 0, end, 0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/**
 * Large-title header for the remaining list screens. Restyled onto the pass head: flat ui_primary
 * band, Archivo 28/34 title in white, body_s subtitle in on_primary_muted, 20dp gutter, tear line
 * on the dark block. Phase 3 replaces its call sites with include_pass_head_compact.
 */
class EventQrTabHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    val titleView: TextView
    val subtitleView: TextView
    val endSlot: LinearLayout

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_pass_head)
        val gutter = dimenOffset(R.dimen.ui_gutter)
        setPadding(gutter, dimenOffset(R.dimen.space_lg), gutter, dimenOffset(R.dimen.space_lg))

        titleView = TextView(context).apply {
            id = R.id.nav_header_title
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Headline)
            setTextColor(ContextCompat.getColor(context, R.color.ui_on_primary))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        subtitleView = TextView(context).apply {
            id = R.id.nav_header_subtitle
            setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
            setTextColor(ContextCompat.getColor(context, R.color.ui_on_primary_muted))
            visibility = GONE
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dimenOffset(R.dimen.space_2)
            }
        }
        val titleColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(titleView)
            addView(subtitleView)
        }
        endSlot = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dimenOffset(R.dimen.space_md)
            }
        }
        addView(titleColumn)
        addView(endSlot)

        context.readHeaderAttrs(attrs) { title, subtitle, _ ->
            title?.let { titleView.text = it }
            subtitle?.let { subtitleText = it }
        }
        applyEventQrTopInsetPadding()
    }

    var title: CharSequence?
        get() = titleView.text
        set(value) { titleView.text = value }

    var subtitleText: CharSequence?
        get() = subtitleView.text
        set(value) {
            subtitleView.text = value
            subtitleView.visibility = if (value.isNullOrBlank()) GONE else VISIBLE
        }

    override fun onFinishInflate() {
        super.onFinishInflate()
        while (childCount > 2) {
            val child = getChildAt(2)
            removeViewAt(2)
            endSlot.addView(child)
        }
    }
}

/**
 * Host for Compose pages: the shared [EventQrDetailHeader] on top, the Compose body below.
 * Compose screens must not draw their own top bar. Returns the header so callers can add actions.
 */
fun ComponentActivity.setContentWithDetailHeader(title: String, body: @Composable () -> Unit): EventQrDetailHeader {
    val header = EventQrDetailHeader(this).apply { this.title = title }
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(ComposeView(context).apply { setContent(body) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }
    setContentView(root)
    return header
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// COMPOSE PASS HEAD (§4.6) - pixel counterpart of include_pass_head.xml.
// ═══════════════════════════════════════════════════════════════════════════════════════════════

/**
 * The pass head. Max three [stats]; zero hides the stub but keeps the tear line.
 *
 * @param tabs role tabs in row0 (empty = no tabs)
 * @param portalChip one portal label when the screen has a single role
 * @param bellCount null = no bell, 0 = bell without a badge, > 0 = badge
 */
@Composable
fun PassHead(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    tabs: List<String> = emptyList(),
    selectedTabIndex: Int = 0,
    portalChip: String? = null,
    bellCount: Int? = null,
    stats: List<PassStat> = emptyList(),
    onTabSelected: ((Int) -> Unit)? = null,
    onBellClick: (() -> Unit)? = null,
) {
    val grid = LocalGrid.current
    val type = LocalType.current

    Column(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UiPrimary)
                .padding(horizontal = grid.gutter),
        ) {
            // row0
            Box(modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.ui_pass_head_row_tabs))) {
                when {
                    tabs.isNotEmpty() -> Row(
                        modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(end = grid.touchMin),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(grid.s20),
                    ) {
                        tabs.take(4).forEachIndexed { index, label ->
                            val selected = index == selectedTabIndex
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier
                                    .height(dimensionResource(R.dimen.ui_pass_head_tab_height))
                                    .then(
                                        if (onTabSelected != null) {
                                            Modifier.clickable { onTabSelected(index) }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(top = grid.s12),
                            ) {
                                Text(
                                    text = label,
                                    style = type.overline,
                                    color = if (selected) UiOnPrimary else UiPrimaryMuted,
                                    maxLines = 1,
                                )
                                Spacer(modifier = Modifier.height(grid.s4))
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(dimensionResource(R.dimen.ui_pass_head_tab_underline))
                                            .background(UiOnPrimary),
                                    )
                                }
                            }
                        }
                    }

                    portalChip != null -> Text(
                        text = portalChip,
                        style = type.overline,
                        color = UiOnPrimary,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(grid.radiusButton))
                            .background(UiOnDarkHairline)
                            .padding(horizontal = grid.s12, vertical = grid.s4),
                        maxLines = 1,
                    )

                    eyebrow != null -> Text(
                        text = eyebrow,
                        style = type.overline,
                        color = UiPrimaryMuted,
                        modifier = Modifier.align(Alignment.CenterStart),
                        maxLines = 1,
                    )
                }

                if (bellCount != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(grid.touchMin)
                            .clip(RoundedCornerShape(grid.radiusButton))
                            .then(if (onBellClick != null) Modifier.clickable { onBellClick() } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(AppIcons.Notification),
                            contentDescription = stringResource(R.string.ui_pass_head_bell),
                            tint = UiOnPrimary,
                            modifier = Modifier.size(dimensionResource(R.dimen.ui_bell_icon)),
                        )
                        if (bellCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(dimensionResource(R.dimen.ui_count_badge_size))
                                    .clip(RoundedCornerShape(grid.radiusBadge))
                                    .background(UiOnPrimary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (bellCount > 99) "99+" else bellCount.toString(),
                                    style = type.badge,
                                    color = UiPrimary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // row1: title
            Text(
                text = title,
                style = type.headline,
                color = UiOnPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = grid.s8),
            )

            // row2: subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = type.bodyS,
                    color = UiPrimaryMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = grid.s2),
                )
            }

            Spacer(modifier = Modifier.height(grid.s16))
        }

        // tear line, drawn on the dark edge so the 24% white dashes are visible
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.stroke_hairline))
                .background(UiOnDarkHairline),
        )

        // stub - 56dp is a floor, not the height: the cell type scales with the font size, so the
        // band takes its tallest cell and the dividers fill whatever the band got.
        val shown = stats.take(3)
        if (shown.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .heightIn(min = dimensionResource(R.dimen.ui_pass_head_stub_height))
                    .background(UiPassStub)
                    .padding(start = grid.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shown.forEachIndexed { index, stat ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(dimensionResource(R.dimen.stroke_hairline))
                                .fillMaxHeight()
                                .background(UiOnDarkHairline),
                        )
                    }
                    PassStatCell(
                        stat = stat,
                        modifier = Modifier
                            .weight(1f)
                            // vertical pad lives on the cell, not the Row: the dividers keep the
                            // full band height and the glyphs keep a clear margin when it grows.
                            .padding(
                                start = if (index == 0) grid.s2 else grid.s12,
                                end = grid.s12,
                                top = dimensionResource(R.dimen.ui_pass_head_stub_pad_v),
                                bottom = dimensionResource(R.dimen.ui_pass_head_stub_pad_v),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PassStatCell(stat: PassStat, modifier: Modifier = Modifier) {
    val type = LocalType.current
    Column(modifier) {
        Text(text = stat.value, style = type.numericM, color = UiOnPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = stat.label,
            style = type.overline,
            color = UiPrimaryMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Compact pass head (§4.6): Block A only, eyebrow row 20dp + title 44dp + tear, with the 20dp
 * screen gutter above the eyebrow - the same top spacing the full pass head shows below the
 * status-bar inset. For detail, forms, scanner and any non-dashboard screen.
 *
 * @param count result metadata on the title's baseline, right edge on the gutter. Never put it in
 *   [trailing]: the 20dp eyebrow row floats it above the title's cap height.
 * @param trailing eyebrow-row end slot for an action only (icon button, short label).
 */
@Composable
fun PassHeadCompact(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    count: String? = null,
) {
    val grid = LocalGrid.current
    val type = LocalType.current

    Column(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UiPrimary)
                .padding(top = grid.gutter)
                .padding(horizontal = grid.gutter),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.ui_pass_head_compact_row)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = eyebrow.orEmpty(),
                    style = type.overline,
                    color = UiPrimaryMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = grid.s12)) {
                Text(
                    text = title,
                    style = type.headline,
                    color = UiOnPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                if (!count.isNullOrBlank()) {
                    // Parity with include_pass_head_compact.xml: the same cap, and a label that
                    // does not fit at the overline size steps down to the ramp floor before the
                    // ellipsis applies. CountLabel.format caps the label at 16 characters, so the
                    // autosize range stays a safety net for longer translations.
                    Text(
                        text = count,
                        style = type.overline.copy(color = UiPrimaryMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = countAutoSize(),
                        modifier = Modifier
                            .padding(start = grid.s12)
                            .widthIn(max = dimensionResource(R.dimen.ui_pass_head_count_max))
                            .alignByBaseline(),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.stroke_hairline))
                .background(UiOnDarkHairline),
        )
    }
}

/**
 * Autosize range for the count slot: the overline size down to the 9sp ramp floor in
 * `ui_pass_head_count_text_step` steps, the same range and granularity as `android:autoSize*` on
 * include_pass_head_compact.xml.
 */
@Composable
internal fun countAutoSize(): TextAutoSize {
    val floor = spResource(R.dimen.ui_pass_head_count_text_min)
    val ceiling = spResource(R.dimen.ui_type_overline)
    return TextAutoSize.StepBased(
        minFontSize = floor,
        maxFontSize = ceiling,
        stepSize = spResource(R.dimen.ui_pass_head_count_text_step),
    )
}

/**
 * Compose counterpart of include_top_bar.xml (§5.8). Returns nothing: the caller owns navigation.
 */
@Composable
fun EventQrTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val grid = LocalGrid.current
    val type = LocalType.current

    Column(modifier.fillMaxWidth().background(UiSurface)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(grid.topBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = dimensionResource(R.dimen.ui_top_bar_back_inset))
                    .size(grid.topBarAction)
                    .clip(RoundedCornerShape(grid.radiusButton))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                MirroredIcon(
                    icon = AppIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    tint = UiTextPrimary,
                    size = dimensionResource(R.dimen.icon_lg),
                )
            }
            Text(
                text = title,
                style = type.title,
                color = UiTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = grid.s8),
            )
            action?.invoke()
        }
        Box(modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.stroke_hairline)).background(com.thedavelopers.eventqr.ui.theme.UiHairline))
    }
}
