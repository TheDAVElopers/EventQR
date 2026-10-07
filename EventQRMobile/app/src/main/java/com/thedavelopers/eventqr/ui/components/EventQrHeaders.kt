package com.thedavelopers.eventqr.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import androidx.activity.ComponentActivity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding

/*
 * The two screen headers used app-wide. Restyle a header here and every page follows.
 *
 *  - EventQrDetailHeader: pages reached by drilling in. Thin back chevron on the left, title right next to it.
 *  - EventQrTabHeader:    pages reached from the bottom nav. Large left-aligned title with an optional subtitle.
 *
 * In XML, set app:title (and app:subtitle for the tab header). Any child views declared inside the tag
 * (an Add button, a search icon, ...) are moved into the end slot on the right.
 * Both views pad themselves for the status bar, so pages must not call applyEventQrTopInsetPadding on them.
 */

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun Context.readHeaderAttrs(attrs: AttributeSet?, block: (title: String?, subtitle: String?, showBack: Boolean) -> Unit) {
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

/** Thin back chevron on the left, title beside it, optional actions on the right. */
class EventQrDetailHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    val backButton: ImageButton
    val titleView: TextView
    val endSlot: LinearLayout

    init {
        setBackgroundResource(R.drawable.bg_header_surface_outline)
        setPadding(dp(8), 0, dp(16), 0)

        backButton = ImageButton(context).apply {
            id = R.id.nav_header_back
            val ripple = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }
            setBackgroundResource(ripple.resourceId)
            setImageResource(R.drawable.ic_header_back)
            ImageViewCompat.setImageTintList(this, ContextCompat.getColorStateList(context, R.color.text_secondary))
            setPadding(dp(11), dp(11), dp(12), dp(12))
            contentDescription = context.getString(R.string.back)
            layoutParams = LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
            setOnClickListener {
                val activity = context.findActivity()
                (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() ?: activity?.finish()
            }
        }
        titleView = TextView(context).apply {
            id = R.id.nav_header_title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL)
        }
        endSlot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
        }
        addView(titleView)
        addView(backButton)
        addView(endSlot)

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
        // Everything declared inside the tag after the three built-in children belongs to the end slot.
        while (childCount > 3) {
            val child = getChildAt(3)
            removeViewAt(3)
            endSlot.addView(child)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Title starts right after the back button and stops short of any end actions.
        measureChild(endSlot, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
        val start = dp(48) + dp(4)
        val end = if (endSlot.childCount > 0) endSlot.measuredWidth + dp(8) else 0
        if (titleView.paddingLeft != start || titleView.paddingRight != end) titleView.setPadding(start, 0, end, 0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Large left-aligned title with an optional subtitle and actions on the right. Matches My Events. */
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
        setPadding(dp(20), dp(20), dp(20), dp(8))

        titleView = TextView(context).apply {
            id = R.id.nav_header_title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.header_title))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        subtitleView = TextView(context).apply {
            id = R.id.nav_header_subtitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.header_subtitle))
            visibility = GONE
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) }
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
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12) }
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
 * Compose screens must not draw their own top bar. Returns the header so callers can add end actions.
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
