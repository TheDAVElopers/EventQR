package com.thedavelopers.eventqr.features.organizer.reports

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.graphics.withSave
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.dimenPx

/** One span of a [SplitBarView]; [value] is the raw category count, not a percentage. */
data class SplitSegment(@param:ColorInt val color: Int, val value: Long)

/**
 * 100% stacked bar for report distributions (§6.4): 10dp tall, rounded ends from the shared radius
 * scale, a 2dp gap of card surface between spans, and a 4dp floor so a 0.7% status stays painted.
 * Decorative: the label line or the legend under it carries the words for TalkBack.
 */
@SuppressLint("ViewConstructor")
class SplitBarView(context: Context, private val segments: List<SplitSegment>) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val silhouette = Path()

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dimenPx(R.dimen.ui_chart_split_height),
        )
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val spans = segments.filter { it.value > 0L }
        if (w <= 0f || h <= 0f || spans.isEmpty()) return

        val radius = minOf(context.dimenPx(R.dimen.ui_radius_button).toFloat(), h / 2f)
        bounds.set(0f, 0f, w, h)
        silhouette.reset()
        silhouette.addRoundRect(bounds, radius, radius, Path.Direction.CW)

        // Canvas coordinates do not follow layout direction: mirror the whole bar so the first
        // category starts at the reading edge, matching the ranked rows.
        canvas.withSave {
            if (this@SplitBarView.layoutDirection == LAYOUT_DIRECTION_RTL) {
                scale(-1f, 1f, w / 2f, h / 2f)
            }
            clipPath(silhouette)
            drawSpans(this, spans, w, h)
        }
    }

    private fun drawSpans(canvas: Canvas, spans: List<SplitSegment>, w: Float, h: Float) {
        val gap = context.dimenPx(R.dimen.ui_chart_split_gap).toFloat()
        val widths = splitSpanWidths(
            values = spans.map { it.value },
            width = w,
            gap = gap,
            floor = context.dimenPx(R.dimen.ui_chart_split_min_segment).toFloat(),
        )
        var x = 0f
        spans.forEachIndexed { index, span ->
            val end = x + widths[index]
            fill.color = span.color
            canvas.drawRect(x, 0f, end, h, fill)
            x = end + gap
        }
    }
}

/**
 * Widths for one 100% bar. Every positive span keeps a 4dp floor first, then the rest is shared by
 * value: below the floor a 0.7% status disappears, and a distribution chart that hides its smallest
 * category is lying. The caller drops zero-value spans before calling this.
 */
internal fun splitSpanWidths(values: List<Long>, width: Float, gap: Float, floor: Float): List<Float> {
    val total = values.sum().toFloat()
    if (values.isEmpty() || total <= 0f) return List(values.size) { 0f }
    val painted = width - gap * (values.size - 1)
    if (painted <= 0f) return List(values.size) { 0f }
    val reserved = (floor * values.size).coerceAtMost(painted)
    val flexible = painted - reserved
    return values.map { reserved / values.size + flexible * (it / total) }
}

/**
 * One ranked row's bar (§6.4). The fill is scaled to the series max, not to the total, and the
 * track is dropped: at 320dp a sub-1% fill is a sliver either way, so the row prints the true
 * "% of total" in its label and the bar carries rank and magnitude only.
 */
@SuppressLint("ViewConstructor")
class RankedBarView(
    context: Context,
    private val fraction: Float,
    @ColorInt barColor: Int,
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor }

    init {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dimenPx(R.dimen.ui_chart_bar_height),
        ).apply {
            topMargin = dimenPx(R.dimen.space_4)
            bottomMargin = dimenPx(R.dimen.space_8)
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || fraction <= 0f) return
        val radius = minOf(context.dimenPx(R.dimen.ui_radius_button).toFloat(), h / 2f)
        // The fill starts at the reading edge, not at the left edge: mirror under RTL.
        canvas.withSave {
            if (this@RankedBarView.layoutDirection == LAYOUT_DIRECTION_RTL) {
                scale(-1f, 1f, w / 2f, h / 2f)
            }
            // Cap at w so a full share never draws past the rounded edge; the bar-height floor
            // keeps a sub-1% share from vanishing, so the low end is flat, never inverted.
            val end = minOf(w, maxOf(w * fraction.coerceIn(0f, 1f), h))
            drawRoundRect(0f, 0f, end, h, radius, radius, fill)
        }
    }
}
