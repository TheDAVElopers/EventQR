package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.thedavelopers.eventqr.R

/*
 * The one empty state used on every list page: muted icon, bold title, optional subtitle, optional action.
 * Restyle it here and every page follows.
 *
 * Place it so it fills the space under the header (match_parent inside a FrameLayout over the list, or
 * 0dp + layout_weight="1" in a vertical LinearLayout). It centers its content in that space, so the
 * header is never counted as part of the centering area. Hidden by default (visibility gone is up to the
 * page, as before); set `text` for the title so existing `view.text = "..."` code keeps working.
 *
 * XML: app:icon, app:title, app:subtitle.
 */
class EventQrEmptyState @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val actionView: Button
    private var defaultTitle: CharSequence? = null
    private var defaultSubtitle: CharSequence? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val side = dp(24)
        setPadding(side, dp(24), side, dp(24))

        iconView = ImageView(context).apply {
            layoutParams = LayoutParams(dp(64), dp(64))
            ImageViewCompat.setImageTintList(this, ContextCompat.getColorStateList(context, R.color.text_disabled))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        titleView = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        subtitleView = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            visibility = GONE
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
        }
        actionView = Button(context).apply {
            setAllCaps(false)
            setTextColor(ContextCompat.getColor(context, R.color.brand_on_primary))
            setBackgroundResource(R.drawable.bg_primary_button)
            setPadding(dp(20), 0, dp(20), 0)
            visibility = GONE
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { topMargin = dp(16) }
        }
        addView(iconView)
        addView(titleView)
        addView(subtitleView)
        addView(actionView)

        iconView.setImageResource(R.drawable.ic_calendar)
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.EventQrEmptyState)
            try {
                a.getResourceId(R.styleable.EventQrEmptyState_icon, 0).takeIf { it != 0 }?.let { iconView.setImageResource(it) }
                defaultTitle = a.getString(R.styleable.EventQrEmptyState_title)
                defaultSubtitle = a.getString(R.styleable.EventQrEmptyState_subtitle)
                titleView.text = defaultTitle
                subtitleView.text = defaultSubtitle
                subtitleView.visibility = if (defaultSubtitle.isNullOrBlank()) GONE else VISIBLE
            } finally {
                a.recycle()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * The title. Kept as `text` so pages that did `emptyView.text = "..."` on a TextView keep compiling.
     * The XML subtitle only goes with the XML title: any other message (an error, a filter result) shows alone.
     */
    var text: CharSequence?
        get() = titleView.text
        set(value) {
            titleView.text = value
            subtext = if (value == defaultTitle) defaultSubtitle else null
        }

    var subtext: CharSequence?
        get() = subtitleView.text
        set(value) {
            subtitleView.text = value
            subtitleView.visibility = if (value.isNullOrBlank()) GONE else VISIBLE
        }

    fun setIcon(@DrawableRes res: Int) = iconView.setImageResource(res)

    fun setAction(label: CharSequence?, onClick: (() -> Unit)? = null) {
        actionView.text = label
        actionView.visibility = if (label.isNullOrBlank()) GONE else VISIBLE
        actionView.setOnClickListener { onClick?.invoke() }
    }

    fun show(title: CharSequence, subtitle: CharSequence? = null) {
        text = title
        subtext = subtitle
        visibility = VISIBLE
    }
}
