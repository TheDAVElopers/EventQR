package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.dimenOffset

/*
 * THE empty state (§5.10), XML side. Left aligned at the 20dp gutter, top offset 40dp, max width
 * 320dp - it is never centred, never an illustration, and it always names the next action.
 *
 *   48dp sunken plate + 24dp icon        bg_ui_empty_plate, ui_text_secondary
 *   16dp gap, title 18/600 max 2 lines   TextAppearance.EventQR.Type.Title
 *   4dp gap, body 14/20 max 2 lines      TextAppearance.EventQR.Type.BodySmall
 *   16dp gap, exactly ONE action         ghost by default, primary via setAction(primary = true)
 *
 * XML: app:icon, app:title, app:subtitle, app:gutter. The component owns the 20dp gutter by default;
 * a host that already pads its container sets app:gutter="false" so the copy keeps one left edge.
 * The `text` property is kept as the title so existing `emptyView.text = "..."` call sites keep
 * working; assigning it never drops the subtitle, the body states the next action.
 *
 * Compose mirror: EmptyState(...) / EmptyStateView(...) in EmptyStateView.kt.
 */
class EventQrEmptyState @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val actionView: TextView
    private var defaultSubtitle: CharSequence? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.TOP

        var iconRes = AppIcons.Event
        var ownGutter = true
        var xmlTitle: CharSequence? = null
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.EventQrEmptyState)
            try {
                a.getResourceId(R.styleable.EventQrEmptyState_icon, 0).takeIf { it != 0 }?.let { iconRes = it }
                xmlTitle = a.getString(R.styleable.EventQrEmptyState_title)
                defaultSubtitle = a.getString(R.styleable.EventQrEmptyState_subtitle)
                ownGutter = a.getBoolean(R.styleable.EventQrEmptyState_gutter, true)
            } finally {
                a.recycle()
            }
        }

        val gutter = if (ownGutter) dimenOffset(R.dimen.ui_gutter) else 0
        setPadding(gutter, dimenOffset(R.dimen.ui_empty_top_offset), gutter, dimenOffset(R.dimen.space_xl))

        val plate = android.widget.FrameLayout(context).apply {
            setBackgroundResource(R.drawable.bg_ui_empty_plate)
            layoutParams = LayoutParams(dimenOffset(R.dimen.ui_empty_plate), dimenOffset(R.dimen.ui_empty_plate))
        }
        iconView = ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                dimenOffset(R.dimen.ui_empty_glyph),
                dimenOffset(R.dimen.ui_empty_glyph),
                Gravity.CENTER,
            )
            ImageViewCompat.setImageTintList(this, ContextCompat.getColorStateList(context, R.color.ui_text_secondary))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        plate.addView(iconView)

        titleView = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
            setTextColor(ContextCompat.getColor(context, R.color.ui_text_primary))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dimenOffset(R.dimen.ui_empty_gap_title)
            }
        }
        subtitleView = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
            setTextColor(ContextCompat.getColor(context, R.color.ui_text_secondary))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = GONE
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dimenOffset(R.dimen.ui_empty_gap_body)
            }
        }
        actionView = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Label)
            setTextColor(ContextCompat.getColor(context, R.color.ui_primary))
            setBackgroundResource(R.drawable.bg_ui_button_ghost)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minimumHeight = dimenOffset(R.dimen.ui_touch_min)
            setPadding(dimenOffset(R.dimen.space_lg), 0, dimenOffset(R.dimen.space_lg), 0)
            visibility = GONE
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dimenOffset(R.dimen.ui_empty_gap_action)
            }
        }

        addView(plate)
        addView(titleView)
        addView(subtitleView)
        addView(actionView)

        iconView.setImageResource(iconRes)
        titleView.text = xmlTitle
        subtitleView.text = defaultSubtitle
        subtitleView.visibility = if (defaultSubtitle.isNullOrBlank()) GONE else VISIBLE
    }

    /**
     * The title. An empty state that changes only its headline keeps the XML body, because the body
     * states what to do next; use [subtext] to replace or clear it.
     */
    var title: CharSequence?
        get() = titleView.text
        set(value) {
            titleView.text = value
        }

    /** Alias of [title], so pages that did `emptyView.text = "..."` keep compiling. */
    var text: CharSequence?
        get() = title
        set(value) {
            title = value
        }

    var subtext: CharSequence?
        get() = subtitleView.text
        set(value) {
            subtitleView.text = value
            subtitleView.visibility = if (value.isNullOrBlank()) GONE else VISIBLE
        }

    fun setIcon(@DrawableRes res: Int) = iconView.setImageResource(res)

    /**
     * Exactly one action. Ghost by default (low priority); pass [primary] = true only when this is
     * the main task on the screen.
     */
    fun setAction(label: CharSequence?, primary: Boolean = false, onClick: (() -> Unit)? = null) {
        actionView.text = label
        actionView.setTextColor(ContextCompat.getColor(context, if (primary) R.color.ui_on_primary else R.color.ui_primary))
        actionView.setBackgroundResource(if (primary) R.drawable.bg_ui_button_primary else R.drawable.bg_ui_button_ghost)
        actionView.visibility = if (label.isNullOrBlank()) GONE else VISIBLE
        actionView.setOnClickListener { onClick?.invoke() }
    }

    /** Legacy overload (label, click) - kept so old call sites compile. */
    fun setAction(label: CharSequence?, onClick: (() -> Unit)?) = setAction(label, false, onClick)

    /**
     * Fills the copy only. Visibility stays with the host: render paths that compute
     * `VISIBLE`/`GONE` from the data set must call this, never [show], or a populated list can be
     * covered by stale empty copy.
     */
    fun setContent(title: CharSequence?, subtitle: CharSequence? = null) {
        text = title
        subtext = subtitle
    }

    /**
     * Fills the copy and reveals the state. Use only where the state owns its slot and no host
     * visibility decision exists (a freshly built state, an error surface). Render paths that
     * decide visibility from the data set must call [setContent] and set the visibility themselves.
     */
    fun show(title: CharSequence, subtitle: CharSequence? = null) {
        setContent(title, subtitle)
        visibility = View.VISIBLE
    }

    /** Hides the state; the counterpart of [show]. */
    fun hide() {
        visibility = View.GONE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = resources.getDimensionPixelSize(R.dimen.ui_empty_max_width)
        // §5.10: max width 320dp, left aligned at the gutter. Measure the block with a RESOLVED
        // width so MATCH_PARENT title/body children get a determinate width - an AT_MOST pass lets
        // them collapse to their measured content and truncate the copy.
        val resolved = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> max
            else -> MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(max)
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(resolved, MeasureSpec.EXACTLY),
            heightMeasureSpec,
        )
    }
}
