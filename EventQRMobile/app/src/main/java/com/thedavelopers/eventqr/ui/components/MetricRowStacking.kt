package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.thedavelopers.eventqr.R

/**
 * The metric-row stacking rule (§6.4, §9.3): above `fontScale` 1.3 a label/value row puts the
 * label on top and the value beneath it, `space_4` under the label. The overline label is 11sp, so
 * at the threshold it is ~14.3sp / ~200dp and leaves no room for a deadline beside it in the 320dp
 * content row; stacking beats breaking the word mid-label. A text value takes the row width; a
 * hug-width value (the STATUS badge) keeps hugging.
 *
 * One implementation for every metric row: `EventDetailActivity`'s REGISTRATION WINDOW and the
 * organizer spec table's `STATUS`, `LOCATION`, `EVENT TIME`, `REGISTRATION WINDOW`, `ORGANIZER` and
 * `CO-ORGANIZERS` rows (one shared list, `organizerEventDetailStackedRows`). At or below 1.3 it
 * does nothing, so the layout's label-left / value-right anatomy stands untouched.
 */
object MetricRowStacking {

    const val STACK_FONT_SCALE = 1.3f

    fun stacks(fontScale: Float): Boolean = fontScale > STACK_FONT_SCALE

    fun apply(context: Context, row: LinearLayout, value: TextView) {
        if (!stacks(context.resources.configuration.fontScale)) return
        // The row's anatomy is label first, value second; a row without a label is left alone.
        val label = row.getChildAt(0) as? TextView ?: return
        applyTo(row, label, value, context.resources.getDimensionPixelSize(R.dimen.space_4))
    }

    /**
     * Idempotent: every parameter is absolute, so rebinding cannot accumulate margins.
     * `internal` so the tests can measure the row without faking the configuration's fontScale.
     */
    internal fun applyTo(row: LinearLayout, label: TextView, value: TextView, gapPx: Int) {
        row.orientation = LinearLayout.VERTICAL
        // 0dp + weight only fills a horizontal row; in a vertical one the child measures to 0,
        // which is exactly how a weighted label went invisible. Both children take the full width.
        (label.layoutParams as LinearLayout.LayoutParams).apply {
            width = LinearLayout.LayoutParams.MATCH_PARENT
            weight = 0f
        }
        (value.layoutParams as LinearLayout.LayoutParams).apply {
            // A 0dp value filled its horizontal row through weight; here it would measure to 0, so
            // it takes the row width and start-aligns its text. A wrap_content value (the STATUS
            // badge) keeps hugging, and keeps its own center_vertical text gravity: the badge's
            // label would otherwise sit at the top of its 24dp box.
            if (width == 0) {
                width = LinearLayout.LayoutParams.MATCH_PARENT
                value.gravity = Gravity.START
            }
            weight = 0f
            marginStart = 0
            marginEnd = 0
            topMargin = gapPx
        }
    }
}
